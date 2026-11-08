package com.leslie.teachtrack.question;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leslie.teachtrack.audit.AuditService;
import com.leslie.teachtrack.auth.AuthPrincipal;
import com.leslie.teachtrack.common.ApiException;
import com.leslie.teachtrack.common.Ids;
import com.leslie.teachtrack.common.PageResponse;
import com.leslie.teachtrack.sync.SyncChangeRepository;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QuestionService {
    private static final Set<String> TYPES = Set.of(
            "SINGLE_CHOICE", "MULTI_CHOICE", "FILL_BLANK", "EXPERIMENT",
            "CALCULATION", "SHORT_ANSWER", "ESSAY", "OTHER");
    private static final Set<String> SOURCES = Set.of(
            "SELF_CREATED", "TEACHER_IMPORTED", "STUDENT_EXAM", "AUTHORIZED_BANK",
            "AI_GENERATED", "AI_VARIANT");
    private static final Set<String> REVIEW = Set.of(
            "DRAFT", "AI_PARSED", "NEEDS_REVIEW", "NEEDS_TEACHER_REVIEW", "APPROVED", "REJECTED");
    private static final Set<String> COPYRIGHT = Set.of(
            "PERSONAL_USE", "AUTHORIZED", "PUBLIC_DOMAIN", "UNKNOWN");
    private static final Set<String> USAGE = Set.of("PRIVATE_TEACHING", "CLASSROOM", "PUBLIC");
    private static final Set<String> IMPORTANCE = Set.of("PRIMARY", "SECONDARY", "RELATED");

    private final QuestionRepository repository;
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final AuditService audit;
    private final SyncChangeRepository sync;

    public QuestionService(
            QuestionRepository repository,
            JdbcTemplate jdbc,
            ObjectMapper objectMapper,
            AuditService audit,
            SyncChangeRepository sync
    ) {
        this.repository = repository;
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.audit = audit;
        this.sync = sync;
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public QuestionDetail create(QuestionAnswer.CreateRequest request, AuthPrincipal principal) {
        validate(request.questionType(), request.difficulty(), request.sourceType(),
                request.copyrightStatus(), request.usageScope(), request.reviewStatus());
        requireSubject(request.subjectId());
        String stem = requireStem(request.stem());
        String stemHash = Ids.sha256Hex(stem);
        repository.findByStemHash(stemHash).ifPresent(existing -> {
            throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE_QUESTION",
                    "题干完全相同的题目已存在: " + existing.id());
        });
        repository.findByNormalizedHash(normalizeStem(stem), null).ifPresent(existing -> {
            throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE_QUESTION",
                    "规范化后重复的题目已存在: " + existing.id());
        });

        String sourceType = defaultIfBlank(request.sourceType(), "SELF_CREATED");
        String reviewStatus = defaultIfBlank(request.reviewStatus(), "DRAFT");
        if (sourceType.startsWith("AI_") && "APPROVED".equals(reviewStatus)) {
            reviewStatus = "NEEDS_TEACHER_REVIEW";
        }
        UUID id = UUID.randomUUID();
        QuestionRow row = new QuestionRow(
                id, null, request.subjectId(), nullToEmpty(request.grade()),
                nullToEmpty(request.textbookVersion()), request.questionType(), request.difficulty(),
                stem, sourceType, nullToEmpty(request.sourceName()),
                reviewStatus, defaultIfBlank(request.copyrightStatus(), "PERSONAL_USE"),
                defaultIfBlank(request.usageScope(), "PRIVATE_TEACHING"),
                1, java.time.Instant.now());
        repository.insert(row, stemHash, normalizeStem(stem), request.abilityTags(), principal.userId());
        applyChildren(id, request.options(), request.answer(), request.knowledge());
        QuestionDetail detail = repository.find(id, false).orElseThrow();
        repository.insertVersion(id, 1, snapshot(detail), "create", principal.userId());
        sync.append("question", id, "UPSERT", 1, null, payload(id, reviewStatus));
        audit.record(principal, "question.create", "Question", id, "SUCCESS", null);
        return detail;
    }

    @Transactional
    public QuestionDetail update(UUID id, QuestionAnswer.UpdateRequest request, AuthPrincipal principal) {
        QuestionDetail current = repository.find(id, false)
                .orElseThrow(() -> ApiException.notFound("题目不存在: " + id));
        if (request.baseVersion() != current.question().version()) {
            throw new ApiException(HttpStatus.CONFLICT, "VERSION_CONFLICT",
                    "题目已被修改，当前版本 " + current.question().version());
        }
        String type = defaultIfBlank(request.questionType(), current.question().questionType());
        int difficulty = request.difficulty() == null ? current.question().difficulty() : request.difficulty();
        validate(type, difficulty, request.sourceType(), request.copyrightStatus(),
                request.usageScope(), request.reviewStatus());
        UUID subjectId = request.subjectId() == null ? current.question().subjectId() : request.subjectId();
        requireSubject(subjectId);
        String stem = requireStem(request.stem() == null ? current.question().stem() : request.stem());
        String sourceType = defaultIfBlank(request.sourceType(), current.question().sourceType());
        String reviewStatus = defaultIfBlank(request.reviewStatus(), current.question().reviewStatus());
        if (sourceType.startsWith("AI_") && "APPROVED".equals(reviewStatus)
                && !principal.hasScope("owner")) {
            throw ApiException.forbidden("AI 生成题目必须由教师审核");
        }
        QuestionRow row = new QuestionRow(
                id, current.question().legacyId(), subjectId,
                request.grade() == null ? current.question().grade() : request.grade(),
                request.textbookVersion() == null ? current.question().textbookVersion() : request.textbookVersion(),
                type, difficulty, stem, sourceType,
                request.sourceName() == null ? current.question().sourceName() : request.sourceName(),
                reviewStatus,
                defaultIfBlank(request.copyrightStatus(), current.question().copyrightStatus()),
                defaultIfBlank(request.usageScope(), current.question().usageScope()),
                current.question().version(), java.time.Instant.now());
        int updated = repository.update(id, request.baseVersion(), row,
                Ids.sha256Hex(stem), normalizeStem(stem),
                request.abilityTags() == null ? current.abilityTags() : request.abilityTags(),
                principal.userId());
        if (updated == 0) {
            throw new ApiException(HttpStatus.CONFLICT, "VERSION_CONFLICT", "题目已被其他端修改");
        }
        if (request.options() != null) {
            repository.replaceOptions(id, request.options());
        }
        if (request.answer() != null) {
            repository.upsertAnswer(id, request.answer());
        }
        if (request.knowledge() != null) {
            repository.replaceKnowledge(id, request.knowledge());
        }
        QuestionDetail detail = repository.find(id, false).orElseThrow();
        repository.insertVersion(id, detail.question().version(), snapshot(detail), "update", principal.userId());
        sync.append("question", id, "UPSERT", detail.question().version(), null, payload(id, reviewStatus));
        audit.record(principal, "question.update", "Question", id, "SUCCESS", null);
        return detail;
    }

    @Transactional
    public QuestionDetail review(UUID id, String status, AuthPrincipal principal) {
        String normalized = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        if (!REVIEW.contains(normalized)) {
            throw ApiException.badRequest("INVALID_REVIEW_STATUS", "未知审核状态: " + status);
        }
        QuestionDetail current = repository.find(id, false)
                .orElseThrow(() -> ApiException.notFound("题目不存在: " + id));
        if ("APPROVED".equals(normalized)) {
            if (current.question().sourceType().startsWith("AI_") && !principal.hasScope("owner")) {
                throw ApiException.forbidden("AI 生成题目必须由教师审核");
            }
            if ("REJECTED".equals(current.question().reviewStatus())) {
                throw ApiException.badRequest("INVALID_TRANSITION", "已拒绝的题目不能直接通过");
            }
        }
        int updated = repository.updateReviewStatus(id, normalized);
        if (updated == 0) {
            throw ApiException.notFound("题目不存在: " + id);
        }
        QuestionDetail detail = repository.find(id, false).orElseThrow();
        repository.insertVersion(id, detail.question().version(), snapshot(detail),
                "review:" + normalized, principal.userId());
        sync.append("question", id, "UPSERT", detail.question().version(), null, payload(id, normalized));
        audit.record(principal, "question.review", "Question", id, "SUCCESS", null);
        return detail;
    }

    @Transactional
    public void softDelete(UUID id, AuthPrincipal principal) {
        int updated = repository.softDelete(id);
        if (updated == 0) {
            throw ApiException.notFound("题目不存在: " + id);
        }
        sync.append("question", id, "DELETE", 1, null, "{\"deleted\":true}");
        audit.record(principal, "question.delete", "Question", id, "SUCCESS", null);
    }

    public QuestionDetail get(UUID id) {
        return repository.find(id, false)
                .orElseThrow(() -> ApiException.notFound("题目不存在: " + id));
    }

    public record DuplicateCheck(QuestionRow exact, QuestionRow near, List<QuestionRow> similar) {
    }

    public DuplicateCheck duplicates(String stem) {
        String normalized = requireStem(stem);
        return new DuplicateCheck(
                repository.findByStemHash(Ids.sha256Hex(normalized)).orElse(null),
                repository.findByNormalizedHash(normalizeStem(normalized), null).orElse(null),
                repository.findSimilar(normalized, 5)
        );
    }

    @Transactional
    public void linkAsset(UUID id, UUID assetId, String assetType, int sortOrder, AuthPrincipal principal) {
        get(id);
        String type = assetType == null || assetType.isBlank() ? "IMAGE" : assetType.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("IMAGE", "DIAGRAM", "GRAPH", "FORMULA", "PDF_PAGE", "OTHER").contains(type)) {
            throw ApiException.badRequest("INVALID_ASSET_TYPE", "未知附件类型: " + assetType);
        }
        repository.linkAsset(id, assetId, type, sortOrder);
        audit.record(principal, "question.linkAsset", "Question", id, "SUCCESS", null);
    }

    public List<QuestionAsset> assets(UUID id) {
        get(id);
        return repository.assets(id);
    }

    public PageResponse<QuestionRow> search(
            UUID subjectId, String grade, String questionType, Integer minDifficulty, Integer maxDifficulty,
            UUID knowledgePointId, String sourceType, String reviewStatus, String query,
            int limit, int offset
    ) {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        int safeOffset = Math.max(0, offset);
        if (questionType != null && !questionType.isBlank() && !TYPES.contains(questionType)) {
            throw ApiException.badRequest("INVALID_QUESTION_TYPE", "未知题型: " + questionType);
        }
        if (sourceType != null && !sourceType.isBlank() && !SOURCES.contains(sourceType)) {
            throw ApiException.badRequest("INVALID_SOURCE_TYPE", "未知来源: " + sourceType);
        }
        if (reviewStatus != null && !reviewStatus.isBlank() && !REVIEW.contains(reviewStatus)) {
            throw ApiException.badRequest("INVALID_REVIEW_STATUS", "未知审核状态: " + reviewStatus);
        }
        List<QuestionRow> items = repository.search(subjectId, grade, questionType, minDifficulty,
                maxDifficulty, knowledgePointId, sourceType, reviewStatus, query, safeLimit, safeOffset);
        long total = repository.count(subjectId, grade, questionType, minDifficulty, maxDifficulty,
                knowledgePointId, sourceType, reviewStatus, query);
        return new PageResponse<>(items, safeLimit, safeOffset, total);
    }

    private void applyChildren(
            UUID questionId,
            List<QuestionAnswer.OptionInput> options,
            QuestionAnswer answer,
            List<QuestionAnswer.KnowledgeLink> knowledge
    ) {
        repository.replaceOptions(questionId, options);
        repository.upsertAnswer(questionId, answer);
        if (knowledge != null) {
            for (QuestionAnswer.KnowledgeLink link : knowledge) {
                if (!IMPORTANCE.contains(link.importance() == null ? "PRIMARY" : link.importance())) {
                    throw ApiException.badRequest("INVALID_IMPORTANCE", "未知知识点重要度");
                }
            }
        }
        repository.replaceKnowledge(questionId, knowledge);
    }

    private void validate(String type, int difficulty, String sourceType, String copyrightStatus,
                          String usageScope, String reviewStatus) {
        if (type == null || !TYPES.contains(type)) {
            throw ApiException.badRequest("INVALID_QUESTION_TYPE", "未知题型: " + type);
        }
        if (difficulty < 1 || difficulty > 5) {
            throw ApiException.badRequest("INVALID_DIFFICULTY", "难度必须为 1..5");
        }
        if (sourceType != null && !sourceType.isBlank() && !SOURCES.contains(sourceType)) {
            throw ApiException.badRequest("INVALID_SOURCE_TYPE", "未知来源: " + sourceType);
        }
        if (copyrightStatus != null && !copyrightStatus.isBlank() && !COPYRIGHT.contains(copyrightStatus)) {
            throw ApiException.badRequest("INVALID_COPYRIGHT_STATUS", "未知版权状态");
        }
        if (usageScope != null && !usageScope.isBlank() && !USAGE.contains(usageScope)) {
            throw ApiException.badRequest("INVALID_USAGE_SCOPE", "未知使用范围");
        }
        if (reviewStatus != null && !reviewStatus.isBlank() && !REVIEW.contains(reviewStatus)) {
            throw ApiException.badRequest("INVALID_REVIEW_STATUS", "未知审核状态");
        }
    }

    private void requireSubject(UUID subjectId) {
        if (subjectId == null) {
            throw ApiException.badRequest("SUBJECT_REQUIRED", "必须指定学科");
        }
        Integer count = jdbc.queryForObject("select count(*) from subjects where id = ?", Integer.class, subjectId);
        if (count == null || count == 0) {
            throw ApiException.badRequest("SUBJECT_NOT_FOUND", "学科不存在: " + subjectId);
        }
    }

    private static String requireStem(String stem) {
        if (stem == null || stem.isBlank()) {
            throw ApiException.badRequest("STEM_REQUIRED", "题干不能为空");
        }
        return stem.trim();
    }

    private String snapshot(QuestionDetail detail) {
        try {
            return objectMapper.writeValueAsString(detail);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize question snapshot", exception);
        }
    }

    private static String payload(UUID id, String reviewStatus) {
        return "{\"id\":\"" + id + "\",\"reviewStatus\":\"" + reviewStatus + "\"}";
    }

    private static final Pattern LEADING_NUMBER = Pattern.compile(
            "^\\s*[（(]?\\s*(?:\\d+|[一二三四五六七八九十]+)\\s*[.、)）．]\\s*");

    private static String normalizeStem(String stem) {
        String normalized = LEADING_NUMBER.matcher(stem.toLowerCase(Locale.ROOT)).replaceFirst("")
                .replaceAll("[\\p{Punct}\\s，。；：！？、【】（）《》“”‘’]", "");
        return Ids.sha256Hex(normalized);
    }

    private static String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}

package com.leslie.teachtrack.practice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leslie.teachtrack.ai.AiProvider;
import com.leslie.teachtrack.audit.AuditService;
import com.leslie.teachtrack.auth.AuthPrincipal;
import com.leslie.teachtrack.common.ApiException;
import com.leslie.teachtrack.sync.SyncChangeRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PracticeService {
    private static final Set<String> MODES = Set.of(
            "BASIC", "ERROR_FOCUS", "TOPIC", "COMPREHENSIVE", "PRE_EXAM", "RETEST");

    private final PracticeRepository repository;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final AiProvider aiProvider;
    private final AuditService audit;
    private final SyncChangeRepository sync;

    public PracticeService(PracticeRepository repository, JdbcTemplate jdbc, ObjectMapper mapper,
                           AiProvider aiProvider, AuditService audit, SyncChangeRepository sync) {
        this.repository = repository;
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.aiProvider = aiProvider;
        this.audit = audit;
        this.sync = sync;
    }

    @Transactional
    public PracticeModels.PracticeDetail generate(PracticeModels.GenerateRequest request, AuthPrincipal principal) {
        UUID studentId = uuid(request.studentId(), "STUDENT_REQUIRED", "学生 ID 必填");
        UUID subjectId = uuid(request.subjectId(), "SUBJECT_REQUIRED", "学科 ID 必填");
        require("select count(*) from students where id = ? and deleted_at is null", studentId, "学生不存在");
        require("select count(*) from subjects where id = ?", subjectId, "学科不存在");

        int count = request.questionCount() == null ? 10 : Math.max(1, Math.min(request.questionCount(), 50));
        int minDifficulty = request.minDifficulty() == null ? 1 : Math.max(1, Math.min(request.minDifficulty(), 5));
        int maxDifficulty = request.maxDifficulty() == null ? 5 : Math.max(minDifficulty, Math.min(request.maxDifficulty(), 5));
        String mode = request.mode() == null ? "BASIC" : request.mode().toUpperCase(Locale.ROOT);
        if (!MODES.contains(mode)) {
            throw ApiException.badRequest("INVALID_MODE", "未知练习模式: " + request.mode());
        }
        List<UUID> knowledgeIds = new ArrayList<>();
        if (request.knowledgePointIds() != null) {
            for (String id : request.knowledgePointIds()) {
                knowledgeIds.add(uuid(id, "INVALID_KNOWLEDGE_POINT", "知识点 ID 无效"));
            }
        }
        List<PracticeRepository.Candidate> candidates = repository.selectCandidates(
                subjectId, knowledgeIds, minDifficulty, maxDifficulty, count);
        if (candidates.size() < count && Boolean.TRUE.equals(request.allowAi())) {
            List<String> names = knowledgeIds.stream()
                    .map(id -> jdbc.query("select name from knowledge_points where id = ?",
                            (rs, rowNum) -> rs.getString("name"), id).stream().findFirst().orElse(""))
                    .filter(name -> !name.isBlank())
                    .toList();
            aiProvider.generateQuestions(new AiProvider.QuestionGenerationRequest(
                    subjectId, "", names, count - candidates.size(), minDifficulty, maxDifficulty, mode));
            // Mock provider intentionally returns nothing; only approved bank questions are used.
            candidates = repository.selectCandidates(subjectId, knowledgeIds, minDifficulty, maxDifficulty, count);
        }
        if (candidates.isEmpty()) {
            Integer approvedTotal = jdbc.queryForObject("""
                    select count(*) from questions
                    where subject_id = ? and review_status = 'APPROVED' and deleted_at is null
                    """, Integer.class, subjectId);
            int available = approvedTotal == null ? 0 : approvedTotal;
            String message = available == 0
                    ? "该学科还没有已审核题目。请先在题库录入完整题目并保存通过，再生成练习。"
                    : "该学科有 " + available + " 道已审核题，但没有题目符合当前难度或知识点筛选。请放宽筛选后重试。";
            if (Boolean.TRUE.equals(request.allowAi())) {
                message += " AI 自动出题未配置或没有通过质量门，本次不会伪造题目。";
            }
            throw ApiException.badRequest("INSUFFICIENT_QUESTIONS", message);
        }
        if (candidates.size() < count) {
            count = candidates.size();
        }

        UUID practiceId = UUID.randomUUID();
        double totalScore = request.totalScore() == null || request.totalScore() <= 0
                ? count * 5.0 : request.totalScore();
        double perItem = totalScore / count;
        String title = request.title() == null || request.title().isBlank()
                ? "精准练习-" + mode : request.title();
        Map<String, Object> strategy = new HashMap<>();
        strategy.put("requestedCount", request.questionCount());
        strategy.put("minDifficulty", minDifficulty);
        strategy.put("maxDifficulty", maxDifficulty);
        strategy.put("knowledgePointIds", knowledgeIds);
        strategy.put("includeMistakes", request.includeMistakes());
        strategy.put("allowAi", request.allowAi());
        try {
            repository.insertSet(practiceId, studentId, subjectId, mode, title,
                    mapper.writeValueAsString(strategy), totalScore, count * 5, principal.userId());
        } catch (Exception exception) {
            throw new IllegalStateException("无法创建练习", exception);
        }
        int sort = 0;
        for (PracticeRepository.Candidate candidate : candidates.subList(0, count)) {
            repository.insertItem(UUID.randomUUID(), practiceId, candidate.id(), "BANK",
                    "已审核题库；难度 " + candidate.difficulty(), perItem, sort++);
        }
        audit.record(principal, "practice.generate", "PracticeSet", practiceId, "SUCCESS", null);
        sync.append("practiceSet", practiceId, "UPSERT", 1, null, "{\"id\":\"" + practiceId + "\"}");
        return detail(practiceId);
    }

    public PracticeModels.PracticeDetail detail(UUID practiceId) {
        PracticeModels.PracticeSetRow set = repository.findSet(practiceId)
                .orElseThrow(() -> ApiException.notFound("练习不存在: " + practiceId));
        return new PracticeModels.PracticeDetail(set, repository.listItems(practiceId),
                repository.mastery(set.studentId()));
    }

    public List<PracticeModels.PracticeSetRow> listByStudent(UUID studentId, int limit) {
        return jdbc.query("""
                select id, student_id, subject_id, mode, title, total_score, estimated_minutes, status,
                       version, created_at
                from practice_sets where student_id = ? and deleted_at is null
                order by created_at desc limit ?
                """, (rs, rowNum) -> new PracticeModels.PracticeSetRow(
                rs.getObject("id", UUID.class),
                rs.getObject("student_id", UUID.class),
                rs.getObject("subject_id", UUID.class),
                rs.getString("mode"),
                rs.getString("title"),
                rs.getBigDecimal("total_score").doubleValue(),
                rs.getInt("estimated_minutes"),
                rs.getString("status"),
                rs.getInt("version"),
                rs.getTimestamp("created_at").toInstant()
        ), studentId, Math.max(1, Math.min(limit, 200)));
    }

    @Transactional
    public PracticeModels.PracticeDetail approve(UUID practiceId, AuthPrincipal principal) {
        repository.findSet(practiceId).orElseThrow(() -> ApiException.notFound("练习不存在"));
        repository.updateStatus(practiceId, "APPROVED");
        audit.record(principal, "practice.approve", "PracticeSet", practiceId, "SUCCESS", null);
        return detail(practiceId);
    }

    @Transactional
    public PracticeModels.PracticeDetail submit(UUID practiceId, PracticeModels.SubmitRequest request,
                                                 AuthPrincipal principal) {
        PracticeModels.PracticeSetRow set = repository.findSet(practiceId)
                .orElseThrow(() -> ApiException.notFound("练习不存在"));
        if ("COMPLETED".equals(set.status())) {
            throw ApiException.badRequest("ALREADY_COMPLETED", "该练习已提交");
        }
        List<PracticeModels.PracticeItemRow> items = repository.listItems(practiceId);
        if (items.isEmpty()) {
            throw ApiException.badRequest("EMPTY_PRACTICE", "练习没有题目");
        }
        Map<UUID, PracticeModels.AnswerInput> answers = new HashMap<>();
        if (request.answers() != null) {
            request.answers().forEach(answer -> answers.put(answer.itemId(), answer));
        }
        UUID attemptId = UUID.randomUUID();
        repository.insertAttempt(attemptId, practiceId, set.studentId(), 0, 0);
        double total = 0;
        double fullTotal = 0;
        Set<UUID> knowledgeToRecompute = new LinkedHashSet<>();
        for (PracticeModels.PracticeItemRow item : items) {
            PracticeModels.AnswerInput answer = answers.get(item.id());
            String studentAnswer = answer == null || answer.studentAnswer() == null
                    ? "" : answer.studentAnswer().trim();
            String standard = repository.standardAnswer(item.questionId());
            boolean correct;
            double score;
            if (answer != null && answer.score() != null) {
                score = Math.max(0, Math.min(answer.score(), item.score()));
                correct = answer.correct() != null ? answer.correct() : score >= item.score();
            } else if (answer != null && answer.correct() != null) {
                correct = answer.correct();
                score = correct ? item.score() : 0;
            } else {
                correct = normalize(studentAnswer).equals(normalize(standard)) && !standard.isBlank();
                score = correct ? item.score() : 0;
            }
            repository.insertResult(UUID.randomUUID(), attemptId, item.id(), studentAnswer, score,
                    item.score(), correct);
            total += score;
            fullTotal += item.score();
            knowledgeToRecompute.addAll(repository.knowledgeIds(item.questionId()));
        }
        repository.updateAttempt(attemptId, total, fullTotal);
        for (UUID knowledgeId : knowledgeToRecompute) {
            recomputeMastery(set.studentId(), knowledgeId);
        }
        repository.updateStatus(practiceId, "COMPLETED");
        audit.record(principal, "practice.submit", "PracticeSet", practiceId, "SUCCESS", null);
        return detail(practiceId);
    }

    public MasteryModels.MasteryRow recomputeMastery(UUID studentId, UUID knowledgeId) {
        List<MasteryModels.Evidence> evidence = repository.evidence(studentId, knowledgeId);
        int correctCount = 0;
        int wrongCount = 0;
        double weighted = 0;
        double totalWeight = 0;
        Instant last = null;
        for (MasteryModels.Evidence item : evidence) {
            if (item.correct()) {
                correctCount++;
            } else {
                wrongCount++;
            }
            double difficultyWeight = 0.6 + 0.2 * (Math.max(1, Math.min(item.difficulty(), 5)) - 1);
            long days = Math.max(0, Duration.between(item.occurredAt(), Instant.now()).toDays());
            double recency = Math.pow(0.5, days / 45.0);
            double weight = difficultyWeight * recency;
            totalWeight += weight;
            weighted += weight * (item.correct() ? 1 : 0);
            if (last == null || item.occurredAt().isAfter(last)) {
                last = item.occurredAt();
            }
        }
        double raw = totalWeight > 0 ? 100 * weighted / totalWeight : 0;
        double shrunk = (totalWeight / (totalWeight + 2)) * raw + (2 / (totalWeight + 2)) * 50;
        double repeatPenalty = Math.min(20, 4.0 * Math.min(wrongCount, 5));
        double mastery = Math.max(0, Math.min(100, shrunk - repeatPenalty));
        double confidence = Math.min(1, totalWeight / 5);
        String trend = String.format(Locale.ROOT, "%.3f", trend(evidence));
        repository.upsertMastery(studentId, knowledgeId, mastery, confidence, evidence.size(),
                correctCount, wrongCount, last, trend);
        return new MasteryModels.MasteryRow(knowledgeId, mastery, confidence, evidence.size(),
                correctCount, wrongCount, trend);
    }

    private static double trend(List<MasteryModels.Evidence> evidence) {
        if (evidence.size() < 2) {
            return 0;
        }
        int window = Math.min(3, evidence.size() / 2);
        double first = 0;
        double last = 0;
        for (int i = 0; i < window; i++) {
            first += evidence.get(i).correct() ? 1 : 0;
            last += evidence.get(evidence.size() - 1 - i).correct() ? 1 : 0;
        }
        return (last - first) / window;
    }

    private void require(String sql, UUID id, String message) {
        Integer count = jdbc.queryForObject(sql, Integer.class, id);
        if (count == null || count == 0) {
            throw ApiException.badRequest("NOT_FOUND", message);
        }
    }

    private static UUID uuid(String value, String code, String message) {
        if (value == null || value.isBlank()) {
            throw ApiException.badRequest(code, message);
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw ApiException.badRequest("INVALID_ID", "ID 格式无效: " + value);
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }
}

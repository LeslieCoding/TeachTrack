package com.leslie.teachtrack.practice;

import com.lowagie.text.Document;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfWriter;
import com.leslie.teachtrack.audit.AuditService;
import com.leslie.teachtrack.auth.AuthPrincipal;
import com.leslie.teachtrack.common.ApiException;
import com.leslie.teachtrack.common.Ids;
import com.leslie.teachtrack.storage.AssetRepository;
import com.leslie.teachtrack.storage.AssetService;
import com.leslie.teachtrack.storage.ObjectStorageProvider;
import com.leslie.teachtrack.storage.PresignedUrl;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class PracticeExportService {
    public record ExportResult(UUID assetId, String format, String variant, String downloadUrl) {
    }

    private final PracticeRepository repository;
    private final PracticeService practiceService;
    private final ObjectStorageProvider storage;
    private final AssetRepository assets;
    private final AssetService assetService;
    private final AuditService audit;
    private final String cjkFont;

    public PracticeExportService(PracticeRepository repository, PracticeService practiceService,
                                 ObjectStorageProvider storage, AssetRepository assets,
                                 AssetService assetService, AuditService audit,
                                 @Value("${teachtrack.export.cjk-font:C:/Windows/Fonts/simhei.ttf}") String cjkFont) {
        this.repository = repository;
        this.practiceService = practiceService;
        this.storage = storage;
        this.assets = assets;
        this.assetService = assetService;
        this.audit = audit;
        this.cjkFont = cjkFont;
    }

    public ExportResult export(UUID practiceId, String variant, String format, AuthPrincipal principal) {
        PracticeModels.PracticeDetail detail = practiceService.detail(practiceId);
        String safeVariant = variant == null ? "STUDENT" : variant.trim().toUpperCase(Locale.ROOT);
        if (!safeVariant.equals("STUDENT") && !safeVariant.equals("TEACHER")) {
            throw ApiException.badRequest("INVALID_VARIANT", "variant 仅支持 STUDENT / TEACHER");
        }
        String safeFormat = format == null ? "PDF" : format.trim().toUpperCase(Locale.ROOT);
        if (!safeFormat.equals("PDF") && !safeFormat.equals("DOCX")) {
            throw ApiException.badRequest("INVALID_FORMAT", "format 仅支持 PDF / DOCX");
        }
        List<PracticeRepository.ExportItem> items = repository.exportItems(practiceId);
        String studentName = repository.studentName(detail.set().studentId());
        byte[] bytes;
        String mime;
        String extension;
        if (safeFormat.equals("PDF")) {
            bytes = renderPdf(detail, items, studentName, safeVariant.equals("TEACHER"));
            mime = "application/pdf";
            extension = "pdf";
        } else {
            bytes = renderDocx(detail, items, studentName, safeVariant.equals("TEACHER"));
            mime = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            extension = "docx";
        }
        String key = "exports/" + practiceId + "/" + safeVariant.toLowerCase(Locale.ROOT) + "." + extension;
        storage.put(key, bytes, mime);
        UUID assetId = UUID.randomUUID();
        assets.insert(new AssetRepository.AssetRow(
                assetId, "PRACTICE", practiceId, storage.providerId(), key, mime,
                bytes.length, Ids.sha256Hex(bytes), "PENDING"));
        assets.markReady(assetId, bytes.length, Ids.sha256Hex(bytes));
        PresignedUrl url = assetService.downloadUrl(assetId);
        audit.record(principal, "practice.export", "PracticeSet", practiceId, "SUCCESS", null);
        return new ExportResult(assetId, safeFormat, safeVariant, url.url());
    }

    private byte[] renderPdf(PracticeModels.PracticeDetail detail, List<PracticeRepository.ExportItem> items,
                             String studentName, boolean teacher) {
        if (!Files.isRegularFile(Path.of(cjkFont))) {
            throw new IllegalStateException("中文字体不存在: " + cjkFont);
        }
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 36, 36, 36, 36);
            PdfWriter.getInstance(document, out);
            document.open();
            BaseFont base = BaseFont.createFont(cjkFont, BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
            Font titleFont = new Font(base, 18, Font.BOLD);
            Font bodyFont = new Font(base, 11);
            Font smallFont = new Font(base, 9);

            document.add(new Paragraph(teacher ? "教师版 · " + detail.set().title() : "学生版 · " + detail.set().title(), titleFont));
            document.add(new Paragraph("学生：" + studentName + "    日期：" + LocalDate.now(), bodyFont));
            document.add(new Paragraph("总分：" + detail.set().totalScore() + "    预计用时：" + detail.set().estimatedMinutes() + " 分钟", smallFont));
            document.add(new Paragraph(" "));
            int index = 1;
            for (PracticeRepository.ExportItem item : items) {
                document.add(new Paragraph(index + ". [" + item.questionType() + " · 难度 " + item.difficulty()
                        + " · " + item.score() + " 分]", smallFont));
                document.add(new Paragraph(item.stem(), bodyFont));
                if (teacher) {
                    document.add(new Paragraph("答案：" + item.standardAnswer(), smallFont));
                    if (!item.analysis().isBlank()) {
                        document.add(new Paragraph("解析：" + item.analysis(), smallFont));
                    }
                    if (!item.knowledge().isBlank()) {
                        document.add(new Paragraph("知识点：" + item.knowledge(), smallFont));
                    }
                    if (!item.selectionReason().isBlank()) {
                        document.add(new Paragraph("选题理由：" + item.selectionReason(), smallFont));
                    }
                } else {
                    document.add(new Paragraph("答题区：", smallFont));
                    document.add(new Paragraph("\n\n"));
                }
                index++;
            }
            document.close();
            return out.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException("PDF 导出失败: " + exception.getMessage(), exception);
        }
    }

    private byte[] renderDocx(PracticeModels.PracticeDetail detail, List<PracticeRepository.ExportItem> items,
                              String studentName, boolean teacher) {
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            addParagraph(document, (teacher ? "教师版 · " : "学生版 · ") + detail.set().title(), 18, true);
            addParagraph(document, "学生：" + studentName + "    日期：" + LocalDate.now(), 11, false);
            addParagraph(document, "总分：" + detail.set().totalScore() + "    预计用时："
                    + detail.set().estimatedMinutes() + " 分钟", 9, false);
            int index = 1;
            for (PracticeRepository.ExportItem item : items) {
                addParagraph(document, index + ". [" + item.questionType() + " · 难度 " + item.difficulty()
                        + " · " + item.score() + " 分]", 10, true);
                addParagraph(document, item.stem(), 11, false);
                if (teacher) {
                    addParagraph(document, "答案：" + item.standardAnswer(), 9, false);
                    if (!item.analysis().isBlank()) {
                        addParagraph(document, "解析：" + item.analysis(), 9, false);
                    }
                    if (!item.knowledge().isBlank()) {
                        addParagraph(document, "知识点：" + item.knowledge(), 9, false);
                    }
                    if (!item.selectionReason().isBlank()) {
                        addParagraph(document, "选题理由：" + item.selectionReason(), 9, false);
                    }
                } else {
                    addParagraph(document, "答题区：", 9, false);
                    addParagraph(document, " ", 11, false);
                    addParagraph(document, " ", 11, false);
                }
                index++;
            }
            document.write(out);
            return out.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException("DOCX 导出失败: " + exception.getMessage(), exception);
        }
    }

    private static void addParagraph(XWPFDocument document, String text, int size, boolean bold) {
        XWPFParagraph paragraph = document.createParagraph();
        XWPFRun run = paragraph.createRun();
        run.setText(text);
        run.setFontSize(size);
        run.setBold(bold);
        run.setFontFamily("SimSun");
        run.setFontFamily("SimSun", XWPFRun.FontCharRange.eastAsia);
    }
}

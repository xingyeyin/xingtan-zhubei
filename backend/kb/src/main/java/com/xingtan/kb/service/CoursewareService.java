package com.xingtan.kb.service;

import com.xingtan.common.exception.BusinessException;
import com.xingtan.kb.entity.KbChunk;
import com.xingtan.kb.entity.KbDocument;
import com.xingtan.kb.mapper.KbChunkMapper;
import com.xingtan.kb.mapper.KbDocumentMapper;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 课件导入服务：保存文件并抽取文本，写入知识库
 */
@Service
@RequiredArgsConstructor
public class CoursewareService {

    private final KbDocumentMapper documentMapper;
    private final KbChunkMapper chunkMapper;

    public KbDocument importFile(Long userId, String filename, String title, String subject, String docType, byte[] data)
            throws Exception {
        String ext = filename == null ? "" : filename.toLowerCase();
        String text = extractText(ext, data);
        if (text == null || text.isBlank()) {
            throw new BusinessException(400, "未能从文件中提取文本，请确认文件为 PPT/PDF/Word/文本格式");
        }
        Path dir = Paths.get("uploads");
        Files.createDirectories(dir);
        String saveName = userId + "_" + System.currentTimeMillis() + "_"
                + filename.replaceAll("[\\\\/:*?\"<>|]", "_");
        Files.write(dir.resolve(saveName), data);

        KbDocument doc = new KbDocument();
        doc.setTitle(title == null || title.isBlank() ? filename : title);
        doc.setDocType(docType == null || docType.isBlank() ? "COURSEWARE" : docType);
        doc.setUserId(userId);
        doc.setSubject(subject);
        doc.setStatus(1);
        doc.setMeta("{\"file\":\"" + saveName + "\",\"size\":" + data.length + "}");
        documentMapper.insert(doc);

        int seq = 0;
        for (String line : text.split("\\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.length() > 1500) {
                trimmed = trimmed.substring(0, 1500);
            }
            insertChunk(doc.getId(), seq++, trimmed);
        }
        if (seq == 0) {
            insertChunk(doc.getId(), seq, text.length() > 1500 ? text.substring(0, 1500) : text);
        }
        return doc;
    }

    private void insertChunk(Long docId, int seq, String content) {
        KbChunk chunk = new KbChunk();
        chunk.setDocId(docId);
        chunk.setSeq(seq);
        chunk.setContent(content);
        chunkMapper.insert(chunk);
    }

    private String extractText(String ext, byte[] data) throws Exception {
        if (ext.endsWith(".pptx")) {
            try (XMLSlideShow ppt = new XMLSlideShow(new ByteArrayInputStream(data))) {
                StringBuilder sb = new StringBuilder();
                ppt.getSlides().forEach(slide -> {
                    for (XSLFShape shape : slide.getShapes()) {
                        if (shape instanceof XSLFTextShape) {
                            sb.append(((XSLFTextShape) shape).getText()).append("\n");
                        }
                    }
                });
                return sb.toString();
            }
        }
        if (ext.endsWith(".pdf")) {
            try (PDDocument document = PDDocument.load(data)) {
                return new PDFTextStripper().getText(document);
            }
        }
        if (ext.endsWith(".docx")) {
            try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(data))) {
                StringBuilder sb = new StringBuilder();
                for (XWPFParagraph p : document.getParagraphs()) {
                    sb.append(p.getText()).append("\n");
                }
                for (XWPFTable table : document.getTables()) {
                    for (XWPFTableRow row : table.getRows()) {
                        for (XWPFTableCell cell : row.getTableCells()) {
                            sb.append(cell.getText()).append("\t");
                        }
                        sb.append("\n");
                    }
                }
                return sb.toString();
            }
        }
        if (ext.endsWith(".txt") || ext.endsWith(".md")) {
            return new String(data, StandardCharsets.UTF_8);
        }
        throw new BusinessException(400, "暂不支持该格式（支持 pptx/pdf/docx/txt/md）");
    }
}

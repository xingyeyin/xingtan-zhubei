package com.xingtan.kb.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xingtan.common.result.PageResult;
import com.xingtan.common.result.Result;
import com.xingtan.kb.entity.KbChunk;
import com.xingtan.kb.entity.KbDocument;
import com.xingtan.kb.mapper.KbChunkMapper;
import com.xingtan.kb.mapper.KbDocumentMapper;
import com.xingtan.kb.service.CoursewareService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 知识库接口
 * TODO(骨架阶段): 文档上传/解析/分块/向量化接入 RAG 管线
 */
@RestController
@RequestMapping("/api/kb")
@RequiredArgsConstructor
public class KbController {

    private final KbDocumentMapper documentMapper;
    private final KbChunkMapper chunkMapper;
    private final CoursewareService coursewareService;

    @GetMapping("/documents")
    public Result<PageResult<KbDocument>> documents(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String docType) {
        LambdaQueryWrapper<KbDocument> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(docType != null && !docType.isBlank(), KbDocument::getDocType, docType)
                .orderByDesc(KbDocument::getId);
        Page<KbDocument> result = documentMapper.selectPage(new Page<>(page, size), wrapper);
        return Result.ok(new PageResult<>(result.getTotal(), page, size, result.getRecords()));
    }

    /**
     * 教材目录树：按 学科→年级→版本 返回教材单元目录，支撑"教材版本自适应"演示。
     * GET /api/kb/textbooks（公开）
     */
    @GetMapping("/textbooks")
    public Result<List<Map<String, Object>>> textbooks() {
        List<KbDocument> docs = documentMapper.selectList(
                new LambdaQueryWrapper<KbDocument>()
                        .eq(KbDocument::getDocType, "TEXTBOOK")
                        .orderByAsc(KbDocument::getSubject)
                        .orderByAsc(KbDocument::getGrade));
        if (docs.isEmpty()) {
            return Result.ok(List.of());
        }
        List<Long> docIds = docs.stream().map(KbDocument::getId).toList();
        Map<Long, List<String>> unitsByDoc = chunkMapper.selectList(
                        new LambdaQueryWrapper<KbChunk>().in(KbChunk::getDocId, docIds))
                .stream()
                .sorted(Comparator.comparing(KbChunk::getSeq))
                .collect(Collectors.groupingBy(KbChunk::getDocId,
                        Collectors.mapping(KbChunk::getContent, Collectors.toList())));

        List<Map<String, Object>> tree = new ArrayList<>();
        for (KbDocument doc : docs) {
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("subject", doc.getSubject());
            node.put("grade", doc.getGrade());
            node.put("version", doc.getTextbookVersion());
            node.put("title", doc.getTitle());
            node.put("units", unitsByDoc.getOrDefault(doc.getId(), List.of()));
            tree.add(node);
        }
        return Result.ok(tree);
    }

    @PostMapping("/upload")
    public Result<KbDocument> upload(@RequestParam("file") MultipartFile file,
                                     @RequestParam(required = false) String title,
                                     @RequestParam(required = false) String subject,
                                     @RequestParam(required = false) String docType,
                                     HttpServletRequest request) throws Exception {
        Long userId = Long.valueOf(request.getAttribute("userId").toString());
        if (docType != null && !docType.isBlank()
                && !List.of("STANDARD", "TEXTBOOK", "TEMPLATE", "CASE", "COURSEWARE").contains(docType)) {
            return Result.fail(400, "不支持的文档类型");
        }
        KbDocument doc = coursewareService.importFile(userId, file.getOriginalFilename(),
                title, subject, docType, file.getBytes());
        return Result.ok(doc);
    }

    /**
     * 知识库文档分块预览（支持管理端"详情"查看已解析内容）
     */
    @GetMapping("/documents/{id}/chunks")
    public Result<List<KbChunk>> chunks(@PathVariable Long id) {
        List<KbChunk> chunks = chunkMapper.selectList(
                new LambdaQueryWrapper<KbChunk>()
                        .eq(KbChunk::getDocId, id)
                        .orderByAsc(KbChunk::getSeq));
        return Result.ok(chunks);
    }

    @GetMapping("/my")
    public Result<PageResult<KbDocument>> my(HttpServletRequest request,
                                             @RequestParam(defaultValue = "1") long page,
                                             @RequestParam(defaultValue = "20") long size) {
        Long userId = Long.valueOf(request.getAttribute("userId").toString());
        LambdaQueryWrapper<KbDocument> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KbDocument::getUserId, userId).eq(KbDocument::getDocType, "COURSEWARE")
                .orderByDesc(KbDocument::getId);
        Page<KbDocument> result = documentMapper.selectPage(new Page<>(page, size), wrapper);
        return Result.ok(new PageResult<>(result.getTotal(), page, size, result.getRecords()));
    }
}

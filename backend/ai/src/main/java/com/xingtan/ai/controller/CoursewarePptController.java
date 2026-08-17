package com.xingtan.ai.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xingtan.ai.gateway.LlmRouter;
import com.xingtan.ai.ppt.PptDesigner;
import com.xingtan.common.exception.BusinessException;
import com.xingtan.kb.entity.KbChunk;
import com.xingtan.kb.entity.KbDocument;
import com.xingtan.kb.mapper.KbChunkMapper;
import com.xingtan.kb.mapper.KbDocumentMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 导入课件 → AI 美化课件
 */
@Slf4j
@RestController
@RequestMapping("/api/ai/courseware")
@RequiredArgsConstructor
public class CoursewarePptController {

    private final KbDocumentMapper documentMapper;
    private final KbChunkMapper chunkMapper;
    private final LlmRouter llmRouter;
    private final ObjectMapper objectMapper;
    private final PptDesigner pptDesigner;

    @GetMapping("/{docId}/export-ppt")
    public ResponseEntity<byte[]> exportPpt(@PathVariable Long docId, HttpServletRequest request)
            throws Exception {
        KbDocument doc = documentMapper.selectById(docId);
        if (doc == null) {
            throw new BusinessException(404, "课件不存在");
        }
        Object attr = request.getAttribute("userId");
        if (attr != null && doc.getUserId() != null
                && !doc.getUserId().equals(Long.valueOf(attr.toString()))) {
            throw new BusinessException(403, "无权操作他人的课件");
        }
        List<KbChunk> chunks = chunkMapper.selectList(
                new LambdaQueryWrapper<KbChunk>().eq(KbChunk::getDocId, docId).orderByAsc(KbChunk::getSeq));
        String content = chunks.stream().map(KbChunk::getContent).collect(Collectors.joining("\n"));
        if (content.length() > 6000) {
            content = content.substring(0, 6000);
        }
        JsonNode deck = requestDeck(doc.getTitle(), doc.getSubject(), content);
        byte[] bytes = pptDesigner.build(deck, doc.getTitle(), doc.getSubject() == null ? "" : doc.getSubject());
        String filename = URLEncoder.encode(
                (doc.getTitle() == null ? "课件" : doc.getTitle()) + "（AI美化课件）.pptx",
                StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.presentationml.presentation"))
                .body(bytes);
    }

    private JsonNode requestDeck(String title, String subject, String content) {
        try {
            String system = "你是资深教学课件设计专家。请基于教师上传的课件内容，设计一份精美课件的逐页方案，"
                    + "输出 JSON 数组，每项为 {\"type\":\"cover|agenda|content|activity|summary|ending\","
                    + "\"title\":\"页面标题\",\"subtitle\":\"副标题(可空)\","
                    + "\"bullets\":[{\"text\":\"要点\",\"level\":0}],"
                    + "\"notes\":\"教师讲稿提示\",\"visual\":\"配图/素材建议\"}。"
                    + "要求：紧扣课件内容；结构完整（封面、核心内容与目标、重难点、教学环节、课堂互动、"
                    + "课堂练习、课堂小结、结尾）；每页要点精炼（3~6 条，每条不超过 25 字）；"
                    + "为每页写教师讲稿提示与配图建议；共 12~16 页。只输出 JSON。";
            String user = "课件标题：" + title
                    + "\n学科：" + (subject == null ? "" : subject)
                    + "\n课件内容摘要：\n" + content;
            String raw = llmRouter.completeJson(null, system, user);
            JsonNode arr = parseJson(raw);
            if (arr != null && arr.isArray() && arr.size() > 0) {
                return arr;
            }
        } catch (Exception e) {
            log.warn("AI 课件 PPT 生成失败，启动 Fallback 兜底：{}", e.getMessage());
        }
        return buildFallbackDeck(title, subject, content);
    }

    /**
     * 无 API Key 时从课件文本内容直接构建完整 12+ 页 deck
     */
    private JsonNode buildFallbackDeck(String title, String subject, String content) {
        ArrayNode deck = objectMapper.createArrayNode();
        String safeTitle = title == null || title.isBlank() ? "教学课件" : title;
        String safeSubject = subject == null ? "" : subject;

        // 按段落拆分课件内容
        String[] lines = content.split("\n");
        List<String> meaningfulLines = new ArrayList<>();
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && trimmed.length() > 2) {
                meaningfulLines.add(trimmed);
            }
        }

        // 1. 封面
        deck.add(slide("cover", safeTitle, safeSubject, bullets(), "开场白引入课题", "学科主题图"));

        // 2. 内容概览
        deck.add(slide("agenda", "内容概览", "", bullets(
                bullet("本课件共 " + meaningfulLines.size() + " 个知识模块", 0),
                bullet("按逻辑顺序逐一展开", 0),
                bullet(safeSubject.isBlank() ? "请结合教材同步学习" : "学科：" + safeSubject, 1)
        ), "引导学生了解整体结构", "目录图标"));

        // 3~N. 内容页（每3-4行一页，最多8页内容）
        int contentPageCount = 0;
        int maxContentPages = Math.min(8, (meaningfulLines.size() + 2) / 3);
        for (int i = 0; i < meaningfulLines.size() && contentPageCount < maxContentPages; i += 3) {
            ArrayNode pageBullets = bullets();
            for (int j = i; j < Math.min(i + 3, meaningfulLines.size()); j++) {
                pageBullets.add(bullet(truncate(meaningfulLines.get(j), 50), 0));
            }
            contentPageCount++;
            deck.add(slide("content", "核心内容（" + contentPageCount + "）", "", pageBullets,
                    "讲解要点，结合实例", "内容配图"));
        }

        // 如果内容太少，补充通用页面
        if (contentPageCount < 3) {
            deck.add(slide("content", "知识要点", "", bullets(
                    bullet("理解核心概念与定义", 0),
                    bullet("掌握关键方法与步骤", 0),
                    bullet("能够灵活应用解题", 0)
            ), "结合教材展开讲解", "知识点图标"));
        }

        // 课堂互动
        deck.add(slide("activity", "课堂互动", "", bullets(
                bullet("提问：本课核心知识点是什么？", 0),
                bullet("小组讨论：如何应用到实际问题", 0),
                bullet("代表发言，教师点评", 0)
        ), "巡视课堂，鼓励参与", "互动插图"));

        // 重难点
        deck.add(slide("content", "重难点解析", "", bullets(
                bullet("重点：掌握核心概念与方法", 0),
                bullet("难点：灵活应用与变式训练", 0),
                bullet("建议：多做练习，归纳总结", 1)
        ), "强调易错点", "重难点图标"));

        // 课堂小结
        deck.add(slide("summary", "课堂小结", "", bullets(
                bullet("回顾本课核心知识", 0),
                bullet("梳理方法与思路", 0),
                bullet("布置课后巩固练习", 0)
        ), "思维导图回顾", "思维导图"));

        // 结尾
        deck.add(slide("ending", "谢谢聆听 · 请批评指正", "", bullets(), "", ""));

        log.info("Fallback 课件 PPT deck 生成完成，共 {} 页", deck.size());
        return deck;
    }

    private ObjectNode slide(String type, String title, String subtitle, ArrayNode bullets, String notes, String visual) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("type", type);
        node.put("title", title);
        node.put("subtitle", subtitle);
        node.set("bullets", bullets);
        node.put("notes", notes);
        node.put("visual", visual);
        return node;
    }

    private ArrayNode bullets(String... texts) {
        ArrayNode arr = objectMapper.createArrayNode();
        for (String t : texts) {
            arr.add(bullet(t, 0));
        }
        return arr;
    }

    private ArrayNode bullets() {
        return objectMapper.createArrayNode();
    }

    private ObjectNode bullet(String text, int level) {
        ObjectNode b = objectMapper.createObjectNode();
        b.put("text", text);
        b.put("level", level);
        return b;
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) + "…" : s;
    }

    private JsonNode parseJson(String raw) throws Exception {
        String s = raw == null ? "" : raw.trim();
        if (s.startsWith("```")) {
            s = s.replaceFirst("^```[a-zA-Z]*", "").trim();
        }
        if (s.endsWith("```")) {
            s = s.substring(0, s.lastIndexOf("```")).trim();
        }
        return objectMapper.readTree(s);
    }
}

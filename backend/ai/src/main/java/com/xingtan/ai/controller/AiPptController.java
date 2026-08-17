package com.xingtan.ai.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xingtan.ai.gateway.LlmRouter;
import com.xingtan.ai.ppt.PptDesigner;
import com.xingtan.common.exception.BusinessException;
import com.xingtan.lesson.entity.LessonPlan;
import com.xingtan.lesson.mapper.LessonPlanMapper;
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

/**
 * 教案 → AI 美化课件
 */
@Slf4j
@RestController
@RequestMapping("/api/ai/lessons")
@RequiredArgsConstructor
public class AiPptController {

    private final LessonPlanMapper lessonPlanMapper;
    private final LlmRouter llmRouter;
    private final ObjectMapper objectMapper;
    private final PptDesigner pptDesigner;

    @GetMapping("/{id}/export-ppt")
    public ResponseEntity<byte[]> exportPpt(@PathVariable Long id) throws Exception {
        LessonPlan plan = lessonPlanMapper.selectById(id);
        if (plan == null) {
            throw new BusinessException(404, "教案不存在");
        }
        JsonNode root = objectMapper.readTree(plan.getContent());
        JsonNode deck = requestDeck(plan, root);
        String subtitle = (plan.getSubject() == null ? "" : plan.getSubject()) + " · "
                + (plan.getGrade() == null ? "" : plan.getGrade()) + " · "
                + (plan.getTextbook() == null ? "" : plan.getTextbook());
        byte[] bytes = pptDesigner.build(deck, plan.getTitle(), subtitle);
        String filename = URLEncoder.encode(
                (plan.getTitle() == null ? "教案" : plan.getTitle()) + "（AI美化课件）.pptx",
                StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.presentationml.presentation"))
                .body(bytes);
    }

    private JsonNode requestDeck(LessonPlan plan, JsonNode root) {
        try {
            String system = "你是资深教学课件设计专家。请根据教案内容设计一份精美课件的逐页方案，输出 JSON 数组，"
                    + "每项为 {\"type\":\"cover|agenda|content|activity|summary|ending\","
                    + "\"title\":\"页面标题\",\"subtitle\":\"副标题(可空)\","
                    + "\"bullets\":[{\"text\":\"要点\",\"level\":0}],"
                    + "\"notes\":\"教师讲稿提示\",\"visual\":\"配图/素材建议\"}。"
                    + "要求：紧扣教案；结构完整（封面、学习目标、教学重难点、教学过程按环节展开、课堂互动、"
                    + "课堂练习、课堂小结、分层作业、结尾）；每页要点精炼（3~6 条，每条不超过 25 字）；"
                    + "为每页写教师讲稿提示与配图建议；共 12~16 页。只输出 JSON。";
            String user = "教案标题：" + plan.getTitle()
                    + "\n学科/年级/教材：" + plan.getSubject() + " / " + plan.getGrade() + " / " + plan.getTextbook()
                    + "\n教案内容：" + root.toString();
            String raw = llmRouter.completeJson(null, system, user);
            JsonNode arr = parseJson(raw);
            if (arr != null && arr.isArray() && arr.size() > 0) {
                return arr;
            }
        } catch (Exception e) {
            log.warn("AI PPT 生成失败，启动 Fallback 兜底：{}", e.getMessage());
        }
        return buildFallbackDeck(plan, root);
    }

    /**
     * 无 API Key 时从教案 JSON 直接构建完整 12-15 页 deck
     */
    private JsonNode buildFallbackDeck(LessonPlan plan, JsonNode root) {
        ArrayNode deck = objectMapper.createArrayNode();
        String title = plan.getTitle() == null ? "教案" : plan.getTitle();
        String subject = plan.getSubject() == null ? "" : plan.getSubject();
        String grade = plan.getGrade() == null ? "" : plan.getGrade();

        // 1. 封面
        deck.add(slide("cover", title, subject + " · " + grade, bullets(), "开场白引入课题", "学科相关主题图"));

        // 2. 学习目标
        ArrayNode objBullets = bullets();
        for (JsonNode obj : root.path("objectives")) {
            String type = obj.path("type").asText("素养");
            String content = obj.path("content").asText("");
            objBullets.add(bullet(type + "：" + truncate(content, 40), 0));
        }
        if (objBullets.size() == 0) {
            objBullets.add(bullet("掌握本课核心知识与技能", 0));
            objBullets.add(bullet("培养学科核心素养", 0));
        }
        deck.add(slide("agenda", "学习目标", "", objBullets, "引导学生齐读目标", "目标图标"));

        // 3. 教学重点
        ArrayNode keyBullets = bullets();
        for (JsonNode kp : root.path("keyPoints")) {
            keyBullets.add(bullet(truncate(kp.asText(), 50), 0));
        }
        if (keyBullets.size() == 0) keyBullets.add(bullet("本课核心知识要点", 0));
        deck.add(slide("content", "教学重点", "", keyBullets, "强调重点，板书标注", "重点标记图标"));

        // 4. 教学难点
        ArrayNode diffBullets = bullets();
        for (JsonNode dp : root.path("difficultPoints")) {
            diffBullets.add(bullet(truncate(dp.asText(), 50), 0));
        }
        if (diffBullets.size() == 0) diffBullets.add(bullet("本课理解难点突破", 0));
        deck.add(slide("content", "教学难点", "", diffBullets, "结合学情分析难点", "灯泡图标"));

        // 5~N. 教学过程（每个环节一页）
        for (JsonNode p : root.path("process")) {
            String stage = p.path("stage").asText("教学环节");
            String duration = p.path("duration").asText("");
            ArrayNode pBullets = bullets();
            String activities = p.path("activities").asText("");
            if (!activities.isBlank()) pBullets.add(bullet(truncate(activities, 50), 0));
            String teacher = p.path("teacher").asText("");
            if (!teacher.isBlank()) pBullets.add(bullet("教师：" + truncate(teacher, 40), 1));
            String student = p.path("student").asText("");
            if (!student.isBlank()) pBullets.add(bullet("学生：" + truncate(student, 40), 1));
            if (pBullets.size() == 0) pBullets.add(bullet(stage, 0));
            String slideTitle = stage + (duration.isBlank() ? "" : "（" + duration + "分钟）");
            deck.add(slide("content", slideTitle, "", pBullets,
                    teacher.isBlank() ? "" : truncate(teacher, 60), "教学场景图"));
        }

        // 课堂互动
        deck.add(slide("activity", "课堂互动", "", bullets(
                bullet("小组讨论：围绕本课重难点展开", 0),
                bullet("代表发言，教师点评", 0),
                bullet("互动时长：5-8分钟", 1)
        ), "巡视各组，引导深度思考", "小组讨论插图"));

        // 课堂小结
        ArrayNode sumBullets = bullets();
        for (JsonNode kp : root.path("keyPoints")) {
            sumBullets.add(bullet(truncate(kp.asText(), 35), 0));
        }
        if (sumBullets.size() == 0) sumBullets.add(bullet("回顾本课核心知识", 0));
        sumBullets.add(bullet("梳理知识脉络与方法", 0));
        deck.add(slide("summary", "课堂小结", "", sumBullets, "思维导图形式回顾", "思维导图图标"));

        // 分层作业
        ArrayNode hwBullets = bullets();
        for (JsonNode hw : root.path("homework")) {
            String level = hw.path("level").asText("基础");
            String content = hw.path("content").asText("");
            hwBullets.add(bullet(level + "：" + truncate(content, 40), 0));
        }
        if (hwBullets.size() == 0) {
            hwBullets.add(bullet("基础：完成课后练习1-3题", 0));
            hwBullets.add(bullet("提高：选做拓展题", 0));
        }
        deck.add(slide("content", "分层作业", "", hwBullets, "分层布置，因材施教", "作业本图标"));

        // 结尾
        deck.add(slide("ending", "谢谢聆听 · 请批评指正", "", bullets(), "", ""));

        log.info("Fallback PPT deck 生成完成，共 {} 页", deck.size());
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

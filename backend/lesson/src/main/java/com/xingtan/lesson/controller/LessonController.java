package com.xingtan.lesson.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xingtan.common.result.PageResult;
import com.xingtan.common.exception.BusinessException;
import com.xingtan.common.result.Result;
import com.xingtan.lesson.entity.LessonPlan;
import com.xingtan.lesson.mapper.LessonPlanMapper;
import com.xingtan.lesson.entity.LessonSection;
import com.xingtan.lesson.mapper.LessonSectionMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextBox;
import org.apache.poi.xslf.usermodel.XSLFTextParagraph;
import org.apache.poi.xslf.usermodel.XSLFTextRun;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFFooter;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.HeaderFooterType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.awt.Rectangle;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 教案接口
 */
@RestController
@RequestMapping("/api/lessons")
@RequiredArgsConstructor
public class LessonController {

    private final LessonPlanMapper lessonPlanMapper;
    private final LessonSectionMapper sectionMapper;
    private final ObjectMapper objectMapper;

    @GetMapping
    public Result<PageResult<LessonPlan>> list(
            HttpServletRequest request,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        Object attr = request.getAttribute("userId");
        Long uid = attr != null ? Long.valueOf(attr.toString()) : userId;
        LambdaQueryWrapper<LessonPlan> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(uid != null, LessonPlan::getUserId, uid)
                .like(keyword != null && !keyword.isBlank(), LessonPlan::getTitle, keyword)
                .orderByDesc(LessonPlan::getId);
        Page<LessonPlan> result = lessonPlanMapper.selectPage(new Page<>(page, size), wrapper);
        return Result.ok(new PageResult<>(result.getTotal(), page, size, result.getRecords()));
    }

    @GetMapping("/public")
    public Result<List<LessonPlan>> publicList(@RequestParam(defaultValue = "20") int limit) {
        return Result.ok(lessonPlanMapper.selectList(
                new LambdaQueryWrapper<LessonPlan>().eq(LessonPlan::getIsPublic, 1)
                        .orderByDesc(LessonPlan::getId).last("limit " + limit)));
    }

    @GetMapping("/{id}")
    public Result<LessonPlan> get(@PathVariable Long id) {
        LessonPlan plan = lessonPlanMapper.selectById(id);
        if (plan == null) {
            throw new BusinessException(404, "教案不存在");
        }
        return Result.ok(plan);
    }

    @PutMapping("/{id}")
    public Result<LessonPlan> update(@PathVariable Long id, @RequestBody LessonPlan plan,
                                     HttpServletRequest request) {
        Object attr = request.getAttribute("userId");
        if (attr != null) {
            LessonPlan existing = lessonPlanMapper.selectById(id);
            if (existing == null) {
                throw new BusinessException(404, "教案不存在");
            }
            if (!existing.getUserId().equals(Long.valueOf(attr.toString()))) {
                throw new BusinessException(403, "无权修改他人的教案");
            }
        }
        plan.setId(id);
        plan.setUpdatedAt(LocalDateTime.now());
        lessonPlanMapper.updateById(plan);
        return Result.ok(lessonPlanMapper.selectById(id));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        Object attr = request.getAttribute("userId");
        LessonPlan existing = lessonPlanMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(404, "教案不存在");
        }
        if (attr != null && !existing.getUserId().equals(Long.valueOf(attr.toString()))) {
            throw new BusinessException(403, "无权删除他人的教案");
        }
        sectionMapper.delete(new LambdaQueryWrapper<LessonSection>().eq(LessonSection::getLessonPlanId, id));
        lessonPlanMapper.deleteById(id);
        return Result.ok();
    }

    @PostMapping("/{id}/duplicate")
    public Result<LessonPlan> duplicate(@PathVariable Long id, HttpServletRequest request) {
        Object attr = request.getAttribute("userId");
        LessonPlan source = lessonPlanMapper.selectById(id);
        if (source == null) {
            throw new BusinessException(404, "教案不存在");
        }
        if (attr != null && !source.getUserId().equals(Long.valueOf(attr.toString()))) {
            throw new BusinessException(403, "无权复制他人的教案");
        }
        LessonPlan copy = new LessonPlan();
        copy.setUserId(source.getUserId());
        copy.setTitle(source.getTitle() + "（副本）");
        copy.setSubject(source.getSubject());
        copy.setGrade(source.getGrade());
        copy.setTextbook(source.getTextbook());
        copy.setLessonType(source.getLessonType());
        copy.setContent(source.getContent());
        copy.setQualityScore(source.getQualityScore());
        copy.setIsPublic(0);
        copy.setStatus(1);
        lessonPlanMapper.insert(copy);

        List<LessonSection> sections = sectionMapper.selectList(
                new LambdaQueryWrapper<LessonSection>().eq(LessonSection::getLessonPlanId, id)
                        .orderByAsc(LessonSection::getSeq));
        for (LessonSection s : sections) {
            LessonSection ns = new LessonSection();
            ns.setLessonPlanId(copy.getId());
            ns.setSectionType(s.getSectionType());
            ns.setSeq(s.getSeq());
            ns.setContent(s.getContent());
            ns.setStandardRef(s.getStandardRef());
            sectionMapper.insert(ns);
        }
        return Result.ok(copy);
    }

    @GetMapping("/{id}/export")
    public ResponseEntity<String> export(@PathVariable Long id) throws Exception {
        LessonPlan plan = lessonPlanMapper.selectById(id);
        if (plan == null) {
            throw new BusinessException(404, "教案不存在");
        }
        String md = toMarkdown(plan);
        String filename = URLEncoder.encode((plan.getTitle() == null ? "教案" : plan.getTitle()) + ".md", StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
                .contentType(MediaType.TEXT_PLAIN)
                .body(md);
    }

    @GetMapping("/{id}/export-ppt")
    public ResponseEntity<byte[]> exportPpt(@PathVariable Long id) throws Exception {
        LessonPlan plan = lessonPlanMapper.selectById(id);
        if (plan == null) {
            throw new BusinessException(404, "教案不存在");
        }
        JsonNode root = objectMapper.readTree(plan.getContent());
        XMLSlideShow ppt = new XMLSlideShow();
        addTitleSlide(ppt, plan.getTitle(),
                (plan.getSubject() == null ? "" : plan.getSubject()) + " · "
                        + (plan.getGrade() == null ? "" : plan.getGrade()) + " · "
                        + (plan.getTextbook() == null ? "" : plan.getTextbook()));

        List<String> objectives = new ArrayList<>();
        for (JsonNode obj : root.path("objectives")) {
            objectives.add(obj.path("type").asText() + "：" + obj.path("content").asText());
        }
        addBulletSlide(ppt, "教学目标", objectives);

        List<String> points = new ArrayList<>();
        points.add("重点：" + String.join("；", toStrings(root.path("keyPoints"))));
        points.add("难点：" + String.join("；", toStrings(root.path("difficultPoints"))));
        addBulletSlide(ppt, "教学重难点", points);

        for (JsonNode p : root.path("process")) {
            addTextSlide(ppt, p.path("stage").asText() + "（" + p.path("duration").asText() + " 分钟）",
                    p.path("activities").asText());
        }
        addTextSlide(ppt, "板书设计", root.path("boardDesign").asText());

        List<String> homework = new ArrayList<>();
        for (JsonNode hw : root.path("homework")) {
            for (JsonNode item : hw.path("items")) {
                homework.add(hw.path("level").asText() + "：" + item.asText());
            }
        }
        addBulletSlide(ppt, "分层作业", homework);

        List<String> cards = new ArrayList<>();
        for (JsonNode card : root.path("standardCards")) {
            cards.add(card.path("ref").asText() + "：" + card.path("content").asText());
        }
        addBulletSlide(ppt, "课标依据", cards);
        addTextSlide(ppt, "AI 说明", "本教案由 AI 辅助生成，请教师审核后使用。");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ppt.write(out);
        ppt.close();
        String filename = URLEncoder.encode(
                (plan.getTitle() == null ? "教案" : plan.getTitle()) + ".pptx", StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.presentationml.presentation"))
                .body(out.toByteArray());
    }

    @GetMapping("/{id}/export-docx")
    public ResponseEntity<byte[]> exportDocx(@PathVariable Long id) throws Exception {
        LessonPlan plan = lessonPlanMapper.selectById(id);
        if (plan == null) {
            throw new BusinessException(404, "教案不存在");
        }
        JsonNode root = objectMapper.readTree(plan.getContent());
        XWPFDocument doc = new XWPFDocument();
        addDocTitle(doc, plan.getTitle(),
                (plan.getSubject() == null ? "" : plan.getSubject()) + " · "
                        + (plan.getGrade() == null ? "" : plan.getGrade()) + " · "
                        + (plan.getTextbook() == null ? "" : plan.getTextbook()));

        addDocHeading1(doc, "一、教学目标");
        for (JsonNode obj : root.path("objectives")) {
            String line = obj.path("type").asText() + "：" + obj.path("content").asText();
            String ref = obj.path("standardRef").asText();
            if (ref != null && !ref.isBlank()) {
                line += "（对应课标：" + ref + "）";
            }
            addDocBullet(doc, line);
        }

        addDocHeading1(doc, "二、教学重难点");
        addDocParagraph(doc, "重点：" + String.join("；", toStrings(root.path("keyPoints"))));
        addDocParagraph(doc, "难点：" + String.join("；", toStrings(root.path("difficultPoints"))));

        JsonNode prep = root.path("preparation");
        if (prep != null && !prep.isMissingNode() && !prep.asText().isBlank()) {
            addDocHeading1(doc, "三、教学准备");
            addDocParagraph(doc, prep.asText());
        }

        addDocHeading1(doc, "四、教学过程");
        for (JsonNode p : root.path("process")) {
            String stageTitle = p.path("stage").asText() + "（" + p.path("duration").asText() + "分钟）";
            addDocHeading2(doc, stageTitle);
            String activities = p.path("activities").asText();
            String teacher = p.path("teacher").asText();
            String student = p.path("student").asText();
            if (activities != null && !activities.isBlank()) {
                addDocParagraph(doc, activities);
            }
            if (teacher != null && !teacher.isBlank()) {
                addDocParagraph(doc, "教师活动：" + teacher);
            }
            if (student != null && !student.isBlank()) {
                addDocParagraph(doc, "学生活动：" + student);
            }
        }

        addDocHeading1(doc, "五、板书设计");
        addDocParagraph(doc, root.path("boardDesign").asText());

        addDocHeading1(doc, "六、分层作业");
        for (JsonNode hw : root.path("homework")) {
            addDocHeading2(doc, hw.path("level").asText());
            for (JsonNode item : hw.path("items")) {
                addDocBullet(doc, item.asText());
            }
        }

        addDocHeading1(doc, "七、课标依据");
        for (JsonNode card : root.path("standardCards")) {
            addDocBullet(doc, card.path("ref").asText() + "：" + card.path("content").asText());
        }

        setDocFooter(doc, "本教案由杏坛智备 AI 辅助生成，教师审核后使用");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        doc.write(out);
        doc.close();
        String filename = URLEncoder.encode(
                (plan.getTitle() == null ? "教案" : plan.getTitle()) + ".docx", StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .body(out.toByteArray());
    }

    private void addDocTitle(XWPFDocument doc, String title, String subtitle) {
        XWPFParagraph p = doc.createParagraph();
        p.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun r = p.createRun();
        r.setText(title == null ? "教案" : title);
        r.setBold(true);
        r.setFontSize(22);
        r.setFontFamily("宋体");
        XWPFParagraph p2 = doc.createParagraph();
        p2.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun r2 = p2.createRun();
        r2.setText(subtitle);
        r2.setFontSize(12);
        r2.setFontFamily("宋体");
    }

    private void addDocHeading1(XWPFDocument doc, String text) {
        XWPFParagraph p = doc.createParagraph();
        XWPFRun r = p.createRun();
        r.setText(text);
        r.setBold(true);
        r.setFontSize(16);
        r.setFontFamily("宋体");
    }

    private void addDocHeading2(XWPFDocument doc, String text) {
        XWPFParagraph p = doc.createParagraph();
        XWPFRun r = p.createRun();
        r.setText(text);
        r.setBold(true);
        r.setFontSize(14);
        r.setFontFamily("宋体");
    }

    private void addDocParagraph(XWPFDocument doc, String text) {
        XWPFParagraph p = doc.createParagraph();
        XWPFRun r = p.createRun();
        r.setText(text == null ? "" : text);
        r.setFontSize(12);
        r.setFontFamily("宋体");
    }

    private void addDocBullet(XWPFDocument doc, String text) {
        XWPFParagraph p = doc.createParagraph();
        XWPFRun r = p.createRun();
        r.setText("• " + (text == null ? "" : text));
        r.setFontSize(12);
        r.setFontFamily("宋体");
    }

    private void setDocFooter(XWPFDocument doc, String text) {
        XWPFFooter footer = doc.createFooter(HeaderFooterType.DEFAULT);
        XWPFParagraph p = footer.createParagraph();
        p.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun r = p.createRun();
        r.setText(text);
        r.setFontSize(10);
        r.setFontFamily("宋体");
    }

    private void addTitleSlide(XMLSlideShow ppt, String title, String subtitle) {
        XSLFSlide slide = ppt.createSlide();
        XSLFTextBox titleBox = slide.createTextBox();
        titleBox.setAnchor(new Rectangle(80, 200, 720, 100));
        XSLFTextParagraph p = titleBox.addNewTextParagraph();
        XSLFTextRun r = p.addNewTextRun();
        r.setText(title == null ? "教案" : title);
        r.setFontSize(38d);
        r.setBold(true);
        XSLFTextBox subBox = slide.createTextBox();
        subBox.setAnchor(new Rectangle(80, 320, 720, 60));
        XSLFTextParagraph p2 = subBox.addNewTextParagraph();
        XSLFTextRun r2 = p2.addNewTextRun();
        r2.setText(subtitle);
        r2.setFontSize(20d);
    }

    private void addBulletSlide(XMLSlideShow ppt, String title, List<String> lines) {
        XSLFSlide slide = ppt.createSlide();
        XSLFTextBox titleBox = slide.createTextBox();
        titleBox.setAnchor(new Rectangle(80, 60, 720, 60));
        XSLFTextParagraph tp = titleBox.addNewTextParagraph();
        XSLFTextRun tr = tp.addNewTextRun();
        tr.setText(title);
        tr.setFontSize(28d);
        tr.setBold(true);
        XSLFTextBox content = slide.createTextBox();
        content.setAnchor(new Rectangle(80, 150, 720, 440));
        if (lines.isEmpty()) {
            lines = new ArrayList<>();
            lines.add("（无内容）");
        }
        for (String line : lines) {
            XSLFTextParagraph cp = content.addNewTextParagraph();
            XSLFTextRun cr = cp.addNewTextRun();
            cr.setText("• " + line);
            cr.setFontSize(18d);
        }
    }

    private void addTextSlide(XMLSlideShow ppt, String title, String text) {
        XSLFSlide slide = ppt.createSlide();
        XSLFTextBox titleBox = slide.createTextBox();
        titleBox.setAnchor(new Rectangle(80, 60, 720, 60));
        XSLFTextParagraph tp = titleBox.addNewTextParagraph();
        XSLFTextRun tr = tp.addNewTextRun();
        tr.setText(title);
        tr.setFontSize(28d);
        tr.setBold(true);
        XSLFTextBox content = slide.createTextBox();
        content.setAnchor(new Rectangle(80, 150, 720, 440));
        XSLFTextParagraph cp = content.addNewTextParagraph();
        XSLFTextRun cr = cp.addNewTextRun();
        cr.setText(text == null ? "" : text);
        cr.setFontSize(18d);
    }

    private String toMarkdown(LessonPlan plan) throws Exception {
        StringBuilder md = new StringBuilder();
        md.append("# ").append(plan.getTitle()).append("\n\n");
        md.append("> ").append(plan.getSubject()).append(" · ").append(plan.getGrade())
                .append(" · ").append(plan.getTextbook()).append("\n\n");
        JsonNode root = objectMapper.readTree(plan.getContent());
        md.append("## 教学目标\n\n");
        for (JsonNode obj : root.path("objectives")) {
            md.append("- **").append(obj.path("type").asText()).append("**：")
                    .append(obj.path("content").asText()).append("\n");
        }
        md.append("\n## 教学重难点\n\n");
        md.append("- 重点：").append(String.join("；", toStrings(root.path("keyPoints")))).append("\n");
        md.append("- 难点：").append(String.join("；", toStrings(root.path("difficultPoints")))).append("\n");
        md.append("\n## 教学过程\n\n");
        for (JsonNode p : root.path("process")) {
            md.append("### ").append(p.path("stage").asText()).append("（")
                    .append(p.path("duration").asText()).append("分钟）\n\n");
            md.append(p.path("activities").asText()).append("\n\n");
        }
        md.append("## 板书设计\n\n").append(root.path("boardDesign").asText()).append("\n\n");
        md.append("## 分层作业\n\n");
        for (JsonNode hw : root.path("homework")) {
            md.append("**").append(hw.path("level").asText()).append("**\n");
            for (JsonNode item : hw.path("items")) {
                md.append("- ").append(item.asText()).append("\n");
            }
            md.append("\n");
        }
        md.append("## 课标依据\n\n");
        for (JsonNode card : root.path("standardCards")) {
            md.append("- ").append(card.path("ref").asText()).append("：")
                    .append(card.path("content").asText()).append("\n");
        }
        md.append("\n---\n\n> 本教案由 AI 辅助生成，请教师审核后使用。\n");
        return md.toString();
    }

    private List<String> toStrings(JsonNode array) {
        List<String> out = new ArrayList<>();
        for (JsonNode n : array) {
            out.add(n.asText());
        }
        return out;
    }
}

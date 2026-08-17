package com.xingtan.ai.ppt;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextBox;
import org.apache.poi.xslf.usermodel.XSLFTextParagraph;
import org.apache.poi.xslf.usermodel.XSLFTextRun;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 品牌化课件渲染器：将 AI 生成的逐页方案渲染为精美 PPTX
 */
@Component
public class PptDesigner {

    private static final Color GREEN = new Color(0x2E, 0x6B, 0x4F);
    private static final Color GREEN_DARK = new Color(0x24, 0x4C, 0x38);
    private static final Color GOLD = new Color(0xE3, 0xA0, 0x3C);
    private static final Color CREAM = new Color(0xF7, 0xF4, 0xEC);
    private static final Color INK = new Color(0x2C, 0x2C, 0x2C);
    private static final Color GRAY = new Color(0x8A, 0x8A, 0x8A);
    private static final int W = 960;
    private static final int H = 540;

    public byte[] build(JsonNode deck, String defaultTitle, String subtitle) throws Exception {
        XMLSlideShow ppt = new XMLSlideShow();
        ppt.setPageSize(new Dimension(W, H));
        int page = 0;
        if (deck != null && deck.isArray() && deck.size() > 0) {
            for (JsonNode node : deck) {
                page++;
                renderSlide(ppt, node, defaultTitle, subtitle, page);
            }
        }
        if (page == 0) {
            buildFallback(ppt, defaultTitle, subtitle);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ppt.write(out);
        ppt.close();
        return out.toByteArray();
    }

    private void renderSlide(XMLSlideShow ppt, JsonNode node, String defaultTitle, String subtitle, int page) {
        String type = node.path("type").asText("content");
        String title = node.path("title").asText("");
        String sub = node.path("subtitle").asText("");
        List<String[]> bullets = new ArrayList<>();
        for (JsonNode b : node.path("bullets")) {
            bullets.add(new String[]{b.path("text").asText(), String.valueOf(b.path("level").asInt(0))});
        }
        String notes = node.path("notes").asText("");
        String visual = node.path("visual").asText("");

        switch (type) {
            case "cover":
                buildCover(ppt, title == null || title.isBlank() ? defaultTitle : title, subtitle, page);
                break;
            case "ending":
                buildEnding(ppt, title, page);
                break;
            case "activity":
                buildContentSlide(ppt, "课堂活动 · " + title, bullets, GOLD, GREEN_DARK, notes, visual, page);
                break;
            case "summary":
                buildContentSlide(ppt, "课堂小结 · " + title, bullets, GREEN, CREAM, notes, visual, page);
                break;
            case "agenda":
                buildContentSlide(ppt, "本课导航 · " + title, bullets, GREEN, CREAM, notes, visual, page);
                break;
            default:
                buildContentSlide(ppt, title, bullets, GREEN, CREAM, notes, visual, page);
                break;
        }
        if (sub != null && !sub.isBlank()) {
            XSLFSlide last = ppt.getSlides().get(ppt.getSlides().size() - 1);
            XSLFTextBox subBox = textBox(last, 60, 118, 840, 30);
            styleRun(addLine(subBox), sub, 14, false, GRAY);
        }
    }

    private void buildCover(XMLSlideShow ppt, String title, String subtitle, int page) {
        XSLFSlide slide = ppt.createSlide();
        fill(slide, GREEN);
        XSLFTextBox titleBox = textBox(slide, 80, 170, 800, 130);
        styleRun(addLine(titleBox), title, 46, true, Color.WHITE);
        XSLFTextBox line = textBox(slide, 80, 320, 220, 10);
        fillText(line, GOLD);
        XSLFTextBox sub = textBox(slide, 80, 350, 800, 50);
        styleRun(addLine(sub), subtitle, 22, false, CREAM);
        footer(slide, page, "AI 美化课件 · 杏坛智备");
    }

    private void buildEnding(XMLSlideShow ppt, String title, int page) {
        XSLFSlide slide = ppt.createSlide();
        fill(slide, GREEN_DARK);
        XSLFTextBox box = textBox(slide, 80, 200, 800, 130);
        styleRun(addLine(box), title == null || title.isBlank() ? "谢谢聆听 · 请批评指正" : title,
                44, true, Color.WHITE);
        XSLFTextBox sub = textBox(slide, 80, 340, 800, 40);
        styleRun(addLine(sub), "本课件由「杏坛智备」AI 生成，请教师审核后使用", 18, false, CREAM);
        footer(slide, page, "杏坛智备");
    }

    private void buildContentSlide(XMLSlideShow ppt, String title, List<String[]> bullets,
                                   Color band, Color bg, String notes, String visual, int page) {
        XSLFSlide slide = ppt.createSlide();
        fill(slide, bg);
        XSLFTextBox bandBox = textBox(slide, 0, 0, W, 110);
        fillText(bandBox, band);
        XSLFTextBox titleBox = textBox(slide, 60, 30, 840, 55);
        styleRun(addLine(titleBox), title, 30, true, Color.WHITE);
        XSLFTextBox line = textBox(slide, 60, 112, 140, 7);
        fillText(line, GOLD);

        XSLFTextBox body = textBox(slide, 60, 145, 840, 300);
        if (bullets.isEmpty()) {
            styleRun(addLine(body), "（无要点）", 18, false, GRAY);
        } else {
            for (String[] b : bullets) {
                XSLFTextParagraph p = body.addNewTextParagraph();
                int level = Integer.parseInt(b[1]);
                p.setLeftMargin((double) (level * 30));
                String prefix = level == 0 ? "•  " : "–  ";
                XSLFTextRun r = p.addNewTextRun();
                r.setText(prefix + b[0]);
                r.setFontSize((double) (20 - Math.min(level, 1) * 2));
                r.setBold(level == 0);
                r.setFontColor(INK);
                p.setSpaceAfter(12.0);
            }
        }
        if (notes != null && !notes.isBlank()) {
            XSLFTextBox noteBox = textBox(slide, 60, 462, 840, 30);
            styleRun(addLine(noteBox), "讲稿提示：" + notes, 11, false, GRAY);
        } else if (visual != null && !visual.isBlank()) {
            XSLFTextBox vis = textBox(slide, 60, 462, 840, 26);
            styleRun(addLine(vis), "配图建议：" + visual, 12, false, GRAY);
        }
        footer(slide, page, "AI 美化课件 · 杏坛智备");
    }

    private void buildFallback(XMLSlideShow ppt, String title, String subtitle) {
        buildCover(ppt, title, subtitle, 1);
        List<String[]> points = new ArrayList<>();
        points.add(new String[]{"本课件由教案/课件内容自动生成", "0"});
        points.add(new String[]{"建议结合课堂实际补充活动与练习", "0"});
        buildContentSlide(ppt, "内容要点", points, GREEN, CREAM, "", "", 2);
        buildEnding(ppt, "谢谢聆听 · 请批评指正", 3);
    }

    private XSLFTextBox textBox(XSLFSlide slide, int x, int y, int w, int h) {
        XSLFTextBox box = slide.createTextBox();
        box.setAnchor(new Rectangle(x, y, w, h));
        box.setWordWrap(true);
        return box;
    }

    private XSLFTextParagraph addLine(XSLFTextBox box) {
        return box.addNewTextParagraph();
    }

    private void styleRun(XSLFTextParagraph p, String text, double size, boolean bold, Color color) {
        XSLFTextRun r = p.addNewTextRun();
        r.setText(text == null ? "" : text);
        r.setFontSize(size);
        r.setBold(bold);
        r.setFontColor(color);
    }

    private void fill(XSLFSlide slide, Color color) {
        XSLFTextBox bg = textBox(slide, 0, 0, W, H);
        fillText(bg, color);
    }

    private void fillText(XSLFTextBox box, Color color) {
        box.setFillColor(color);
        box.setLineColor(color);
        box.setLineWidth(0);
    }

    private void footer(XSLFSlide slide, int page, String brand) {
        XSLFTextBox left = textBox(slide, 60, 505, 600, 24);
        styleRun(addLine(left), brand + " · 第 " + page + " 页", 12, false, GRAY);
        XSLFTextBox right = textBox(slide, 700, 505, 200, 24);
        styleRun(addLine(right), "本课件由 AI 生成，请教师审核后使用", 12, false, GRAY);
    }
}

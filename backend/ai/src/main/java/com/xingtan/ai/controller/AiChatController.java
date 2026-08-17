package com.xingtan.ai.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xingtan.ai.gateway.LlmRouter;
import com.xingtan.common.exception.BusinessException;
import com.xingtan.common.result.Result;
import com.xingtan.kb.entity.KbChunk;
import com.xingtan.kb.entity.KbDocument;
import com.xingtan.kb.mapper.KbChunkMapper;
import com.xingtan.kb.mapper.KbDocumentMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * AI 对话接口（教案追问/修改建议）
 */
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiChatController {

    private final LlmRouter llmRouter;
    private final KbDocumentMapper documentMapper;
    private final KbChunkMapper chunkMapper;

    @PostMapping("/chat")
    public Result<String> chat(@RequestBody Map<String, Object> request) {
        String message = request.get("message") == null ? "" : request.get("message").toString();
        String system = "你是乡村教师的 AI 备课助手。回答要简洁、实用、可操作，"
                + "符合义务教育课程标准理念，必要时给出具体教学建议或示例。";
        return Result.ok(llmRouter.complete(null, system, message));
    }

    @PostMapping("/analyze-courseware")
    public Result<String> analyzeCourseware(@RequestBody Map<String, Object> request,
                                            HttpServletRequest httpRequest) {
        Long docId = Long.valueOf(request.get("docId").toString());
        KbDocument doc = documentMapper.selectById(docId);
        if (doc == null) {
            throw new BusinessException(404, "课件不存在");
        }
        Object attr = httpRequest.getAttribute("userId");
        if (attr != null && doc.getUserId() != null
                && !doc.getUserId().equals(Long.valueOf(attr.toString()))) {
            throw new BusinessException(403, "无权分析他人的课件");
        }
        List<KbChunk> chunks = chunkMapper.selectList(
                new LambdaQueryWrapper<KbChunk>().eq(KbChunk::getDocId, docId)
                        .orderByAsc(KbChunk::getSeq));
        String content = chunks.stream().map(KbChunk::getContent)
                .collect(Collectors.joining("\n"));
        if (content.length() > 6000) {
            content = content.substring(0, 6000);
        }
        String system = "你是资深中小学教研专家，请基于教师上传的课件内容，"
                + "从教师备课与制作PPT的角度输出结构化分析。";
        String user = "课件标题：" + doc.getTitle() + "\n\n【课件内容】\n" + content
                + "\n\n请输出：\n1) 本课核心内容与教学目标；\n2) 教学重难点；\n"
                + "3) 教案设计建议（含教学过程环节与时间分配）；\n"
                + "4) PPT 制作大纲（8~12页，逐页给出标题与要点）。\n"
                + "请用清晰的编号与小标题组织。";
        return Result.ok(llmRouter.complete(null, system, user));
    }
}

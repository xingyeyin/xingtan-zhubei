package com.xingtan.stats.controller;

import com.xingtan.common.result.Result;
import com.xingtan.stats.entity.Feedback;
import com.xingtan.stats.mapper.FeedbackMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * 用户反馈接口
 */
@RestController
@RequestMapping("/api/feedbacks")
@RequiredArgsConstructor
public class FeedbackController {

    private final FeedbackMapper feedbackMapper;

    @PostMapping
    public Result<Feedback> create(@RequestBody Feedback feedback, HttpServletRequest request) {
        Long userId = Long.valueOf(request.getAttribute("userId").toString());
        feedback.setUserId(userId);
        feedback.setCreatedAt(LocalDateTime.now());
        feedbackMapper.insert(feedback);
        return Result.ok(feedback);
    }
}

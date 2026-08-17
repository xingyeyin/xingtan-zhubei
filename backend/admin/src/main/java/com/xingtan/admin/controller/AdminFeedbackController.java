package com.xingtan.admin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xingtan.common.result.Result;
import com.xingtan.stats.entity.Feedback;
import com.xingtan.stats.mapper.FeedbackMapper;
import com.xingtan.system.entity.SysUser;
import com.xingtan.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 管理端：用户反馈
 */
@RestController
@RequestMapping("/api/admin/feedbacks")
@RequiredArgsConstructor
public class AdminFeedbackController {

    private final FeedbackMapper feedbackMapper;
    private final SysUserMapper userMapper;

    @GetMapping
    public Result<Map<String, Object>> list(@RequestParam(defaultValue = "50") int limit) {
        List<Feedback> list = feedbackMapper.selectList(
                new LambdaQueryWrapper<Feedback>().orderByDesc(Feedback::getId).last("limit " + limit));
        Map<Long, String> names = userMapper.selectList(null).stream()
                .collect(Collectors.toMap(SysUser::getId, SysUser::getNickname));
        List<Map<String, Object>> records = list.stream().map(f -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", f.getId());
            m.put("userId", f.getUserId());
            m.put("nickname", names.getOrDefault(f.getUserId(), "匿名"));
            m.put("lessonPlanId", f.getLessonPlanId());
            m.put("type", f.getType());
            m.put("content", f.getContent());
            m.put("createdAt", f.getCreatedAt());
            return m;
        }).collect(Collectors.toList());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("records", records);
        data.put("total", list.size());
        return Result.ok(data);
    }
}

package com.xingtan.stats.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xingtan.common.result.Result;
import com.xingtan.lesson.entity.LessonPlan;
import com.xingtan.kb.mapper.KbDocumentMapper;
import com.xingtan.lesson.mapper.LessonPlanMapper;
import com.xingtan.stats.entity.ContentReviewLog;
import com.xingtan.stats.entity.Feedback;
import com.xingtan.stats.entity.UsageLog;
import com.xingtan.stats.mapper.ContentReviewLogMapper;
import com.xingtan.stats.mapper.FeedbackMapper;
import com.xingtan.stats.mapper.UsageLogMapper;
import com.xingtan.system.entity.School;
import com.xingtan.system.entity.SysUser;
import com.xingtan.system.mapper.SchoolMapper;
import com.xingtan.system.mapper.SysUserMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 统计接口
 * TODO(骨架阶段): 聚合今日/本周/本月指标、趋势、区域分布
 */
@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
public class StatsController {

    private final UsageLogMapper usageLogMapper;
    private final SysUserMapper userMapper;
    private final LessonPlanMapper lessonPlanMapper;
    private final KbDocumentMapper documentMapper;
    private final ContentReviewLogMapper reviewLogMapper;
    private final FeedbackMapper feedbackMapper;
    private final SchoolMapper schoolMapper;

    @GetMapping("/overview")
    public Result<Map<String, Object>> overview() {
        Long teachers = userMapper.selectCount(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getRole, "TEACHER"));
        Long generations = usageLogMapper.selectCount(
                new LambdaQueryWrapper<UsageLog>().eq(UsageLog::getAction, "GENERATE"));
        Long docs = documentMapper.selectCount(null);
        List<LessonPlan> plans = lessonPlanMapper.selectList(null);
        double avgQuality = plans.stream().mapToInt(p -> p.getQualityScore() == null ? 0 : p.getQualityScore())
                .average().orElse(0.0);

        Long feedbackCount = feedbackMapper.selectCount(null);
        Long usefulCount = feedbackMapper.selectCount(
                new LambdaQueryWrapper<Feedback>().eq(Feedback::getType, "USEFUL"));
        double goodRate = feedbackCount == 0 ? 0.0 :
                Math.round(usefulCount.doubleValue() / feedbackCount.doubleValue() * 1000) / 10.0;

        Long publicLessons = lessonPlanMapper.selectCount(
                new LambdaQueryWrapper<LessonPlan>().eq(LessonPlan::getIsPublic, 1));
        Long schoolCount = schoolMapper.selectCount(
                new LambdaQueryWrapper<School>().eq(School::getStatus, 1));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("totalTeachers", teachers);
        data.put("totalGenerations", generations);
        data.put("totalDocs", docs);
        data.put("savedHours", Math.round(generations * 1.5 * 10) / 10.0);
        data.put("avgQuality", Math.round(avgQuality * 10) / 10.0);
        data.put("goodRate", goodRate);
        data.put("feedbackCount", feedbackCount);
        data.put("publicLessons", publicLessons);
        data.put("schoolCount", schoolCount);
        return Result.ok(data);
    }

    @GetMapping("/trend")
    public Result<List<Map<String, Object>>> trend(@RequestParam(defaultValue = "7") int days) {
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        return Result.ok(usageLogMapper.trendByDay(since));
    }

    @GetMapping("/distribution")
    public Result<Map<String, Object>> distribution() {
        List<LessonPlan> plans = lessonPlanMapper.selectList(null);
        Map<String, Long> count = plans.stream()
                .collect(Collectors.groupingBy(
                        p -> p.getSubject() == null ? "未分类" : p.getSubject(), Collectors.counting()));
        Map<String, Object> data = new LinkedHashMap<>(count);
        return Result.ok(data);
    }

    @GetMapping("/recent")
    public Result<List<Map<String, Object>>> recent(@RequestParam(defaultValue = "10") int limit) {
        List<Map<String, Object>> logs = usageLogMapper.recent(limit);
        Map<Long, String> names = userMapper.selectList(null).stream()
                .collect(Collectors.toMap(SysUser::getId, SysUser::getNickname));
        for (Map<String, Object> log : logs) {
            Object uid = log.get("user_id");
            log.put("nickname", uid == null ? "匿名" : names.getOrDefault(Long.valueOf(uid.toString()), "匿名"));
        }
        return Result.ok(logs);
    }

    @GetMapping("/reviews")
    public Result<Map<String, Object>> reviews() {
        List<ContentReviewLog> logs = reviewLogMapper.selectList(
                new LambdaQueryWrapper<ContentReviewLog>().orderByDesc(ContentReviewLog::getId));
        long pass = logs.stream().filter(l -> "PASS".equals(l.getResult())).count();
        long blocked = logs.size() - pass;
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("pending", 0);
        data.put("pass", pass);
        data.put("blocked", blocked);
        data.put("records", logs);
        return Result.ok(data);
    }

    @GetMapping("/my")
    public Result<Map<String, Object>> my(HttpServletRequest request) {
        Long userId = Long.valueOf(request.getAttribute("userId").toString());
        Long lessonCount = lessonPlanMapper.selectCount(
                new LambdaQueryWrapper<LessonPlan>().eq(LessonPlan::getUserId, userId));
        Long generations = usageLogMapper.selectCount(
                new LambdaQueryWrapper<UsageLog>().eq(UsageLog::getUserId, userId)
                        .eq(UsageLog::getAction, "GENERATE"));
        List<LessonPlan> plans = lessonPlanMapper.selectList(
                new LambdaQueryWrapper<LessonPlan>().eq(LessonPlan::getUserId, userId)
                        .orderByDesc(LessonPlan::getId).last("limit 5"));
        double avg = plans.stream().mapToInt(p -> p.getQualityScore() == null ? 0 : p.getQualityScore())
                .average().orElse(0.0);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("lessonCount", lessonCount);
        data.put("totalGenerations", generations);
        data.put("savedHours", Math.round(generations * 1.5 * 10) / 10.0);
        data.put("avgQuality", Math.round(avg * 10) / 10.0);
        data.put("recentLessons", plans);
        return Result.ok(data);
    }
}

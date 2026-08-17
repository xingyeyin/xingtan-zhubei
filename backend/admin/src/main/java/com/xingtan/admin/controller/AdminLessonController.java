package com.xingtan.admin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xingtan.common.exception.BusinessException;
import com.xingtan.common.result.PageResult;
import com.xingtan.common.result.Result;
import com.xingtan.lesson.entity.LessonPlan;
import com.xingtan.lesson.entity.LessonSection;
import com.xingtan.lesson.mapper.LessonPlanMapper;
import com.xingtan.lesson.mapper.LessonSectionMapper;
import com.xingtan.system.entity.School;
import com.xingtan.system.entity.SysUser;
import com.xingtan.system.mapper.SchoolMapper;
import com.xingtan.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 管理端：教案总览
 */
@RestController
@RequestMapping("/api/admin/lessons")
@RequiredArgsConstructor
public class AdminLessonController {

    private final LessonPlanMapper lessonPlanMapper;
    private final LessonSectionMapper sectionMapper;
    private final SysUserMapper userMapper;
    private final SchoolMapper schoolMapper;

    @GetMapping
    public Result<PageResult<Map<String, Object>>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String keyword) {
        LambdaQueryWrapper<LessonPlan> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(keyword != null && !keyword.isBlank(), LessonPlan::getTitle, keyword)
                .orderByDesc(LessonPlan::getId);
        Page<LessonPlan> result = lessonPlanMapper.selectPage(new Page<>(page, size), wrapper);
        Map<Long, SysUser> users = userMapper.selectList(null).stream()
                .collect(Collectors.toMap(SysUser::getId, u -> u));
        Map<Long, String> schools = schoolMapper.selectList(null).stream()
                .collect(Collectors.toMap(School::getId, School::getName));
        List<Map<String, Object>> records = result.getRecords().stream().map(p -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.getId());
            m.put("title", p.getTitle());
            m.put("subject", p.getSubject());
            m.put("grade", p.getGrade());
            m.put("qualityScore", p.getQualityScore());
            m.put("isPublic", p.getIsPublic());
            m.put("createdAt", p.getCreatedAt());
            SysUser u = users.get(p.getUserId());
            m.put("teacher", u == null ? "未知" : u.getNickname());
            m.put("school", u == null || u.getSchoolId() == null ? "-" : schools.get(u.getSchoolId()));
            return m;
        }).collect(Collectors.toList());
        return Result.ok(new PageResult<>(result.getTotal(), page, size, records));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        LessonPlan plan = lessonPlanMapper.selectById(id);
        if (plan == null) {
            throw new BusinessException(404, "教案不存在");
        }
        sectionMapper.delete(new LambdaQueryWrapper<LessonSection>().eq(LessonSection::getLessonPlanId, id));
        lessonPlanMapper.deleteById(id);
        return Result.ok();
    }
}

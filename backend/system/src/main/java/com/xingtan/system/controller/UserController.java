package com.xingtan.system.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xingtan.common.result.PageResult;
import com.xingtan.common.result.Result;
import com.xingtan.lesson.entity.LessonPlan;
import com.xingtan.lesson.mapper.LessonPlanMapper;
import com.xingtan.system.entity.School;
import com.xingtan.system.entity.SysUser;
import com.xingtan.system.mapper.SchoolMapper;
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
 * 用户管理接口（管理端）
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final SysUserMapper userMapper;
    private final SchoolMapper schoolMapper;
    private final LessonPlanMapper lessonPlanMapper;

    @GetMapping
    public Result<PageResult<Map<String, Object>>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String role) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        boolean hasKeyword = keyword != null && !keyword.isBlank();
        wrapper.and(hasKeyword, q -> q.like(SysUser::getNickname, keyword).or().like(SysUser::getPhone, keyword))
                .eq(role != null && !role.isBlank(), SysUser::getRole, role)
                .orderByDesc(SysUser::getId);
        Page<SysUser> result = userMapper.selectPage(new Page<>(page, size), wrapper);

        Map<Long, String> schoolNames = schoolMapper.selectList(null).stream()
                .collect(Collectors.toMap(School::getId, School::getName));
        Map<Long, Long> lessonCounts = lessonPlanMapper.selectList(null).stream()
                .collect(Collectors.groupingBy(LessonPlan::getUserId, Collectors.counting()));
        List<Map<String, Object>> records = result.getRecords().stream().map(u -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", u.getId());
            m.put("phone", u.getPhone());
            m.put("nickname", u.getNickname());
            m.put("role", u.getRole());
            m.put("status", u.getStatus());
            m.put("school", schoolNames.get(u.getSchoolId()));
            m.put("lessonCount", lessonCounts.getOrDefault(u.getId(), 0L));
            m.put("createdAt", u.getCreatedAt());
            return m;
        }).collect(Collectors.toList());
        return Result.ok(new PageResult<>(result.getTotal(), page, size, records));
    }
}

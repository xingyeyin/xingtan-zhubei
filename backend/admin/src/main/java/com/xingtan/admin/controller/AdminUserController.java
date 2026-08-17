package com.xingtan.admin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xingtan.common.exception.BusinessException;
import com.xingtan.common.result.Result;
import com.xingtan.lesson.entity.LessonPlan;
import com.xingtan.lesson.mapper.LessonPlanMapper;
import com.xingtan.stats.entity.UsageLog;
import com.xingtan.stats.mapper.UsageLogMapper;
import com.xingtan.system.entity.School;
import com.xingtan.system.entity.SysUser;
import com.xingtan.system.entity.TeacherProfile;
import com.xingtan.system.mapper.SchoolMapper;
import com.xingtan.system.mapper.SysUserMapper;
import com.xingtan.system.mapper.TeacherProfileMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理端：用户详情与状态管理
 */
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final SysUserMapper userMapper;
    private final SchoolMapper schoolMapper;
    private final TeacherProfileMapper profileMapper;
    private final LessonPlanMapper lessonPlanMapper;
    private final UsageLogMapper usageLogMapper;

    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        School school = user.getSchoolId() == null ? null : schoolMapper.selectById(user.getSchoolId());
        TeacherProfile profile = profileMapper.selectOne(
                new LambdaQueryWrapper<TeacherProfile>().eq(TeacherProfile::getUserId, id).last("limit 1"));
        Long lessonCount = lessonPlanMapper.selectCount(
                new LambdaQueryWrapper<LessonPlan>().eq(LessonPlan::getUserId, id));
        Long usageCount = usageLogMapper.selectCount(
                new LambdaQueryWrapper<UsageLog>().eq(UsageLog::getUserId, id));
        List<Map<String, Object>> recent = usageLogMapper.recentByUser(id, 5);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", user.getId());
        data.put("phone", user.getPhone());
        data.put("nickname", user.getNickname());
        data.put("role", user.getRole());
        data.put("status", user.getStatus());
        data.put("createdAt", user.getCreatedAt());
        data.put("school", school == null ? null : school.getName());
        data.put("subjects", profile == null ? null : profile.getSubjects());
        data.put("grades", profile == null ? null : profile.getGrades());
        data.put("lessonCount", lessonCount);
        data.put("usageCount", usageCount);
        data.put("recentLogs", recent);
        List<LessonPlan> lessons = lessonPlanMapper.selectList(
                new LambdaQueryWrapper<LessonPlan>().eq(LessonPlan::getUserId, id)
                        .orderByDesc(LessonPlan::getId).last("limit 10"));
        data.put("lessons", lessons);
        return Result.ok(data);
    }

    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        SysUser update = new SysUser();
        update.setId(id);
        update.setStatus(body.get("status") == null ? 0 : Integer.valueOf(body.get("status").toString()));
        userMapper.updateById(update);
        return Result.ok();
    }
}

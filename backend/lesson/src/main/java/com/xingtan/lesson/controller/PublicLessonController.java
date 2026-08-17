package com.xingtan.lesson.controller;

import com.xingtan.common.exception.BusinessException;
import com.xingtan.common.result.Result;
import com.xingtan.lesson.entity.LessonPlan;
import com.xingtan.lesson.mapper.LessonPlanMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 公开教案查看（无需登录，用于分享链接）
 */
@RestController
@RequestMapping("/api/public/lessons")
@RequiredArgsConstructor
public class PublicLessonController {

    private final LessonPlanMapper lessonPlanMapper;

    @GetMapping("/{id}")
    public Result<LessonPlan> get(@PathVariable Long id) {
        LessonPlan plan = lessonPlanMapper.selectById(id);
        if (plan == null || plan.getIsPublic() == null || plan.getIsPublic() != 1) {
            throw new BusinessException(404, "教案不存在或未公开");
        }
        return Result.ok(plan);
    }
}

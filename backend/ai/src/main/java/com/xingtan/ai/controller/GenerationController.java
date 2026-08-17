package com.xingtan.ai.controller;

import com.xingtan.ai.entity.GenerationTask;
import com.xingtan.ai.service.GenerationService;
import com.xingtan.common.result.Result;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 生成任务接口
 */
@RestController
@RequestMapping("/api/generations")
@RequiredArgsConstructor
public class GenerationController {

    private final GenerationService generationService;

    @PostMapping
    public Result<GenerationTask> create(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        Object attr = request.getAttribute("userId");
        Long userId = attr != null ? Long.valueOf(attr.toString())
                : body.get("userId") == null ? 1L : Long.valueOf(body.get("userId").toString());
        String scene = body.get("scene") == null ? null : body.get("scene").toString();
        @SuppressWarnings("unchecked")
        Map<String, Object> params = body.get("params") == null ? Map.of()
                : (Map<String, Object>) body.get("params");
        GenerationTask task = generationService.create(userId, scene, params);
        generationService.submitAsync(task.getId());
        return Result.ok(task);
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> get(@PathVariable Long id) {
        return Result.ok(generationService.detail(id));
    }
}

package com.xingtan.system.controller;

import com.xingtan.common.result.Result;
import com.xingtan.system.dto.RegisterRequest;
import com.xingtan.system.dto.ChangePasswordRequest;
import com.xingtan.system.dto.ProfileUpdateRequest;
import com.xingtan.system.dto.LoginRequest;
import com.xingtan.system.dto.LoginVO;
import com.xingtan.system.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 认证接口
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public Result<LoginVO> login(@RequestBody LoginRequest request) {
        return Result.ok(authService.login(request));
    }

    @PostMapping("/register")
    public Result<LoginVO> register(@RequestBody RegisterRequest request) {
        return Result.ok(authService.register(request));
    }

    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        String token = authorization == null ? null
                : authorization.startsWith("Bearer ") ? authorization.substring(7) : null;
        authService.logout(token);
        return Result.ok();
    }

    @GetMapping("/profile")
    public Result<Map<String, Object>> profile(HttpServletRequest request) {
        Long userId = Long.valueOf(request.getAttribute("userId").toString());
        return Result.ok(authService.profile(userId));
    }

    @PutMapping("/profile")
    public Result<Map<String, Object>> updateProfile(HttpServletRequest request,
                                                     @RequestBody ProfileUpdateRequest body) {
        Long userId = Long.valueOf(request.getAttribute("userId").toString());
        return Result.ok(authService.updateProfile(userId, body));
    }

    @PostMapping("/password")
    public Result<Void> changePassword(HttpServletRequest request,
                                       @RequestBody ChangePasswordRequest body) {
        Long userId = Long.valueOf(request.getAttribute("userId").toString());
        authService.changePassword(userId, body);
        return Result.ok();
    }
}

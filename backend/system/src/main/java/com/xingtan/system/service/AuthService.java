package com.xingtan.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xingtan.common.exception.BusinessException;
import com.xingtan.system.dto.LoginRequest;
import com.xingtan.system.dto.LoginVO;
import com.xingtan.system.dto.ChangePasswordRequest;
import com.xingtan.system.dto.ProfileUpdateRequest;
import com.xingtan.system.dto.RegisterRequest;
import com.xingtan.system.entity.AuthToken;
import com.xingtan.system.entity.School;
import com.xingtan.system.entity.TeacherProfile;
import com.xingtan.system.entity.SysUser;
import com.xingtan.system.mapper.AuthTokenMapper;
import com.xingtan.system.mapper.SchoolMapper;
import com.xingtan.system.mapper.TeacherProfileMapper;
import com.xingtan.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 认证服务
 * 注册 / 登录 / 令牌签发 / 个人信息
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final SysUserMapper userMapper;
    private final SchoolMapper schoolMapper;
    private final TeacherProfileMapper profileMapper;
    private final AuthTokenMapper tokenMapper;
    private final PasswordEncoder passwordEncoder;

    public LoginVO login(LoginRequest request) {
        SysUser user = userMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getPhone, request.getPhone()));
        if (user == null || !passwordEncoder.matches(
                request.getPassword() == null ? "" : request.getPassword(), user.getPassword())) {
            throw new BusinessException(401, "账号或密码错误");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BusinessException(403, "账号已被禁用");
        }
        return issueToken(user);
    }

    public LoginVO register(RegisterRequest request) {
        if (request.getPhone() == null || !request.getPhone().matches("1\\d{10}")) {
            throw new BusinessException(400, "请输入正确的 11 位手机号");
        }
        if (request.getPassword() == null || request.getPassword().length() < 6) {
            throw new BusinessException(400, "密码至少 6 位");
        }
        Long exists = userMapper.selectCount(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getPhone, request.getPhone()));
        if (exists != null && exists > 0) {
            throw new BusinessException(400, "该手机号已注册，请直接登录");
        }
        SysUser user = new SysUser();
        user.setPhone(request.getPhone());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setNickname(request.getNickname() == null || request.getNickname().isBlank()
                ? "教师" + request.getPhone().substring(7) : request.getNickname());
        user.setRole("TEACHER");
        user.setSchoolId(resolveSchool(request.getSchoolName()));
        user.setStatus(1);
        userMapper.insert(user);

        TeacherProfile profile = new TeacherProfile();
        profile.setUserId(user.getId());
        profile.setSubjects(request.getSubjects());
        profileMapper.insert(profile);
        return issueToken(user);
    }

    public void logout(String token) {
        if (token != null && !token.isBlank()) {
            tokenMapper.deleteById(token);
        }
    }

    public Map<String, Object> profile(Long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", user.getId());
        data.put("phone", user.getPhone());
        data.put("nickname", user.getNickname());
        data.put("role", user.getRole());
        School school = user.getSchoolId() == null ? null : schoolMapper.selectById(user.getSchoolId());
        data.put("school", school == null ? null : school.getName());
        TeacherProfile profile = profileMapper.selectOne(
                new LambdaQueryWrapper<TeacherProfile>().eq(TeacherProfile::getUserId, userId).last("limit 1"));
        data.put("subjects", profile == null ? null : profile.getSubjects());
        data.put("grades", profile == null ? null : profile.getGrades());
        return data;
    }

    public Map<String, Object> updateProfile(Long userId, ProfileUpdateRequest request) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        SysUser update = new SysUser();
        update.setId(userId);
        if (request.getNickname() != null && !request.getNickname().isBlank()) {
            update.setNickname(request.getNickname());
        }
        if (request.getSchoolName() != null && !request.getSchoolName().isBlank()) {
            update.setSchoolId(resolveSchool(request.getSchoolName()));
        }
        userMapper.updateById(update);

        TeacherProfile profile = profileMapper.selectOne(
                new LambdaQueryWrapper<TeacherProfile>().eq(TeacherProfile::getUserId, userId).last("limit 1"));
        if (profile == null) {
            profile = new TeacherProfile();
            profile.setUserId(userId);
        }
        profile.setSubjects(request.getSubjects());
        profile.setGrades(request.getGrades());
        if (profile.getId() == null) {
            profileMapper.insert(profile);
        } else {
            profileMapper.updateById(profile);
        }
        return profile(userId);
    }

    public void changePassword(Long userId, ChangePasswordRequest request) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        if (!passwordEncoder.matches(request.getOldPassword() == null ? "" : request.getOldPassword(),
                user.getPassword())) {
            throw new BusinessException(400, "原密码不正确");
        }
        if (request.getNewPassword() == null || request.getNewPassword().length() < 6) {
            throw new BusinessException(400, "新密码至少 6 位");
        }
        SysUser update = new SysUser();
        update.setId(userId);
        update.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userMapper.updateById(update);
    }

    private LoginVO issueToken(SysUser user) {
        String token = UUID.randomUUID().toString().replace("-", "");
        AuthToken authToken = new AuthToken();
        authToken.setToken(token);
        authToken.setUserId(user.getId());
        authToken.setExpiresAt(LocalDateTime.now().plusDays(7));
        authToken.setCreatedAt(LocalDateTime.now());
        tokenMapper.insert(authToken);

        School school = user.getSchoolId() == null ? null : schoolMapper.selectById(user.getSchoolId());
        LoginVO vo = new LoginVO();
        vo.setToken(token);
        vo.setUserId(user.getId());
        vo.setNickname(user.getNickname());
        vo.setRole(user.getRole());
        vo.setSchoolName(school == null ? null : school.getName());
        return vo;
    }

    private Long resolveSchool(String schoolName) {
        if (schoolName == null || schoolName.isBlank()) {
            return null;
        }
        School school = schoolMapper.selectOne(
                new LambdaQueryWrapper<School>().eq(School::getName, schoolName));
        if (school == null) {
            school = new School();
            school.setName(schoolName);
            school.setStatus(1);
            schoolMapper.insert(school);
        }
        return school.getId();
    }
}

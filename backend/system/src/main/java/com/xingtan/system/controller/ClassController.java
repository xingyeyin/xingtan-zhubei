package com.xingtan.system.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xingtan.common.exception.BusinessException;
import com.xingtan.common.result.Result;
import com.xingtan.system.entity.ClassGroup;
import com.xingtan.system.entity.Student;
import com.xingtan.system.entity.StudentScore;
import com.xingtan.system.entity.SysUser;
import com.xingtan.system.mapper.ClassGroupMapper;
import com.xingtan.system.mapper.StudentMapper;
import com.xingtan.system.mapper.StudentScoreMapper;
import com.xingtan.system.mapper.SysUserMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 班级与学生成绩管理（学情数据）
 */
@RestController
@RequestMapping("/api/classes")
@RequiredArgsConstructor
public class ClassController {

    private final ClassGroupMapper classMapper;
    private final StudentMapper studentMapper;
    private final StudentScoreMapper scoreMapper;
    private final SysUserMapper userMapper;

    private Long currentSchoolId(HttpServletRequest request) {
        Long userId = Long.valueOf(request.getAttribute("userId").toString());
        SysUser user = userMapper.selectById(userId);
        if (user == null || user.getSchoolId() == null) {
            throw new BusinessException(400, "请先完善学校信息");
        }
        return user.getSchoolId();
    }

    private ClassGroup getOwnedClass(Long id, HttpServletRequest request) {
        ClassGroup group = classMapper.selectById(id);
        if (group == null) {
            throw new BusinessException(404, "班级不存在");
        }
        if (!group.getSchoolId().equals(currentSchoolId(request))) {
            throw new BusinessException(403, "无权操作其他学校的班级");
        }
        return group;
    }

    @GetMapping
    public Result<List<Map<String, Object>>> list(HttpServletRequest request) {
        Long schoolId = currentSchoolId(request);
        List<ClassGroup> groups = classMapper.selectList(
                new LambdaQueryWrapper<ClassGroup>().eq(ClassGroup::getSchoolId, schoolId)
                        .orderByAsc(ClassGroup::getGrade).orderByAsc(ClassGroup::getClassName));
        Map<Long, Long> counts = studentMapper.selectList(null).stream()
                .collect(Collectors.groupingBy(Student::getClassId, Collectors.counting()));
        List<Map<String, Object>> records = new ArrayList<>();
        for (ClassGroup g : groups) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", g.getId());
            m.put("grade", g.getGrade());
            m.put("className", g.getClassName());
            m.put("studentCount", counts.getOrDefault(g.getId(), 0L));
            records.add(m);
        }
        return Result.ok(records);
    }

    @PostMapping
    public Result<ClassGroup> create(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        Long schoolId = currentSchoolId(request);
        ClassGroup group = new ClassGroup();
        group.setSchoolId(schoolId);
        group.setGrade(body.get("grade") == null ? "" : body.get("grade").toString());
        group.setClassName(body.get("className") == null ? "" : body.get("className").toString());
        if (group.getClassName().isBlank()) {
            throw new BusinessException(400, "班级名称不能为空");
        }
        classMapper.insert(group);
        return Result.ok(group);
    }

    @PutMapping("/{id}")
    public Result<ClassGroup> update(@PathVariable Long id, @RequestBody Map<String, Object> body,
                                     HttpServletRequest request) {
        ClassGroup group = getOwnedClass(id, request);
        group.setGrade(body.get("grade") == null ? group.getGrade() : body.get("grade").toString());
        group.setClassName(body.get("className") == null ? group.getClassName() : body.get("className").toString());
        classMapper.updateById(group);
        return Result.ok(group);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        getOwnedClass(id, request);
        List<Student> students = studentMapper.selectList(
                new LambdaQueryWrapper<Student>().eq(Student::getClassId, id));
        for (Student s : students) {
            scoreMapper.delete(new LambdaQueryWrapper<StudentScore>().eq(StudentScore::getStudentId, s.getId()));
            studentMapper.deleteById(s.getId());
        }
        classMapper.deleteById(id);
        return Result.ok();
    }

    @GetMapping("/{id}/students")
    public Result<Map<String, Object>> students(@PathVariable Long id, HttpServletRequest request) {
        ClassGroup group = getOwnedClass(id, request);
        List<Student> students = studentMapper.selectList(
                new LambdaQueryWrapper<Student>().eq(Student::getClassId, id).orderByAsc(Student::getId));
        List<Long> ids = students.stream().map(Student::getId).collect(Collectors.toList());
        List<StudentScore> scores = ids.isEmpty() ? List.of()
                : scoreMapper.selectList(new LambdaQueryWrapper<StudentScore>().in(StudentScore::getStudentId, ids));
        Map<Long, Map<String, BigDecimal>> scoreMap = new LinkedHashMap<>();
        for (StudentScore s : scores) {
            scoreMap.computeIfAbsent(s.getStudentId(), k -> new LinkedHashMap<>())
                    .put(s.getSubject(), s.getScore());
        }
        List<Map<String, Object>> records = new ArrayList<>();
        for (Student s : students) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", s.getId());
            m.put("name", s.getName());
            m.put("scores", scoreMap.getOrDefault(s.getId(), new LinkedHashMap<>()));
            records.add(m);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("classId", group.getId());
        data.put("grade", group.getGrade());
        data.put("className", group.getClassName());
        data.put("students", records);
        return Result.ok(data);
    }

    @PostMapping("/{id}/students")
    public Result<Student> addStudent(@PathVariable Long id, @RequestBody Map<String, Object> body,
                                      HttpServletRequest request) {
        getOwnedClass(id, request);
        String name = body.get("name") == null ? "" : body.get("name").toString();
        if (name.isBlank()) {
            throw new BusinessException(400, "学生姓名不能为空");
        }
        Student student = new Student();
        student.setClassId(id);
        student.setName(name);
        studentMapper.insert(student);
        saveScores(student.getId(), body.get("scores"));
        return Result.ok(student);
    }

    @PutMapping("/students/{studentId}")
    public Result<Student> updateStudent(@PathVariable Long studentId, @RequestBody Map<String, Object> body,
                                         HttpServletRequest request) {
        Student student = studentMapper.selectById(studentId);
        if (student == null) {
            throw new BusinessException(404, "学生不存在");
        }
        getOwnedClass(student.getClassId(), request);
        if (body.get("name") != null && !body.get("name").toString().isBlank()) {
            student.setName(body.get("name").toString());
        }
        studentMapper.updateById(student);
        if (body.get("scores") != null) {
            scoreMapper.delete(new LambdaQueryWrapper<StudentScore>().eq(StudentScore::getStudentId, studentId));
            saveScores(studentId, body.get("scores"));
        }
        return Result.ok(student);
    }

    @DeleteMapping("/students/{studentId}")
    public Result<Void> deleteStudent(@PathVariable Long studentId, HttpServletRequest request) {
        Student student = studentMapper.selectById(studentId);
        if (student == null) {
            throw new BusinessException(404, "学生不存在");
        }
        getOwnedClass(student.getClassId(), request);
        scoreMapper.delete(new LambdaQueryWrapper<StudentScore>().eq(StudentScore::getStudentId, studentId));
        studentMapper.deleteById(studentId);
        return Result.ok();
    }

    @GetMapping("/{id}/analysis")
    public Result<Map<String, Object>> analysis(@PathVariable Long id, HttpServletRequest request) {
        ClassGroup group = getOwnedClass(id, request);
        List<Student> students = studentMapper.selectList(
                new LambdaQueryWrapper<Student>().eq(Student::getClassId, id));
        List<Long> ids = students.stream().map(Student::getId).collect(Collectors.toList());
        List<StudentScore> scores = ids.isEmpty() ? List.of()
                : scoreMapper.selectList(new LambdaQueryWrapper<StudentScore>().in(StudentScore::getStudentId, ids));
        Map<String, List<BigDecimal>> bySubject = scores.stream().collect(Collectors.groupingBy(
                StudentScore::getSubject,
                Collectors.mapping(s -> s.getScore() == null ? BigDecimal.ZERO : s.getScore(),
                        Collectors.toList())));
        Map<String, Object> stats = new LinkedHashMap<>();
        for (Map.Entry<String, List<BigDecimal>> e : bySubject.entrySet()) {
            List<BigDecimal> list = e.getValue();
            BigDecimal sum = list.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal avg = sum.divide(BigDecimal.valueOf(list.size()), 1, RoundingMode.HALF_UP);
            long pass = list.stream().filter(v -> v.compareTo(BigDecimal.valueOf(60)) >= 0).count();
            long excellent = list.stream().filter(v -> v.compareTo(BigDecimal.valueOf(90)) >= 0).count();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("avg", avg);
            m.put("count", list.size());
            m.put("passRate", Math.round(pass * 100.0 / list.size()));
            m.put("excellentRate", Math.round(excellent * 100.0 / list.size()));
            stats.put(e.getKey(), m);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("classId", group.getId());
        data.put("className", (group.getGrade() == null ? "" : group.getGrade()) + group.getClassName());
        data.put("studentCount", students.size());
        data.put("stats", stats);
        return Result.ok(data);
    }

    @SuppressWarnings("unchecked")
    private void saveScores(Long studentId, Object scoresObj) {
        if (!(scoresObj instanceof Map)) {
            return;
        }
        Map<String, Object> scores = (Map<String, Object>) scoresObj;
        for (Map.Entry<String, Object> e : scores.entrySet()) {
            if (e.getValue() == null || e.getValue().toString().isBlank()) {
                continue;
            }
            StudentScore score = new StudentScore();
            score.setStudentId(studentId);
            score.setSubject(e.getKey());
            score.setScore(new BigDecimal(e.getValue().toString()));
            scoreMapper.insert(score);
        }
    }
}

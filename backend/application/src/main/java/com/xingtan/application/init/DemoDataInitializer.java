package com.xingtan.application.init;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xingtan.stats.entity.UsageLog;
import com.xingtan.stats.entity.Feedback;
import com.xingtan.stats.entity.ContentReviewLog;
import com.xingtan.stats.mapper.UsageLogMapper;
import com.xingtan.stats.mapper.FeedbackMapper;
import com.xingtan.stats.mapper.ContentReviewLogMapper;
import com.xingtan.kb.entity.KbChunk;
import com.xingtan.kb.entity.KbDocument;
import com.xingtan.kb.mapper.KbChunkMapper;
import com.xingtan.kb.mapper.KbDocumentMapper;
import com.xingtan.system.entity.School;
import com.xingtan.system.entity.SysUser;
import com.xingtan.system.entity.TeacherProfile;
import com.xingtan.system.entity.ClassGroup;
import com.xingtan.system.mapper.SchoolMapper;
import com.xingtan.system.mapper.SysUserMapper;
import com.xingtan.system.mapper.TeacherProfileMapper;
import com.xingtan.system.mapper.ClassGroupMapper;
import com.xingtan.lesson.entity.LessonPlan;
import com.xingtan.lesson.mapper.LessonPlanMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

@Slf4j
@Component
@RequiredArgsConstructor
public class DemoDataInitializer implements ApplicationRunner {

    private final SysUserMapper userMapper;
    private final SchoolMapper schoolMapper;
    private final TeacherProfileMapper profileMapper;
    private final UsageLogMapper usageLogMapper;
    private final KbDocumentMapper documentMapper;
    private final KbChunkMapper chunkMapper;
    private final ClassGroupMapper classGroupMapper;
    private final FeedbackMapper feedbackMapper;
    private final ContentReviewLogMapper contentReviewLogMapper;
    private final LessonPlanMapper lessonPlanMapper;
    private final PasswordEncoder passwordEncoder;

    private static final Random RANDOM = new Random(42);

    private static final String[][] SCHOOLS = {
            {"10", "新乡市红旗区第一实验小学", "新乡市红旗区", "小学"},
            {"11", "原阳县阳光小学", "新乡市原阳县", "小学"},
            {"12", "原阳县实验中学", "新乡市原阳县", "初中"},
            {"13", "延津县育才小学", "新乡市延津县", "小学"},
            {"14", "延津县第二初级中学", "新乡市延津县", "初中"},
            {"15", "封丘县西关小学", "新乡市封丘县", "小学"},
            {"16", "辉县市孟庄镇中学", "新乡市辉县市", "初中"},
            {"17", "卫辉市城郊乡中心校", "新乡市卫辉市", "小学"}
    };

    private static final String[] SUBJECTS = {"语文", "数学", "英语", "物理", "化学", "生物", "历史", "地理", "政治"};
    private static final String[] PRIMARY_SUBJECTS = {"语文", "数学", "英语"};
    private static final String[] GRADES_PRIMARY = {"一年级", "二年级", "三年级", "四年级", "五年级", "六年级"};
    private static final String[] GRADES_JUNIOR = {"七年级", "八年级", "九年级"};
    private static final String[] TEACHER_SURNAMES = {"王", "李", "张", "刘", "陈", "杨", "赵", "黄", "周", "吴", "徐", "孙", "马", "朱", "胡", "郭", "何", "高", "林", "罗", "郑", "梁", "谢", "宋", "唐"};
    private static final String[] TEACHER_GIVEN = {"老师", "明", "华", "强", "军", "芳", "静", "丽", "敏", "磊", "洋", "艳", "勇", "娟", "涛", "超", "秀兰", "霞", "平", "刚", "桂英", "文", "辉", "玲", "健"};

    private static final String[] LOG_ACTIONS = {"GENERATE", "EDIT", "EXPORT", "COLLECT", "FEEDBACK", "LOGIN"};
    private static final String[] LOG_SCENES = {"LESSON_PLAN", "PPT", "WORKSHEET", "APP", "COLLECTION"};
    private static final String[] FEEDBACK_TYPES = {"USEFUL", "NEED_FIX", "REPORT"};
    private static final String[] REVIEW_RESULTS = {"PASS", "BLOCKED"};
    private static final String[] REVIEW_CONTENT_TYPES = {"LESSON_PLAN", "PPT", "WORKSHEET", "TEXTBOOK_CHUNK"};

    @Override
    public void run(ApplicationArguments args) {
        int schoolCount = 0;
        int teacherCount = 0;
        int classCount = 0;
        int lessonPlanCount = 0;
        int logCount = 0;
        int feedbackCount = 0;
        int reviewCount = 0;

        ensureSchool(2L, "新乡某乡村中学", "新乡市", "初中");
        ensureSchool(3L, "原阳县某乡村小学", "新乡市原阳县", "小学");
        schoolCount += 2;

        for (String[] s : SCHOOLS) {
            Long id = Long.parseLong(s[0]);
            ensureSchool(id, s[1], s[2], s[3]);
            schoolCount++;
        }

        ensureUser(1L, "13800000000", "演示教师", 1L);
        ensureUser(2L, "13900000000", "张老师", 2L);
        ensureUser(3L, "13700000000", "李老师", 3L);
        ensureAdmin(4L, "13000000000", "管理员");
        teacherCount += 3;

        ensureProfile(2L, "语文、数学", "五年级", 8, false);
        ensureProfile(3L, "英语", "七年级", 12, true);

        Long logs = usageLogMapper.selectCount(
                new LambdaQueryWrapper<UsageLog>().eq(UsageLog::getUserId, 2L));
        if (logs == null || logs == 0) {
            insertLog(2L, 2L, "LOGIN", "APP", 8, 0);
            insertLog(2L, 2L, "GENERATE", "LESSON_PLAN", 96, -1);
            insertLog(2L, 2L, "EXPORT", "LESSON_PLAN", 15, -1);
            logCount += 3;
        }

        List<Long> allSchoolIds = new ArrayList<>();
        allSchoolIds.add(2L);
        allSchoolIds.add(3L);
        for (String[] s : SCHOOLS) {
            allSchoolIds.add(Long.parseLong(s[0]));
        }

        List<Long> allTeacherIds = new ArrayList<>();
        int phoneSeq = 1;
        for (int i = 0; i < allSchoolIds.size(); i++) {
            Long schoolId = allSchoolIds.get(i);
            School school = schoolMapper.selectById(schoolId);
            if (school == null) continue;
            boolean isPrimary = "小学".equals(school.getLevel());

            int teachersPerSchool = 3 + RANDOM.nextInt(3);
            for (int t = 0; t < teachersPerSchool && phoneSeq <= 25; t++) {
                Long userId = 10L + phoneSeq - 1;
                String phone = "139" + String.format("%08d", phoneSeq);
                String surname = TEACHER_SURNAMES[(phoneSeq - 1) % TEACHER_SURNAMES.length];
                String given = TEACHER_GIVEN[(phoneSeq - 1) % TEACHER_GIVEN.length];
                String nickname = surname + given;

                String subject;
                if (isPrimary) {
                    subject = PRIMARY_SUBJECTS[(phoneSeq + t) % PRIMARY_SUBJECTS.length];
                } else {
                    subject = SUBJECTS[(phoneSeq + t) % SUBJECTS.length];
                }

                String grades = isPrimary
                        ? GRADES_PRIMARY[(phoneSeq + t) % GRADES_PRIMARY.length]
                        : GRADES_JUNIOR[(phoneSeq + t) % GRADES_JUNIOR.length];

                SysUser createdUser = ensureUser(userId, phone, nickname, schoolId);
                if (createdUser != null) {
                    allTeacherIds.add(userId);
                    teacherCount++;
                } else {
                    allTeacherIds.add(userId);
                }

                int teachingYears = 1 + RANDOM.nextInt(30);
                boolean weakNetwork = RANDOM.nextInt(100) < 30;
                ensureProfile(userId, subject, grades, teachingYears, weakNetwork);

                int classesForThisTeacher = 1 + RANDOM.nextInt(2);
                for (int c = 0; c < classesForThisTeacher; c++) {
                    String className = buildClassName(isPrimary, phoneSeq, t, c);
                    ClassGroup cg = ensureClassGroup(schoolId, grades, className);
                    if (cg != null) classCount++;
                }

                phoneSeq++;
            }
        }

        Long totalLessonPlans = lessonPlanMapper.selectCount(new LambdaQueryWrapper<>());
        lessonPlanCount = totalLessonPlans != null ? totalLessonPlans.intValue() : 0;

        Long totalLogs = usageLogMapper.selectCount(new LambdaQueryWrapper<>());
        if (totalLogs == null || totalLogs < 50) {
            logCount += generateUsageLogs(allTeacherIds, allSchoolIds);
        }

        Long totalFeedback = feedbackMapper.selectCount(new LambdaQueryWrapper<>());
        if (totalFeedback == null || totalFeedback < 10) {
            feedbackCount += generateFeedbacks(allTeacherIds);
        }

        Long totalReviews = contentReviewLogMapper.selectCount(new LambdaQueryWrapper<>());
        if (totalReviews == null || totalReviews < 20) {
            reviewCount += generateContentReviews(allTeacherIds);
        }

        ensureStandardDocs();

        log.info("==================== 演示数据初始化汇总 ====================");
        log.info("初始化学校：{} 所", schoolCount);
        log.info("初始化教师：{} 位", teacherCount);
        log.info("初始化班级：{} 个", classCount);
        log.info("教案总数：{} 份", lessonPlanCount);
        Long finalLogCount = usageLogMapper.selectCount(new LambdaQueryWrapper<>());
        log.info("使用日志：{} 条", finalLogCount != null ? finalLogCount : 0);
        Long finalFeedbackCount = feedbackMapper.selectCount(new LambdaQueryWrapper<>());
        log.info("反馈数据：{} 条", finalFeedbackCount != null ? finalFeedbackCount : 0);
        Long finalReviewCount = contentReviewLogMapper.selectCount(new LambdaQueryWrapper<>());
        log.info("内容审核记录：{} 条", finalReviewCount != null ? finalReviewCount : 0);
        log.info("教师账号范围：13900000001 ~ 13900000025（密码 123456）");
        log.info("管理员账号：13000000000（密码 admin123）");
        log.info("============================================================");
    }

    private int generateUsageLogs(List<Long> teacherIds, List<Long> schoolIds) {
        int count = 0;
        int totalLogs = 220;
        for (int i = 0; i < totalLogs; i++) {
            double progress = (double) i / totalLogs;
            int dayOffset = (int) -Math.round(30 * Math.pow(1 - progress, 1.5));
            if (dayOffset > 0) dayOffset = 0;
            if (dayOffset < -30) dayOffset = -30;

            Long userId = teacherIds.get(RANDOM.nextInt(teacherIds.size()));
            Long schoolId = schoolIds.get(RANDOM.nextInt(schoolIds.size()));

            String action;
            double actionR = RANDOM.nextDouble();
            if (actionR < 0.28) action = "GENERATE";
            else if (actionR < 0.48) action = "EDIT";
            else if (actionR < 0.63) action = "EXPORT";
            else if (actionR < 0.78) action = "COLLECT";
            else if (actionR < 0.88) action = "FEEDBACK";
            else action = "LOGIN";

            String scene = LOG_SCENES[RANDOM.nextInt(LOG_SCENES.length)];
            int durationSec;
            switch (action) {
                case "GENERATE":
                    durationSec = 60 + RANDOM.nextInt(240);
                    break;
                case "EDIT":
                    durationSec = 120 + RANDOM.nextInt(480);
                    break;
                case "EXPORT":
                    durationSec = 10 + RANDOM.nextInt(40);
                    break;
                case "LOGIN":
                    durationSec = 3 + RANDOM.nextInt(15);
                    break;
                default:
                    durationSec = 20 + RANDOM.nextInt(120);
            }
            insertLog(userId, schoolId, action, scene, durationSec, dayOffset);
            count++;
        }
        return count;
    }

    private int generateFeedbacks(List<Long> teacherIds) {
        int count = 0;
        int total = 32;
        List<String> usefulContents = Arrays.asList(
                "教案生成很贴合乡村学生学情，推荐！",
                "PPT模板简洁好用，上课方便",
                "题目设计有梯度，分层教学好用",
                "课标匹配准确，节省大量备课时间",
                "导出的Word格式很规范，直接打印",
                "多模态资源多，课堂更生动",
                "自动生成的教学反思很有参考价值",
                "知识点拆解细致，讲解清晰",
                "导学案例设计得很好，学生很喜欢",
                "作业分层设计合理，减轻批改负担",
                "跨学科主题设计得很新颖",
                "重难点分析精准，很实用",
                "学情分析模块对我帮助很大",
                "情境导入环节设计很出彩",
                "板书整理得很清晰，值得收藏",
                "评价方式多样化，很有启发",
                "小组活动设计可直接采用",
                "乡村场景案例很多，非常贴合"
        );
        List<String> needFixContents = Arrays.asList(
                "有些例题偏难，希望调整难度分级",
                "生字词部分希望增加拼音标注",
                "英语听力部分素材再丰富一些",
                "实验步骤配图需要更清晰",
                "希望增加更多本地乡土案例",
                "计算题的步骤解析能更详细",
                "古诗词部分希望增加赏析内容",
                "希望支持自定义模板保存"
        );
        List<String> reportContents = Arrays.asList(
                "生成内容中发现有事实错误，需要修正",
                "部分题目描述有歧义，请检查"
        );

        for (int i = 0; i < total; i++) {
            double progress = (double) i / total;
            int dayOffset = (int) -Math.round(28 * Math.pow(1 - progress, 1.3));
            if (dayOffset > 0) dayOffset = 0;
            if (dayOffset < -28) dayOffset = -28;

            Long userId = teacherIds.get(RANDOM.nextInt(teacherIds.size()));
            String type;
            String content;
            double r = RANDOM.nextDouble();
            if (r < 0.70) {
                type = "USEFUL";
                content = usefulContents.get(RANDOM.nextInt(usefulContents.size()));
            } else if (r < 0.95) {
                type = "NEED_FIX";
                content = needFixContents.get(RANDOM.nextInt(needFixContents.size()));
            } else {
                type = "REPORT";
                content = reportContents.get(RANDOM.nextInt(reportContents.size()));
            }

            Feedback fb = new Feedback();
            fb.setUserId(userId);
            fb.setLessonPlanId(null);
            fb.setType(type);
            fb.setContent(content);
            fb.setCreatedAt(LocalDateTime.now().plusDays(dayOffset)
                    .plusHours(RANDOM.nextInt(10))
                    .plusMinutes(RANDOM.nextInt(60)));
            feedbackMapper.insert(fb);
            count++;
        }
        return count;
    }

    private int generateContentReviews(List<Long> teacherIds) {
        int count = 0;
        int total = 52;
        List<String> passDetails = Arrays.asList(
                "内容合规，符合课标要求",
                "表述准确，无违规内容",
                "教学资源合规，审核通过",
                "内容正向，适合中小学使用",
                "未发现敏感内容，通过",
                "数据来源可靠，审核通过"
        );
        List<String> blockedDetails = Arrays.asList(
                "包含不当表述，已拦截",
                "部分案例存在争议，需人工复核",
                "检测到潜在敏感词，已拦截"
        );

        for (int i = 0; i < total; i++) {
            double progress = (double) i / total;
            int dayOffset = (int) -Math.round(29 * Math.pow(1 - progress, 1.4));
            if (dayOffset > 0) dayOffset = 0;
            if (dayOffset < -29) dayOffset = -29;

            String contentType = REVIEW_CONTENT_TYPES[RANDOM.nextInt(REVIEW_CONTENT_TYPES.length)];
            double r = RANDOM.nextDouble();
            String result = r < 0.95 ? "PASS" : "BLOCKED";
            String detail;
            if ("PASS".equals(result)) {
                detail = passDetails.get(RANDOM.nextInt(passDetails.size()));
            } else {
                detail = blockedDetails.get(RANDOM.nextInt(blockedDetails.size()));
            }

            ContentReviewLog review = new ContentReviewLog();
            review.setTaskId(1000L + i);
            review.setContentType(contentType);
            review.setResult(result);
            review.setDetail(detail);
            review.setCreatedAt(LocalDateTime.now().plusDays(dayOffset)
                    .plusHours(RANDOM.nextInt(12))
                    .plusMinutes(RANDOM.nextInt(60)));
            contentReviewLogMapper.insert(review);
            count++;
        }
        return count;
    }

    private String buildClassName(boolean isPrimary, int phoneSeq, int t, int c) {
        int num = ((phoneSeq + t + c) % 10) + 1;
        String suffix = "(" + num + ")班";
        if (isPrimary) {
            String[] prefixes = {"一年级", "二年级", "三年级", "四年级", "五年级", "六年级"};
            return prefixes[(phoneSeq + t + c) % prefixes.length] + suffix;
        } else {
            String[] prefixes = {"七年级", "八年级", "九年级"};
            return prefixes[(phoneSeq + t + c) % prefixes.length] + suffix;
        }
    }

    private void ensureStandardDocs() {
        addStandard("2022版义务教育物理课程标准·核心素养与内容主题", "物理",
                "物理核心素养：物理观念、科学思维、科学探究、科学态度与责任。内容主题：物质、运动和相互作用、能量、实验探究、跨学科实践。");
        addStandard("2022版义务教育化学课程标准·核心素养与学习主题", "化学",
                "化学核心素养：化学观念、科学思维、科学探究与实践、科学态度与责任。学习主题：科学探究与化学实验、物质的性质与应用、物质的组成与结构、物质的化学变化、化学与社会·跨学科实践。");
        addStandard("2022版义务教育生物学课程标准·核心素养与学习主题", "生物",
                "生物学核心素养：生命观念、科学思维、探究实践、态度责任。学习主题：生物体的结构层次、生物的多样性、生物与环境、植物的生活、人体生理与健康、遗传与进化、生物学与社会·跨学科实践。");
        addStandard("2022版义务教育道德与法治课程标准·核心素养", "道德与法治",
                "道德与法治核心素养：政治认同、道德修养、法治观念、健全人格、责任意识。");
        addStandard("2022版义务教育历史课程标准·核心素养", "历史",
                "历史核心素养：唯物史观、时空观念、史料实证、历史解释、家国情怀。");
        addStandard("2022版义务教育地理课程标准·核心素养", "地理",
                "地理核心素养：人地协调观、综合思维、区域认知、地理实践力。");
    }

    private void addStandard(String title, String subject, String content) {
        Long exists = documentMapper.selectCount(
                new LambdaQueryWrapper<KbDocument>().eq(KbDocument::getDocType, "STANDARD")
                        .eq(KbDocument::getSubject, subject));
        if (exists != null && exists > 0) {
            return;
        }
        KbDocument doc = new KbDocument();
        doc.setTitle(title);
        doc.setDocType("STANDARD");
        doc.setSubject(subject);
        doc.setStatus(1);
        documentMapper.insert(doc);
        KbChunk chunk = new KbChunk();
        chunk.setDocId(doc.getId());
        chunk.setSeq(0);
        chunk.setContent(content);
        chunkMapper.insert(chunk);
    }

    private void ensureSchool(Long id, String name, String region, String level) {
        if (schoolMapper.selectById(id) == null) {
            School school = new School();
            school.setId(id);
            school.setName(name);
            school.setRegion(region);
            school.setLevel(level);
            school.setType("公办");
            school.setStatus(1);
            school.setCreatedAt(LocalDateTime.now().minusDays(60 + RANDOM.nextInt(30)));
            schoolMapper.insert(school);
        }
    }

    private SysUser ensureUser(Long id, String phone, String nickname, Long schoolId) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            user = new SysUser();
            user.setId(id);
            user.setPhone(phone);
            user.setNickname(nickname);
            user.setRole("TEACHER");
            user.setSchoolId(schoolId);
            user.setStatus(1);
            user.setPassword(passwordEncoder.encode("123456"));
            user.setCreatedAt(LocalDateTime.now().minusDays(45 + RANDOM.nextInt(25)));
            user.setLastLoginAt(LocalDateTime.now().minusDays(RANDOM.nextInt(7)));
            userMapper.insert(user);
            return user;
        } else {
            SysUser update = new SysUser();
            update.setId(id);
            update.setPassword(passwordEncoder.encode("123456"));
            update.setSchoolId(schoolId);
            userMapper.updateById(update);
            return null;
        }
    }

    private void ensureAdmin(Long id, String phone, String nickname) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            user = new SysUser();
            user.setId(id);
            user.setPhone(phone);
            user.setNickname(nickname);
            user.setRole("ADMIN");
            user.setStatus(1);
            user.setPassword(passwordEncoder.encode("admin123"));
            user.setCreatedAt(LocalDateTime.now().minusDays(90));
            userMapper.insert(user);
        } else {
            SysUser update = new SysUser();
            update.setId(id);
            update.setRole("ADMIN");
            update.setPassword(passwordEncoder.encode("admin123"));
            userMapper.updateById(update);
        }
    }

    private void ensureProfile(Long userId, String subjects, String grades, int teachingYears, boolean weakNetwork) {
        Long exists = profileMapper.selectCount(
                new LambdaQueryWrapper<TeacherProfile>().eq(TeacherProfile::getUserId, userId));
        if (exists == null || exists == 0) {
            TeacherProfile profile = new TeacherProfile();
            profile.setUserId(userId);
            profile.setSubjects(subjects);
            profile.setGrades(grades);
            profile.setTeachingYears(teachingYears);
            profile.setWeakNetwork(weakNetwork);
            profile.setCreatedAt(LocalDateTime.now().minusDays(40 + RANDOM.nextInt(20)));
            profileMapper.insert(profile);
        }
    }

    private ClassGroup ensureClassGroup(Long schoolId, String grade, String className) {
        Long exists = classGroupMapper.selectCount(
                new LambdaQueryWrapper<ClassGroup>()
                        .eq(ClassGroup::getSchoolId, schoolId)
                        .eq(ClassGroup::getClassName, className));
        if (exists == null || exists == 0) {
            ClassGroup cg = new ClassGroup();
            cg.setSchoolId(schoolId);
            cg.setGrade(grade);
            cg.setClassName(className);
            cg.setCreatedAt(LocalDateTime.now().minusDays(30 + RANDOM.nextInt(30)));
            classGroupMapper.insert(cg);
            return cg;
        }
        return null;
    }

    private void insertLog(Long userId, Long schoolId, String action, String scene, int durationSec, int dayOffset) {
        UsageLog usageLog = new UsageLog();
        usageLog.setUserId(userId);
        usageLog.setSchoolId(schoolId);
        usageLog.setAction(action);
        usageLog.setScene(scene);
        usageLog.setDurationSec(durationSec);
        if ("GENERATE".equals(action)) {
            usageLog.setCostCents(durationSec / 3 + RANDOM.nextInt(5));
        } else {
            usageLog.setCostCents(0);
        }
        usageLog.setCreatedAt(LocalDateTime.now().plusDays(dayOffset)
                .plusHours(RANDOM.nextInt(14))
                .plusMinutes(RANDOM.nextInt(60))
                .plusSeconds(RANDOM.nextInt(60)));
        usageLogMapper.insert(usageLog);
    }
}

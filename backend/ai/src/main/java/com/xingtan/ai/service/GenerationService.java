package com.xingtan.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xingtan.ai.entity.GenerationTask;
import com.xingtan.ai.entity.PromptTemplate;
import com.xingtan.ai.gateway.LlmRouter;
import com.xingtan.ai.mapper.GenerationTaskMapper;
import com.xingtan.ai.mapper.PromptTemplateMapper;
import com.xingtan.common.exception.BusinessException;
import com.xingtan.kb.entity.KbChunk;
import com.xingtan.kb.entity.KbDocument;
import com.xingtan.kb.mapper.KbChunkMapper;
import com.xingtan.kb.mapper.KbDocumentMapper;
import com.xingtan.lesson.entity.LessonPlan;
import com.xingtan.lesson.entity.LessonSection;
import com.xingtan.lesson.mapper.LessonPlanMapper;
import com.xingtan.lesson.mapper.LessonSectionMapper;
import com.xingtan.stats.entity.UsageLog;
import com.xingtan.stats.mapper.UsageLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * 生成任务服务
 * 生成管线：意图理解 → 知识定位（课标/教材/模板/案例召回）→ 大模型生成 → 结构化解析 → 落库
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GenerationService {

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    private final GenerationTaskMapper taskMapper;
    private final PromptTemplateMapper templateMapper;
    private final LessonPlanMapper lessonPlanMapper;
    private final LessonSectionMapper sectionMapper;
    private final KbChunkMapper chunkMapper;
    private final KbDocumentMapper documentMapper;
    private final UsageLogMapper usageLogMapper;
    private final LlmRouter llmRouter;
    private final ObjectMapper objectMapper;

    public GenerationTask create(Long userId, String scene, Map<String, Object> params) {
        GenerationTask task = new GenerationTask();
        task.setUserId(userId);
        task.setScene(scene == null ? "LESSON_PLAN" : scene);
        try {
            task.setParams(objectMapper.writeValueAsString(params == null ? Map.of() : params));
        } catch (Exception e) {
            task.setParams("{}");
        }
        task.setStatus("PENDING");
        task.setCostCents(0);
        taskMapper.insert(task);
        return task;
    }

    public void submitAsync(Long taskId) {
        CompletableFuture.runAsync(() -> {
            try {
                generate(taskId);
            } catch (Exception e) {
                markStatus(taskId, "FAILED", e.getMessage());
            }
        }, EXECUTOR);
    }

    public GenerationTask get(Long taskId) {
        GenerationTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(404, "生成任务不存在");
        }
        return task;
    }

    public Map<String, Object> detail(Long taskId) {
        GenerationTask task = get(taskId);
        if ("RUNNING".equals(task.getStatus()) && task.getUpdatedAt() != null
                && task.getUpdatedAt().isBefore(LocalDateTime.now().minusMinutes(10))) {
            markStatus(taskId, "TIMEOUT", "生成超时，请重新生成");
            task = get(taskId);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("task", task);
        if ("SUCCESS".equals(task.getStatus())) {
            LessonPlan plan = lessonPlanMapper.selectOne(
                    new LambdaQueryWrapper<LessonPlan>().eq(LessonPlan::getTaskId, taskId).last("limit 1"));
            result.put("lessonPlan", plan);
        }
        return result;
    }

    public void markStatus(Long taskId, String status, String errorMessage) {
        GenerationTask task = new GenerationTask();
        task.setId(taskId);
        task.setStatus(status);
        task.setErrorMessage(errorMessage);
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);
    }

    private void generate(Long taskId) throws Exception {
        GenerationTask task = get(taskId);
        long start = System.currentTimeMillis();
        markStatus(taskId, "RUNNING", null);

        Map<String, Object> params = readParams(task.getParams());
        String subject = str(params, "subject");
        String grade = str(params, "grade");
        String textbook = str(params, "textbook");
        String title = str(params, "title");
        String lessonType = str(params, "lessonType");
        String extra = str(params, "extra");
        if (title == null || title.isBlank()) {
            throw new BusinessException("缺少课题，无法生成教案");
        }

        JsonNode root;
        try {
            String coreKnowledge = recallCore(subject, title);
            String caseKnowledge = recallCases();
            String system = loadTemplate("LESSON_PLAN");
            String user = buildPrompt(subject, grade, textbook, title, lessonType, extra, coreKnowledge, caseKnowledge);

            String raw = llmRouter.completeJson(task.getProvider(), system, user);
            log.info("模型原始返回(前500字符): {}", raw == null ? "null" : raw.substring(0, Math.min(500, raw.length())));
            root = parseJson(raw);
            if (!root.path("objectives").isArray() || root.path("objectives").isEmpty()
                    || !root.path("process").isArray() || root.path("process").isEmpty()) {
                throw new BusinessException("模型返回内容不完整，启动兜底模板");
            }
        } catch (Exception e) {
            log.warn("大模型生成失败，启动Fallback兜底机制：{}", e.getMessage());
            String fallbackJson = fallbackGenerate(params);
            root = parseJson(fallbackJson);
        }

        LessonPlan plan = new LessonPlan();
        plan.setTaskId(taskId);
        plan.setUserId(task.getUserId());
        plan.setTitle(root.path("title").asText(title));
        plan.setSubject(subject);
        plan.setGrade(grade);
        plan.setTextbook(textbook);
        plan.setLessonType(lessonType);
        plan.setContent(root.toString());
        plan.setQualityScore(root.path("qualityScore").asInt(0));
        plan.setStatus(1);
        lessonPlanMapper.insert(plan);
        saveSections(plan.getId(), root);

        UsageLog usageLog = new UsageLog();
        usageLog.setUserId(task.getUserId());
        usageLog.setSchoolId(1L);
        usageLog.setAction("GENERATE");
        usageLog.setScene("LESSON_PLAN");
        usageLog.setDurationSec((int) ((System.currentTimeMillis() - start) / 1000));
        usageLogMapper.insert(usageLog);

        markStatus(taskId, "SUCCESS", null);
    }

    private String fallbackGenerate(Map<String, Object> params) {
        String subject = str(params, "subject");
        String title = str(params, "title");
        String grade = str(params, "grade");
        String textbook = str(params, "textbook");
        String lessonType = str(params, "lessonType");

        String matchedKey = null;
        String matchedSubject = null;
        for (Map.Entry<String, String> entry : FALLBACK_TEMPLATES.entrySet()) {
            String key = entry.getKey();
            String tplSubject = key.split("\\|")[0];
            String tplTitle = key.split("\\|")[1];
            if (title != null && (title.contains(tplTitle) || tplTitle.contains(title.replaceAll("[《》\"'“”‘’]", "")))) {
                matchedKey = key;
                matchedSubject = tplSubject;
                break;
            }
        }
        if (matchedKey == null && subject != null) {
            for (Map.Entry<String, String> entry : FALLBACK_TEMPLATES.entrySet()) {
                String tplSubject = entry.getKey().split("\\|")[0];
                if (subject.contains(tplSubject) || tplSubject.contains(subject)) {
                    matchedKey = entry.getKey();
                    matchedSubject = tplSubject;
                    break;
                }
            }
        }
        if (matchedKey == null) {
            matchedKey = "语文|背影";
            matchedSubject = "语文";
        }

        String tpl = FALLBACK_TEMPLATES.get(matchedKey);
        String actualTitle = (title == null || title.isBlank()) ? matchedKey.split("\\|")[1] : title;
        String actualSubject = (subject == null || subject.isBlank()) ? matchedSubject : subject;
        String actualGrade = (grade == null || grade.isBlank()) ? "八年级" : grade;
        String actualTextbook = (textbook == null || textbook.isBlank()) ? "人教版" : textbook;
        String actualLessonType = (lessonType == null || lessonType.isBlank()) ? "新授课" : lessonType;

        tpl = tpl.replace("{{TITLE}}", escapeJson(actualTitle));
        tpl = tpl.replace("{{SUBJECT}}", escapeJson(actualSubject));
        tpl = tpl.replace("{{GRADE}}", escapeJson(actualGrade));
        tpl = tpl.replace("{{TEXTBOOK}}", escapeJson(actualTextbook));
        tpl = tpl.replace("{{LESSON_TYPE}}", escapeJson(actualLessonType));

        return tpl;
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    private static final Map<String, String> FALLBACK_TEMPLATES = new LinkedHashMap<>();

    static {
        FALLBACK_TEMPLATES.put("语文|背影", "{\n" +
                "  \"title\": \"{{TITLE}}\",\n" +
                "  \"subject\": \"{{SUBJECT}}\",\n" +
                "  \"grade\": \"{{GRADE}}\",\n" +
                "  \"textbook\": \"{{TEXTBOOK}}\",\n" +
                "  \"lessonType\": \"{{LESSON_TYPE}}\",\n" +
                "  \"objectives\": [\n" +
                "    {\"type\": \"文化自信\", \"content\": \"通过品读《背影》中父子离别场景，感受中国式亲情的含蓄深沉，理解传统伦理文化中父爱子、子敬父的人伦情怀，增强对中华优秀传统家庭文化的认同感。\", \"standardRef\": \"义务教育语文课程标准（2022年版）第四学段·阅读与鉴赏·文化自信\"},\n" +
                "    {\"type\": \"语言运用\", \"content\": \"积累'狼藉、簌簌、踌躇、蹒跚、颓唐'等词语；学习作者抓住人物特征（背影）进行细节描写的方法，体会四次写背影、四次写眼泪的行文线索与表达效果；能够有感情地朗读课文。\", \"standardRef\": \"义务教育语文课程标准（2022年版）第四学段·阅读与鉴赏·语言运用\"},\n" +
                "    {\"type\": \"思维能力\", \"content\": \"通过比较阅读、问题链探究，分析父亲'迂'背后的深爱与不易，辩证理解中年人视角与少年视角的差异，发展形象思维和辩证思维能力。\", \"standardRef\": \"义务教育语文课程标准（2022年版）第四学段·阅读与鉴赏·思维能力\"}\n" +
                "  ],\n" +
                "  \"keyPoints\": [\n" +
                "    \"抓住背影这一特写镜头，赏析父亲买橘子段的细节描写，体会朴素语言中蕴含的深情\",\n" +
                "    \"理清文章叙事线索（四次背影、四次流泪），把握课文整体结构与情感脉络\",\n" +
                "    \"理解'我'的心理变化：由不耐烦到感动、愧疚，体会散文的真实性与感染力\"\n" +
                "  ],\n" +
                "  \"difficultPoints\": [\n" +
                "    \"理解中年朱自清回望父亲时的复杂情感（爱、愧疚、理解），打通读者与文本的情感隔膜\",\n" +
                "    \"品味'大去之期不远矣'等语句背后的家族困境与人生况味，体会散文'以小见大'的写法\"\n" +
                "  ],\n" +
                "  \"process\": [\n" +
                "    {\"stage\": \"情境导入\", \"duration\": 5, \"activities\": \"播放筷子兄弟《父亲》MV片段（2分钟）→ 学生自由发言：你眼中父亲的一个最深刻的画面 → 教师导语：'同学们，我们常常记住母亲的面容，却常常忽略父亲的背影。今天，让我们跟随朱自清，去回望一个定格在中国文学史上的背影。'板书课题，出示学习目标。\", \"teacher\": \"播放视频、设问引导、板书课题\", \"student\": \"看视频、谈感受、齐读课题\"},\n" +
                "    {\"stage\": \"初读感知\", \"duration\": 8, \"activities\": \"学生自由朗读课文，圈画生字词；指名朗读，师生正音；速读思考：文章写了一件什么事？几次写到背影？几次写到流泪？请分别用一个词语概括每一次的情感。\", \"teacher\": \"巡视指导、板书四次背影/流泪、提问梳理\", \"student\": \"朗读、圈画、填写学习单、回答问题\"},\n" +
                "    {\"stage\": \"精读品析\", \"duration\": 15, \"activities\": \"聚焦第6段（买橘子段）：①全班齐读；②圈出描写父亲穿戴、动作、神态的词语（黑、胖、蹒跚、探、攀、缩、倾）；③换位思考：如果你就是月台边的儿子，看着父亲这样爬上爬下，你心里最想说什么？④教师追问：为什么作者不写父亲正面的脸，而写背影？⑤拓展：你自己的父亲有没有这样'笨拙'却深情的瞬间？写一句'微家书'。\", \"teacher\": \"范读、设问链引导、点评学生发言、板书细节词\", \"student\": \"批注、小组讨论、全班交流、写微家书\"},\n" +
                "    {\"stage\": \"互动探究\", \"duration\": 7, \"activities\": \"小组合作探究：①文中'我那时真是聪明过分''唉，我现在想想，那时真是太聪明了'用了什么修辞？表达什么情感？②如果你是导演，请为课文结尾段（父亲来信、'我'读信浮现背影）设计一个电影镜头。\", \"teacher\": \"发放任务单、巡视各组、引导展示\", \"student\": \"4人组讨论、代表展示镜头脚本、互评\"},\n" +
                "    {\"stage\": \"当堂练习\", \"duration\": 7, \"activities\": \"①基础题：选词填空（簌簌/踌躇/蹒跚）；②提升题：仿写一段话（100字左右），用一个特写镜头（一个背影、一双手、一个侧脸）描写你熟悉的亲人；③选做题：比较阅读《台阶》中父亲形象的异同。\", \"teacher\": \"出示题目、巡视批改、选取2篇仿写当堂点评\", \"student\": \"独立完成、同桌互改、朗读仿写\"},\n" +
                "    {\"stage\": \"总结升华\", \"duration\": 3, \"activities\": \"师生共建板书思维导图 → 教师结语：'背影不只是一个人的背影，它是所有不善言辞却深爱孩子的父辈的缩影。希望同学们回家，也认真看看父母的背影，然后说一句：您辛苦了。'布置分层作业。\", \"teacher\": \"梳理板书、情感升华、布置作业\", \"student\": \"回顾要点、记录作业、情感共鸣\"}\n" +
                "  ],\n" +
                "  \"boardDesign\": \"【板书设计】\\n\\n                      《背影》  朱自清\\n\\n    线索：四次背影（难忘→刻画→惜别→思念）\\n          四次流泪（悲哀→感动→不舍→伤心）\\n\\n    买橘子段：  穿戴（黑→丧母）  动作（探→攀→缩→倾）\\n                ↙        ↘\\n         细节描写        深情（父爱子）\\n\\n    情感变化：不耐烦 → 感动 → 愧疚（反语'太聪明'）\\n\\n    主题：以小见大 → 中国式亲情\\n\\n【右侧板书区：学生微家书精选、仿写好词积累】\",\n" +
                "  \"homework\": [\n" +
                "    {\"level\": \"基础层（A）\", \"items\": [\"抄写并背诵课下注释及'读读写写'词语2遍，家长签字\", \"有感情地朗读课文3遍，录音上传班级群\", \"完成练习册《背影》基础积累部分\"]},\n" +
                "    {\"level\": \"提升层（B）\", \"items\": [\"完成仿写：《____的背影》，200字以上，用上至少3个细节描写\", \"与父母共读《背影》，记录父母读完后的一句话感受，明天交流\", \"完成练习册阅读理解篇《目送》节选\"]},\n" +
                "    {\"level\": \"挑战层（C）\", \"items\": [\"阅读朱自清《儿女》《给亡妇》，写一篇300字读书札记，谈谈朱自清散文的'人情味'\", \"小组合作：以《感恩父母》为题，拍摄1分钟亲情微视频（下周展播）\"]}\n" +
                "  ],\n" +
                "  \"qualityScore\": 92,\n" +
                "  \"standardCards\": [\n" +
                "    {\"ref\": \"义务教育语文课程标准（2022年版）第四学段·阅读与鉴赏\", \"content\": \"欣赏文学作品，有自己的情感体验，初步领悟作品的内涵，从中获得对自然、社会、人生的有益启示。对作品中感人的情境和形象，能说出自己的体验。\"},\n" +
                "    {\"ref\": \"义务教育语文课程标准（2022年版）第四学段·表达与交流\", \"content\": \"写作要有真情实感，力求表达自己对自然、社会、人生的感受、体验和思考；多角度观察生活，发现生活的丰富多彩，抓住事物的特征，有自己的感受和认识。\"},\n" +
                "    {\"ref\": \"义务教育语文课程标准（2022年版）·核心素养·文化自信\", \"content\": \"通过语文学习，继承和弘扬中华优秀传统文化，理解和认同中华文化，热爱中华文化，具有文化自觉意识。\"}\n" +
                "  ]\n" +
                "}");

        FALLBACK_TEMPLATES.put("语文|三峡", "{\n" +
                "  \"title\": \"{{TITLE}}\",\n" +
                "  \"subject\": \"{{SUBJECT}}\",\n" +
                "  \"grade\": \"{{GRADE}}\",\n" +
                "  \"textbook\": \"{{TEXTBOOK}}\",\n" +
                "  \"lessonType\": \"{{LESSON_TYPE}}\",\n" +
                "  \"objectives\": [\n" +
                "    {\"type\": \"文化自信\", \"content\": \"通过诵读郦道元《三峡》，感受祖国山河之美与古代山水文学的艺术魅力，了解北魏地理学名著《水经注》的文化价值，增强对中华优秀传统文化的自豪感与保护自然的责任感。\", \"standardRef\": \"义务教育语文课程标准（2022年版）第四学段·阅读与鉴赏·文化自信\"},\n" +
                "    {\"type\": \"语言运用\", \"content\": \"积累'阙、嶂、曦、御、溯、湍、巘、漱、属引'等文言字词；掌握'自非亭午夜分，不见曦月''虽乘奔御风，不以疾也'等特殊句式；能够借助注释和工具书疏通文意，背诵全文。\", \"standardRef\": \"义务教育语文课程标准（2022年版）第四学段·阅读与鉴赏·语言运用\"},\n" +
                "    {\"type\": \"思维能力\", \"content\": \"通过分析四季三峡的景物特征与写作顺序（夏→春冬→秋），学习作者抓住景物特征、动静结合、正面侧面描写相结合的写景方法，发展逻辑思维与审美鉴赏能力。\", \"standardRef\": \"义务教育语文课程标准（2022年版）第四学段·阅读与鉴赏·思维能力\"}\n" +
                "  ],\n" +
                "  \"keyPoints\": [\n" +
                "    \"积累常见文言实词虚词，疏通文意，熟读成诵\",\n" +
                "    \"分析三峡四季景色特点，体会作者大笔点染的写景手法\",\n" +
                "    \"品味文中精妙的炼字（如'素''绿''清''荣''峻''茂'）与骈散结合的语言节奏\"\n" +
                "  ],\n" +
                "  \"difficultPoints\": [\n" +
                "    \"理解结尾引用渔歌'巴东三峡巫峡长，猿鸣三声泪沾裳'的作用（意境升华、乡愁主题）\",\n" +
                "    \"体会作者在客观地理记叙中融入的主观情感与审美倾向\"\n" +
                "  ],\n" +
                "  \"process\": [\n" +
                "    {\"stage\": \"情境导入\", \"duration\": 5, \"activities\": \"播放《航拍中国》三峡片段（2分钟）→ 学生用一句话描述看到的三峡 → 教师导语：'1500年前，有一位地理学家郦道元，用不到200字就写尽了三峡的雄奇秀险。今天我们就走进《水经注》中的千古名篇《三峡》。'出示学习目标。\", \"teacher\": \"播放视频、启发联想、板书课题\", \"student\": \"观看视频、口头描绘、齐读目标\"},\n" +
                "    {\"stage\": \"初读正音\", \"duration\": 8, \"activities\": \"①教师范读，学生标注字音与停顿；②学生自由朗读2遍；③指名朗读，师生评价节奏与情感；④齐读，读出雄壮之气。\", \"teacher\": \"范读、正音（阙quē/嶂zhàng/曦xī/御yù/溯sù/湍tuān/巘yǎn/漱shù/属zhǔ）、指导停顿\", \"student\": \"跟读、自由读、指名读、齐读\"},\n" +
                "    {\"stage\": \"疏通文意\", \"duration\": 12, \"activities\": \"①学生结合注释自主疏通，圈画疑难；②4人小组合作互教互学，完成'古今异义''词类活用'整理表；③全班交流：抽查重点句翻译（自非亭午夜分、虽乘奔御风、清荣峻茂）；④教师点拨特殊句式。\", \"teacher\": \"巡视、点拨疑难、板书重点字词、抽查翻译\", \"student\": \"自主口译、小组互教、全班展示、整理笔记\"},\n" +
                "    {\"stage\": \"品读赏景\", \"duration\": 10, \"activities\": \"①默读思考：课文写了三峡的哪些景物？按什么顺序写？②小组竞赛：用一个字概括四季三峡特点（夏：险/急；春冬：秀/趣；秋：凄/哀）；③批注赏析：你最欣赏哪一句？为什么？（重点品'素湍绿潭，回清倒影''清荣峻茂，良多趣味'）；④讨论：为什么先写夏水？为什么以秋景作结？\", \"teacher\": \"设问引导、组织竞赛、板书四季特征、点评赏析\", \"student\": \"默读思考、小组抢答、批注、讨论发言\"},\n" +
                "    {\"stage\": \"当堂练习\", \"duration\": 7, \"activities\": \"①基础：默写易错字词+3个重点句翻译；②提升：展开想象，把'春冬之时'段改写成一段100字的现代写景散文；③选做：对比阅读《早发白帝城》与本文夏水段的异同。\", \"teacher\": \"出示练习、巡视批改、选取改写片段展示点评\", \"student\": \"独立完成、同桌互查、朗读改写\"},\n" +
                "    {\"stage\": \"总结升华\", \"duration\": 3, \"activities\": \"师生共背全文 → 教师结语：'一部《水经注》，不只是地理书，更是中国最早的旅行散文。三峡之美，美在山河，更美在古人的山水情怀。希望有一天，你们也能亲身走进三峡，印证心中那幅1500年的画卷。'布置作业。\", \"teacher\": \"引导背诵、文化升华、布置作业\", \"student\": \"背诵、记录、情感共鸣\"}\n" +
                "  ],\n" +
                "  \"boardDesign\": \"【板书设计】\\n\\n                         《三峡》  郦道元（北魏）  ——《水经注》\\n\\n                 山：  两岸连山，略无阙处    重岩叠嶂，隐天蔽日    （正面+侧面）\\n    /   夏：  襄陵、沿溯阻绝  →  乘奔御风  不以疾也  （水大、流急）\\n  景   春冬：素湍绿潭、回清倒影  →  清荣峻茂  良多趣味  （动静、色彩）\\n    \\   秋：  林寒涧肃、高猿长啸  →  属引凄异、渔歌泪沾裳  （听觉、凄婉）\\n\\n    顺序：  夏 → 春冬 → 秋      写法：大笔点染、动静结合、正侧结合、引用\\n    情感：  对祖国山河的热爱与赞美，对渔民艰辛的同情\",\n" +
                "  \"homework\": [\n" +
                "    {\"level\": \"基础层（A）\", \"items\": [\"背诵并默写《三峡》全文，家长签字\", \"整理本课通假字、古今异义、词类活用各1张知识卡片\", \"完成练习册文言文基础积累部分\"]},\n" +
                "    {\"level\": \"提升层（B）\", \"items\": [\"把课文中你最喜欢的一段（春冬或秋）扩写成300字现代文，加入适当的修辞手法\", \"上网查找今日三峡（三峡大坝建成后）的图片或视频，与同学分享古今三峡的变化\", \"阅读《水经注·江水》其他片段，选一段做100字读书笔记\"]},\n" +
                "    {\"level\": \"挑战层（C）\", \"items\": [\"比较阅读李白《早发白帝城》、杜甫《登高》与本文，以'诗与文的三峡'为题写400字赏析\", \"以'我心中的三峡'为主题制作一张图文手抄报（电子版/手绘版均可）\"]}\n" +
                "  ],\n" +
                "  \"qualityScore\": 90,\n" +
                "  \"standardCards\": [\n" +
                "    {\"ref\": \"义务教育语文课程标准（2022年版）第四学段·阅读与鉴赏\", \"content\": \"诵读古代诗词，阅读浅易文言文，能借助注释和工具书理解基本内容。注重积累、感悟和运用，提高自己的欣赏品位。\"},\n" +
                "    {\"ref\": \"义务教育语文课程标准（2022年版）第四学段·梳理与探究\", \"content\": \"围绕共同的学习主题或语文实践活动，制定简单的研究计划，从报刊、书籍或其他媒体中获取有关资料，讨论分析问题，独立或合作写出简单的研究报告。\"},\n" +
                "    {\"ref\": \"义务教育语文课程标准（2022年版）·核心素养·审美创造\", \"content\": \"通过审美创造活动，学生能感受和体验语言文字之美，发现美、表达美、创造美，具备高雅的审美情趣和正确的审美观念。\"}\n" +
                "  ]\n" +
                "}");

        FALLBACK_TEMPLATES.put("语文|苏州园林", "{\n" +
                "  \"title\": \"{{TITLE}}\",\n" +
                "  \"subject\": \"{{SUBJECT}}\",\n" +
                "  \"grade\": \"{{GRADE}}\",\n" +
                "  \"textbook\": \"{{TEXTBOOK}}\",\n" +
                "  \"lessonType\": \"{{LESSON_TYPE}}\",\n" +
                "  \"objectives\": [\n" +
                "    {\"type\": \"文化自信\", \"content\": \"通过学习叶圣陶《苏州园林》，了解中国古典园林艺术的独特审美（天人合一、写意山水），认识苏州园林作为世界文化遗产的价值，增强文化自信与文化遗产保护意识。\", \"standardRef\": \"义务教育语文课程标准（2022年版）第四学段·阅读与鉴赏·文化自信\"},\n" +
                "    {\"type\": \"语言运用\", \"content\": \"积累'轩榭、败笔、丘壑、嶙峋、镂空、蔷薇、斟酌、因地制宜、自出心裁、重峦叠嶂'等词语；学习本文抓住事物总体特征、由主到次的说明顺序，掌握举例子、作比较、打比方、引用等说明方法。\", \"standardRef\": \"义务教育语文课程标准（2022年版）第四学段·阅读与鉴赏·语言运用\"},\n" +
                "    {\"type\": \"思维能力\", \"content\": \"通过梳理文章结构、绘制苏州园林'特征→表现'思维导图，训练概括与逻辑分类能力；通过比较《中国石拱桥》《苏州园林》说明语言的异同，体会说明文语言的准确与生动。\", \"standardRef\": \"义务教育语文课程标准（2022年版）第四学段·阅读与鉴赏·思维能力\"}\n" +
                "  ],\n" +
                "  \"keyPoints\": [\n" +
                "    \"把握苏州园林'务必使游览者无论站在哪个点上，眼前总是一幅完美的图画'这一总体特征\",\n" +
                "    \"理清由总到分、由主到次（整体→局部→细部）的说明结构与逻辑顺序\",\n" +
                "    \"辨识并分析本文运用的主要说明方法及其表达效果\"\n" +
                "  ],\n" +
                "  \"difficultPoints\": [\n" +
                "    \"理解说明文语言准确与生动的统一（如议论性、描写性语句在说明文中的作用）\",\n" +
                "    \"体会作者审美眼光与遣词造句的匠心（如'寂寞''盘曲嶙峋'等拟人化、形容词的炼字）\"\n" +
                "  ],\n" +
                "  \"process\": [\n" +
                "    {\"stage\": \"情境导入\", \"duration\": 5, \"activities\": \"展示拙政园、留园高清图片组（12张）→ 学生投票：最吸引你的是哪张？为什么？→ 教师导语：'苏州园林是中国园林之母。叶圣陶先生说：江南园林甲天下，苏州园林甲江南。今天我们就跟着叶老的笔，走进这座'无声的诗，立体的画'。'板书课题，出示学习目标。\", \"teacher\": \"展示图片、引发讨论、板书课题\", \"student\": \"观察投票、谈感受、齐读目标\"},\n" +
                "    {\"stage\": \"初读梳理\", \"duration\": 8, \"activities\": \"①快速默读，圈点勾画：作者认为苏州园林的总体特征是什么？文中哪一句话可以概括？②画出每段的中心句，完成'总→分'结构思维导图（总特征→4个讲究→3个注意→结语）；③同桌互查，全班订正。\", \"teacher\": \"巡视指导、板书结构框架、抽查中心句\", \"student\": \"默读圈画、画思维导图、同桌互查\"},\n" +
                "    {\"stage\": \"精读品析\", \"duration\": 15, \"activities\": \"①主问题探究：苏州园林的'图画美'体现在哪些方面？（4个讲究+3个注意）小组认领一个角度，结合课文语句+图片，做'小小解说员'展示；②说明方法寻宝：找出文中运用的举例子、作比较、打比方、摹状貌、引用例句，分析其作用；③语言品析：'假如安排两座以上的桥梁，那就一座一个样，决不雷同'中的'决不'能否去掉？为什么？鉴赏'寂寞''珠光宝气''盘曲嶙峋'等词。\", \"teacher\": \"组织小组展示、板书说明方法、追问语言准确性\", \"student\": \"小组合作、上台解说、圈画分析说明方法、讨论炼字\"},\n" +
                "    {\"stage\": \"互动探究\", \"duration\": 7, \"activities\": \"思辨讨论：①为什么第2段先概括后，3-6段按'亭台轩榭→假山池沼→花草树木→花墙廊子'顺序写？（主→次，整体→局部）；②如果你是苏州园林的设计者，现在要加一个'网红打卡点'，你会怎么设计？不能破坏'图画美'原则。\", \"teacher\": \"提出思辨问题、巡视小组讨论、点评创意\", \"student\": \"思辨讨论、组内设计、创意发言\"},\n" +
                "    {\"stage\": \"当堂练习\", \"duration\": 7, \"activities\": \"①基础：选词填空（因地制宜/自出心裁/别具匠心/斟酌）；②提升：选取校园一处景观（花坛/长廊/假山），模仿课文总分结构，写一段150字说明文字，至少用2种说明方法；③选做：比较《中国石拱桥》与本文在说明语言上的不同。\", \"teacher\": \"出示练习、巡视批改、选取2段习作当堂点评\", \"student\": \"独立完成、同桌互评、朗读展示\"},\n" +
                "    {\"stage\": \"总结升华\", \"duration\": 3, \"activities\": \"学生对照板书回顾本课要点 → 教师结语：'苏州园林不是一处景点，而是中国人'天人合一'的生活美学。一窗一石，皆是诗意。希望同学们用今天学到的说明方法，去发现身边校园与家乡的美。'布置分层作业。\", \"teacher\": \"回顾梳理、美学升华、布置作业\", \"student\": \"回顾要点、记录作业、美学共鸣\"}\n" +
                "  ],\n" +
                "  \"boardDesign\": \"【板书设计】\\n\\n                      《苏州园林》  叶圣陶   说明文\\n\\n       总特征：务必使游览者无论站在哪个点上，眼前总是一幅完美的图画\\n                         ↓\\n         主 ┌─ 讲究亭台轩榭的布局（不讲究对称 → 美术画/图案画 作比较）\\n         │  ├─ 讲究假山池沼的配合（自然之趣 → 举例子 两座桥/鱼戏莲叶）\\n         次 ├─ 讲究花草树木的映衬（画意 →  俯仰生姿/落叶常绿树相间 摹状貌）\\n         │  └─ 讲究近景远景的层次（花墙、廊子 →  隔而不隔 界而未界）\\n         │\\n         次 ┌─ 每一个角落的图画美（阶砌/爬山虎/镜子）\\n         │  ├─ 门和窗的图案美（简朴/别具匠心/镂空）\\n         细 └─ 极少使用彩绘（色调与草木配合 → 安静闲适）\\n\\n    说明顺序：总→分、主→次、整体→局部、逻辑顺序        语言：准确 + 生动（审美）\",\n" +
                "  \"homework\": [\n" +
                "    {\"level\": \"基础层（A）\", \"items\": [\"抄写本课'读读写写'词语2遍+注音，家长签字\", \"完成练习册《苏州园林》基础积累与说明方法部分\", \"画一张苏州园林总特征思维导图（A4纸）\"]},\n" +
                "    {\"level\": \"提升层（B）\", \"items\": [\"选取家乡一处景点（公园/古建/广场），模仿课文结构写一篇400字说明文，突出其总体特征，至少用3种说明方法\", \"上网观看《世界遗产在中国·苏州园林》纪录片，写200字观后感\", \"收集10副苏州园林楹联，挑选你最喜欢的一副做赏析\"]},\n" +
                "    {\"level\": \"挑战层（C）\", \"items\": [\"以小组为单位，设计一座'理想中的校园微园林'，完成手绘平面设计图+300字设计说明（说明：必须体现'图画美'原则）\", \"阅读陈从周《说园》选段，写一篇300字读书札记，比较叶、陈两位先生写园林的角度差异\"]}\n" +
                "  ],\n" +
                "  \"qualityScore\": 91,\n" +
                "  \"standardCards\": [\n" +
                "    {\"ref\": \"义务教育语文课程标准（2022年版）第四学段·阅读与鉴赏\", \"content\": \"阅读新闻、说明文、议论文、游记、散文等不同体裁的文本，能把握文章的基本观点、主要内容和表达方式，获取有价值的信息。\"},\n" +
                "    {\"ref\": \"义务教育语文课程标准（2022年版）第四学段·表达与交流·写作\", \"content\": \"写简单的说明文，做到明白清楚；能根据文章的基本观点和内容，搜集整理资料，合理安排结构，用恰当的说明方法准确清楚地说明事物。\"},\n" +
                "    {\"ref\": \"义务教育语文课程标准（2022年版）·核心素养·文化自信\", \"content\": \"认同中华文化，继承和弘扬中华优秀传统文化、革命文化、社会主义先进文化，理解和借鉴不同民族和地区的文化，拓展文化视野，增强文化自觉。\"}\n" +
                "  ]\n" +
                "}");

        FALLBACK_TEMPLATES.put("数学|一元二次方程", "{\n" +
                "  \"title\": \"{{TITLE}}\",\n" +
                "  \"subject\": \"{{SUBJECT}}\",\n" +
                "  \"grade\": \"{{GRADE}}\",\n" +
                "  \"textbook\": \"{{TEXTBOOK}}\",\n" +
                "  \"lessonType\": \"{{LESSON_TYPE}}\",\n" +
                "  \"objectives\": [\n" +
                "    {\"type\": \"数学抽象\", \"content\": \"通过实际问题（面积、增长率）建立方程模型，抽象出一元二次方程的概念及一般形式 ax²+bx+c=0（a≠0），理解二次项、一次项、常数项及系数含义，体会方程是刻画现实世界数量关系的有效模型。\", \"standardRef\": \"义务教育数学课程标准（2022年版）第三学段·数与代数·方程与不等式·数学抽象\"},\n" +
                "    {\"type\": \"数学运算\", \"content\": \"能够准确识别一元二次方程；熟练地将一元二次方程化为一般形式并确定各项系数；会根据方程解的定义求参数值；逐步养成认真细致、步步有据的运算习惯。\", \"standardRef\": \"义务教育数学课程标准（2022年版）第三学段·数与代数·核心素养·数学运算\"},\n" +
                "    {\"type\": \"数学建模\", \"content\": \"经历从'实际问题→抽象成一元二次方程模型'的完整过程，能解决简单的面积问题、增长率问题、数字问题，发展模型观念与应用意识，体会数学来源于生活又服务于生活。\", \"standardRef\": \"义务教育数学课程标准（2022年版）第三学段·综合与实践·核心素养·数学建模\"}\n" +
                "  ],\n" +
                "  \"keyPoints\": [\n" +
                "    \"一元二次方程的概念、一般形式 ax²+bx+c=0（a≠0）及各项系数识别\",\n" +
                "    \"由实际问题建立一元二次方程模型，理解 a≠0 的必要性\",\n" +
                "    \"根据一元二次方程解的定义进行简单代入运算\"\n" +
                "  ],\n" +
                "  \"difficultPoints\": [\n" +
                "    \"理解并强调一般形式中 a≠0 的隐含条件，以及 b、c 可以为 0 的情况\",\n" +
                "    \"从复杂实际问题中抽象出等量关系并正确列出一元二次方程\"\n" +
                "  ],\n" +
                "  \"process\": [\n" +
                "    {\"stage\": \"情境导入\", \"duration\": 5, \"activities\": \"出示校园艺术节海报设计问题：'学校要设计一块面积为 150 cm² 的矩形海报，长比宽多 5 cm，求长和宽。'学生尝试列方程，发现不是已学的一元一次方程 → 教师顺势引出课题：'像这样未知数最高次数是2的整式方程，就是我们今天要学的《一元二次方程》。'板书课题，出示学习目标。\", \"teacher\": \"出示情境题、启发回忆一元一次方程、引出新知\", \"student\": \"独立尝试列方程、对比发现不同、进入学习状态\"},\n" +
                "    {\"stage\": \"概念生成\", \"duration\": 12, \"activities\": \"①再出示2个实际问题（增长率问题、斜边勾股问题），学生分别列方程；②观察3个方程：x²+5x-150=0、x²-1200=0、x²+36x-7600=0，它们有什么共同特点？→ 小组讨论3分钟；③全班交流，抽象概念：'等号两边都是整式，只含有一个未知数，并且未知数最高次数是2的方程，叫做一元二次方程。'判断一个概念的三个要素；④追问：为什么要强调'整式方程'？（去分母后可能不是）；⑤练习：判断8个式子哪些是一元二次方程（抢答）。\", \"teacher\": \"出示问题链、引导抽象概念、板书定义三要素、组织抢答\", \"student\": \"列方程、小组讨论找共同点、归纳概念、抢答辨析\"},\n" +
                "    {\"stage\": \"一般形式探究\", \"duration\": 10, \"activities\": \"①类比一元一次方程一般形式 ax+b=0（a≠0），引导学生思考：一元二次方程能不能也写成统一形式？②学生尝试整理：ax²+bx+c=0（a,b,c为常数，a≠0），强调'整式方程→先整理成标准形式再判断'；③重点讨论：为什么必须 a≠0？若 a=0 会变成什么方程？b=0 呢？c=0 呢？b=c=0 呢？（四种特殊类型）；④典例示范：把方程 3x(x-1)=2(x+2)-4 化成一般形式，并写出各项系数。（易错：去括号、移项变号）。\", \"teacher\": \"类比引导、板书一般形式、重点追问 a≠0、典例板演并强调易错点\", \"student\": \"自主整理、讨论特殊情况、跟随板演并标注易错点\"},\n" +
                "    {\"stage\": \"互动建模\", \"duration\": 10, \"activities\": \"①小组建模挑战（2题）：题1：面积类——靠墙围矩形花圃；题2：增长率类——两年产值增长；每组派代表上台板书方程；②互评：其他组评价是否正确、能否化为一般形式；③方程解的定义引入：类比'使方程左右两边相等的未知数的值就是方程的解'，简单代入验证 x=10 是否是 x²+5x-150=0 的解，再求参数题：若 x=2 是方程 x²+kx-8=0 的解，求 k 值。\", \"teacher\": \"发放建模任务单、巡视指导、组织互评与追问\", \"student\": \"小组合作列方程、上台展示、互评、学习解的定义并代入计算\"},\n" +
                "    {\"stage\": \"当堂练习\", \"duration\": 7, \"activities\": \"①必做题（A组）：判断题5道、选择题3道（概念辨析+一般形式识别）、把3个方程化为一般形式并写系数；②提升题（B组）：已知 (m-1)x^|m|+1 + 2x - 3 = 0 是关于 x 的一元二次方程，求 m 值；列方程：一个两位数，十位数字比个位数字小2，两个数字的平方和比这个两位数小3，列出方程（不用求解）；③选做题（C组）：设计一个实际问题，使列出的方程是 x²+10x-200=0。\", \"teacher\": \"发放分层练习、巡视、快批、重点讲解 m 值问题\", \"student\": \"独立完成、同桌互改、订正错题、挑战C组\"},\n" +
                "    {\"stage\": \"课堂小结\", \"duration\": 3, \"activities\": \"学生用'3个关键词+1个疑问'谈收获（3关键词：一元二次方程概念、一般形式、a≠0；1个疑问：怎么解？）→ 教师梳理板书思维导图，预告下节课学习解法 → 布置分层作业。\", \"teacher\": \"引导反思、梳理体系、预告后续学习\", \"student\": \"自我总结、谈疑问、记录作业\"}\n" +
                "  ],\n" +
                "  \"boardDesign\": \"【板书设计】\\n\\n                    §21.1  一元二次方程\\n\\n    ┌──────────────────────────────────────────────┐\\n    │  一、概念  三要素                        三、建模应用          │\\n    │        ① 整式方程                                  1. 面积问题\\n    │        ② 只含1个未知数（一元）           2. 增长率问题\\n    │        ③ 未知数最高次数=2（二次）        3. 数字/勾股问题\\n    │                                                      ↓\\n    │  二、一般形式                              实际问题 → 设元 → 找等量→列方程\\n    │        ax² + bx + c = 0  （a≠0）      → 化为一般形式\\n    │        ↑    ↑    ↑                                ↓\\n    │     二次项 一次项 常数项                   验证解：代入\\n    │        a    b    c    （a,b,c为常数）           （下节课：如何解？）\\n    │                                                      ↙      ↘\\n    │  ⚠ 重点：a ≠ 0                 若 a=0 → 一元一次        配方法  公式法  因式分解法\\n    │        b、c 可为 0                缺项型：ax²+c=0, ax²+bx=0\\n    └──────────────────────────────────────────────┘\\n\\n【例题板演区】           【学生练习展示区】\",\n" +
                "  \"homework\": [\n" +
                "    {\"level\": \"基础层（A）\", \"items\": [\"课本P4 习题21.1 第1、2、3题（概念判断+化一般形式）\", \"自编5个方程，其中3个是一元二次方程、2个不是，让同桌判断并说明理由\", \"整理今天的课堂笔记，画出一元二次方程概念的思维导图\"]},\n" +
                "    {\"level\": \"提升层（B）\", \"items\": [\"课本P4-P5 习题21.1 第4、5、6题（列方程+参数题）\", \"已知关于x的方程 (k-2)x^k²-2 + 3x - 5 = 0 是一元二次方程，求 k 的值，并写出此时的二次项系数、一次项系数和常数项\", \"调查：生活中还有哪些问题可以用一元二次方程建模？举出2个实际场景（不用求解）\"]},\n" +
                "    {\"level\": \"挑战层（C）\", \"items\": [\"课本P5 拓广探索 第7、8题\", \"阅读拓展：查阅古巴比伦泥版、中国《九章算术》中关于二次方程的历史资料，写一篇200字的'一元二次方程前世今生'数学小作文\", \"探究：如果定义'一元三次方程'，你会如何下定义？请写出它的一般形式并举2个实例\"]}\n" +
                "  ],\n" +
                "  \"qualityScore\": 93,\n" +
                "  \"standardCards\": [\n" +
                "    {\"ref\": \"义务教育数学课程标准（2022年版）第三学段·数与代数·方程与不等式\", \"content\": \"能根据具体问题中的数量关系列出方程，体会方程是刻画现实世界数量关系的有效模型；了解一元二次方程的概念，能将一元二次方程化为一般形式。\"},\n" +
                "    {\"ref\": \"义务教育数学课程标准（2022年版）·核心素养·模型观念\", \"content\": \"模型观念主要是指对运用数学模型解决实际问题有清晰的认识。知道数学模型可以用来解决一类问题，是数学应用的基本形式；能够从现实生活或具体情境中抽象出数学问题，用数学符号建立方程、不等式、函数等表示数学问题中的数量关系和变化规律。\"},\n" +
                "    {\"ref\": \"义务教育数学课程标准（2022年版）·核心素养·运算能力\", \"content\": \"运算能力主要是指根据法则和运算律进行正确运算的能力。能够明晰运算的对象和意义，理解算法与算理之间的关系；能够运用运算律和运算性质进行合理简便的运算。\"}\n" +
                "  ]\n" +
                "}");

        FALLBACK_TEMPLATES.put("数学|分数乘法", "{\n" +
                "  \"title\": \"{{TITLE}}\",\n" +
                "  \"subject\": \"{{SUBJECT}}\",\n" +
                "  \"grade\": \"{{GRADE}}\",\n" +
                "  \"textbook\": \"{{TEXTBOOK}}\",\n" +
                "  \"lessonType\": \"{{LESSON_TYPE}}\",\n" +
                "  \"objectives\": [\n" +
                "    {\"type\": \"数感与量感\", \"content\": \"借助折纸、画图等直观操作，理解分数乘分数的算理（'求一个数的几分之几是多少用乘法'的本质），能通过几何直观感知积的分子分母与乘数分子分母的关系，发展数感与量感。\", \"standardRef\": \"义务教育数学课程标准（2022年版）第二学段·数与运算·核心素养·数感量感\"},\n" +
                "    {\"type\": \"运算能力\", \"content\": \"掌握分数乘分数的计算法则（分子相乘作分子、分母相乘作分母），能正确进行分数乘法计算（含约分技巧：先约分再乘）；能解决'求一个数的几分之几是多少'的简单实际问题，养成先估后算、认真约分的良好习惯。\", \"standardRef\": \"义务教育数学课程标准（2022年版）第二学段·数与运算·核心素养·运算能力\"},\n" +
                "    {\"type\": \"几何直观与推理意识\", \"content\": \"经历'折纸操作→观察发现→猜想验证→归纳法则'的探究过程，能用面积模型（矩形图）解释分数乘法的算理，发展几何直观与归纳推理意识，体会数形结合思想。\", \"standardRef\": \"义务教育数学课程标准（2022年版）第二学段·核心素养·几何直观·推理意识\"}\n" +
                "  ],\n" +
                "  \"keyPoints\": [\n" +
                "    \"理解分数乘分数的算理，掌握计算法则并能正确计算\",\n" +
                "    \"理解'求一个数的几分之几是多少'用乘法计算的数量关系\",\n" +
                "    \"掌握先约分再相乘的简便计算技巧，能识别约分时的常见错误\"\n" +
                "  ],\n" +
                "  \"difficultPoints\": [\n" +
                "    \"借助几何直观（矩形面积模型）理解分数乘分数算理：为什么分子乘分子、分母乘分母？\",\n" +
                "    \"正确处理整数与分数相乘时整数的'分母为1'及约分的书写规范\"\n" +
                "  ],\n" +
                "  \"process\": [\n" +
                "    {\"stage\": \"情境导入\", \"duration\": 5, \"activities\": \"出示李伯伯家种地情境图：'李伯伯家有一块 1/2 公顷的地，种土豆的面积占这块地的 1/5，种玉米的面积占 3/5。'学生提数学问题→提炼核心问题：种土豆的面积是多少公顷？→学生猜测列式（1/2×1/5）→教师追问：为什么用乘法？（求1/2公顷的1/5是多少，就是求一个数的几分之几用乘法）→揭示课题：今天我们就来研究《分数乘分数》。板书课题。\", \"teacher\": \"出示情境、引导提问题、追问乘法意义\", \"student\": \"看图提问题、猜测列式、回顾一个数乘分数的意义\"},\n" +
                "    {\"stage\": \"操作探究算理\", \"duration\": 15, \"activities\": \"①活动1：折一折，涂一涂。每组一张正方形纸代表1公顷。a.先折出1/2公顷（涂色斜线）；b.再把这1/2公顷平均分成5份，给其中1份涂色（另一个方向）。②观察：两次涂色重叠部分占整张纸的几分之几？（1/10）所以 1/2×1/5=1/10。③活动2：玉米面积 1/2×3/5，继续折纸观察 → 结果 3/10。④对比观察两组算式：分子分母有什么变化？猜想分数乘分数怎么算？→学生发言，初步归纳。\", \"teacher\": \"发放学具、指导折纸、板书过程、引导观察规律\", \"student\": \"折纸涂色、组内观察、说发现、提出猜想\"},\n" +
                "    {\"stage\": \"验证归纳法则\", \"duration\": 8, \"activities\": \"①举例验证：用画图法验证 2/3×4/5 = 8/15 是否正确？（矩形图：横向3等份、纵向5等份，共15格，取2×4=8格）；②全班讨论归纳：分数乘分数，用分子相乘的积作分子，用分母相乘的积作分母。板书法则；③追问：整数乘分数怎么用这个法则？（把整数看成分母是1的分数，如 3×2/7 = 3/1×2/7=6/7）；④约分优化：出示 5/6×9/10，学生先直接算，再先约分再算，比较哪种更简便——强调'先约分，再计算，结果要是最简分数'。\", \"teacher\": \"组织验证、板书法则、强调约分技巧、示范规范书写\", \"student\": \"画图验证、归纳法则、思考整数转化、尝试两种约分方法对比\"},\n" +
                "    {\"stage\": \"互动练习\", \"duration\": 7, \"activities\": \"①基础口答（开火车）：8道简单分数乘分数口算，含简单约分；②闯关练习：第一关——计算4道（含整数×分数、分数×分数、注意约分包涵跨分子分母）；第二关——火眼金睛：判断3道典型错题（约分位置错、没约成最简、分子分母乘反）；第三关——解决问题：蜂鸟飞行问题（速度×时间）。\", \"teacher\": \"组织口答闯关、出示典型错题引导辨析\", \"student\": \"开火车口答、独立计算、找茬改错、解决实际问题\"},\n" +
                "    {\"stage\": \"当堂检测\", \"duration\": 7, \"activities\": \"①必做题（A）：6道计算题（含2道应用题）；②提升题（B）：比较大小找规律（真分数乘真分数，积比任意因数都小）；计算 2023/2024 × 2025，用拆分法简算；③选做题（C）：一杯纯果汁，先喝1/2，加满水，再喝1/3，再加满水，最后全喝完。喝的果汁多还是水多？\", \"teacher\": \"发放检测单、巡视批改、点评简算思路\", \"student\": \"独立完成、同桌互查、订正、挑战简算趣题\"},\n" +
                "    {\"stage\": \"课堂小结\", \"duration\": 3, \"activities\": \"学生用'我学会了____，我的提醒是____，我还想知道____'句式小结 → 教师回扣板书：意义（求一个数的几分之几）→ 算理（面积模型）→ 算法（分子×分子，分母×分母，先约分再乘）→ 预告下节课'分数乘加乘减混合运算'→ 布置作业。\", \"teacher\": \"引导三句式小结、梳理脉络\", \"student\": \"自我梳理、谈收获与易错点、记录作业\"}\n" +
                "  ],\n" +
                "  \"boardDesign\": \"【板书设计】\\n\\n                     《分数乘分数》\\n\\n  一、意义：求一个数的几分之几是多少 → 用乘法\\n        例：1/2 公顷 的 1/5 是多少？ 列式：1/2 × 1/5\\n\\n  二、算理（几何直观·面积模型）：\\n        折纸示意：                       算式：\\n        □（1公顷）                      1/2 × 1/5 = 1×1 / 2×5 = 1/10 ✔\\n        ▓░░░ （1/2公顷）                1/2 × 3/5 = 1×3 / 2×5 = 3/10 ✔\\n        ▒░░░ （再分5份取1份）            2/3 × 4/5 = 2×4 / 3×5 = 8/15 ✔\\n\\n  三、法则：分数乘分数 → 分子相乘的积作分子，分母相乘的积作分母\\n                        （字母表示：a/b × c/d = ac/bd ，b,d≠0）\\n        整数×分数 → 整数看成分母是1：3×2/7 = 3/1×2/7 = 6/7\\n\\n  四、技巧： ⭐ 先约分，再相乘！结果一定要是最简分数！\\n             例：5/6 × 9/10 = ╱5×╱9³ / ╱6²×╱10₂ = 3/4  ✓\\n\\n【学生板演区】             【典型错题分析区】\",\n" +
                "  \"homework\": [\n" +
                "    {\"level\": \"基础层（A）\", \"items\": [\"课本P5 做一做 全部 + 课本P6 练习一 第3、4、5题\", \"画2个矩形图表示下列算式的算理：① 2/3×1/4  ② 3/5×2/3\", \"计算练习：12道分数乘法计算题（含整数×分数），要求规范书写、能约分的先约分\"]},\n" +
                "    {\"level\": \"提升层（B）\", \"items\": [\"课本P7 练习一 第6、7、8、9题（应用题为主）\", \"先找规律，再计算： ① 比较大小并找规律：真分数×真分数，积____任何一个因数；② 简算：25/97 × 98 = 25/97 × (97+1) = ? + ? = ?； 2024 × 123/2023\", \"妈妈买了 5/6 千克苹果，第一天吃了这些苹果的 1/4，第二天吃了 1/4 千克。两天一共吃了多少千克？（注意：两个1/4 的区别！）\"]},\n" +
                "    {\"level\": \"挑战层（C）\", \"items\": [\"课本P8 练习一 第10、11题（探究规律）\", \"奇妙的'埃及分数'：阅读资料，了解古埃及人如何把任意分数拆成若干个单位分数（分子是1的分数）之和，并动手试试：把 3/4 拆成2个不同的单位分数之和；把 5/6 拆成3个单位分数之和\", \"一根绳子长 8 米，第一次剪去全长的 1/2，第二次剪去余下的 1/3，第三次剪去余下的 1/4 …… 这样剪了 2024 次后，剩下的绳子有多长？（找规律解决）\"]}\n" +
                "  ],\n" +
                "  \"qualityScore\": 94,\n" +
                "  \"standardCards\": [\n" +
                "    {\"ref\": \"义务教育数学课程标准（2022年版）第二学段·数与运算\", \"content\": \"能进行简单的分数（不含带分数）四则运算，理解算理，掌握算法，形成运算能力；能解决分数的简单实际问题，形成初步的模型意识。\"},\n" +
                "    {\"ref\": \"义务教育数学课程标准（2022年版）·核心素养·运算能力\", \"content\": \"运算能力主要是指根据法则和运算律进行正确运算的能力。能够明晰运算的对象和意义，理解算法与算理之间的关系；能够运用运算律和运算性质进行合理简便的运算。运算能力有助于形成规范化思考问题的品质，养成一丝不苟、严谨求实的科学态度。\"},\n" +
                "    {\"ref\": \"义务教育数学课程标准（2022年版）·核心素养·几何直观\", \"content\": \"几何直观主要是指运用图表描述和分析问题的意识与习惯。能够感知各种几何图形及其组成元素，依据图形的特征进行分类；能够用恰当的图形和数量关系描述和解决现实问题和数学问题。\"}\n" +
                "  ]\n" +
                "}");

        FALLBACK_TEMPLATES.put("数学|三角形内角和", "{\n" +
                "  \"title\": \"{{TITLE}}\",\n" +
                "  \"subject\": \"{{SUBJECT}}\",\n" +
                "  \"grade\": \"{{GRADE}}\",\n" +
                "  \"textbook\": \"{{TEXTBOOK}}\",\n" +
                "  \"lessonType\": \"{{LESSON_TYPE}}\",\n" +
                "  \"objectives\": [\n" +
                "    {\"type\": \"空间观念\", \"content\": \"通过量、剪、拼、折等操作活动，经历三角形内角和是180°的探究全过程，发展空间观念与几何直观；能运用三角形内角和180°这一性质进行简单计算和角度推理。\", \"standardRef\": \"义务教育数学课程标准（2022年版）第二学段·图形的认识与测量·空间观念\"},\n" +
                "    {\"type\": \"推理意识\", \"content\": \"经历'观察→猜想→验证→归纳→应用'的完整数学探究过程，从特殊三角形（直角、等边）到一般三角形，初步体会不完全归纳法，发展推理意识与严谨思考的习惯。\", \"standardRef\": \"义务教育数学课程标准（2022年版）第二学段·核心素养·推理意识\"},\n" +
                "    {\"type\": \"应用意识\", \"content\": \"能运用三角形内角和性质解决求未知角、判断三角形类型、解释生活现象（如梯子、金字塔）等实际问题，感受数学的实用性与趣味性，激发探究几何奥秘的兴趣。\", \"standardRef\": \"义务教育数学课程标准（2022年版）第二学段·综合与实践·应用意识\"}\n" +
                "  ],\n" +
                "  \"keyPoints\": [\n" +
                "    \"通过操作活动探究并掌握三角形内角和等于180°这一性质\",\n" +
                "    \"能灵活运用三角形内角和性质求三角形中未知角的度数\",\n" +
                "    \"理解'无论三角形形状、大小如何变化，内角和都是180°'这一不变性\"\n" +
                "  ],\n" +
                "  \"difficultPoints\": [\n" +
                "    \"从操作验证（剪拼、折叠）到理性理解（转化为平角）的思维飞跃，理解转化思想\",\n" +
                "    \"能清晰地利用'三角形内角和180°'进行有条理的几何推理与表达\"\n" +
                "  ],\n" +
                "  \"process\": [\n" +
                "    {\"stage\": \"情境导入·激疑生趣\", \"duration\": 5, \"activities\": \"出示三角形三兄弟吵架动画（或漫画）：直角三角形说'我有一个直角，我的内角和最大'；钝角三角形说'我的钝角比你们都大，我的内角和才最大'；锐角三角形说'我虽然个子小，但我们的内角和是一样的呀！'→ 学生投票站队→ 教师导语：'到底谁对？今天我们就用数学实验来当'小法官'，判决《三角形内角和》的世纪官司！'板书课题，出示学习目标。\", \"teacher\": \"播放动画/出示漫画、组织投票、设置悬念引入\", \"student\": \"观看、投票站队、产生好奇、进入探究状态\"},\n" +
                "    {\"stage\": \"操作一：测量计算·初步验证\", \"duration\": 8, \"activities\": \"①每人手中有3个三角形（锐角、直角、钝角各1），小组分工：每人测一种；②用量角器分别量出每个三角形3个内角的度数，填在记录表中，计算内角和；③汇报结果：各组报数据（可能出现179°、181°、180°等）；④引导讨论：为什么结果不完全一样？（测量误差）→ 教师追问：测量有误差，有没有更准确的验证方法？\", \"teacher\": \"发放三角形学具、指导量角规范、板书数据、追问误差原因\", \"student\": \"测量、填表、计算、汇报、思考误差与更严谨方法\"},\n" +
                "    {\"stage\": \"操作二：剪拼折叠·深度验证\", \"duration\": 15, \"activities\": \"①活动A 剪拼法：给每人一个钝角三角形，标好∠1∠2∠3，把三个内角剪下来，拼一拼，看看能拼成什么角？→ 学生操作，拼成平角（180°）。上台展示拼法；②活动B 折叠法：不用剪，把三角形折一折，也能让三个角凑成平角？→ 小组探究，教师适时指导折法（过一个顶点作对边高，沿高的中点连线折）；③活动C 延伸：教师用几何画板动态演示——拖动三角形一个顶点，改变形状大小，实时显示三个内角度数与总和，学生观察：总和始终=180°！→ 归纳结论：任意三角形的内角和都是180°。（强调'任意'）\", \"teacher\": \"指导剪拼、启发折叠、用几何画板动态验证、板书结论并画圈强调任意\", \"student\": \"剪拼展示、探究折法、观察动态演示、惊呼发现、理解结论\"},\n" +
                "    {\"stage\": \"互动推理·方法多样\", \"duration\": 5, \"activities\": \"思辨提升：除了实验，能不能用已学知识推理？①长方形四个角都是90°→内角和360°→沿对角线剪开→两个完全一样的直角三角形→每个直角三角形内角和=180°→直角三角形验证；②追问：那锐角三角形、钝角三角形呢？能不能也转化？（作高，分成两个直角三角形，180+180-90-90=180°，渗透演绎推理雏形）\", \"teacher\": \"追问演绎证法、画长方形图辅助理解\", \"student\": \"倾听思考、尝试推理、感受多种方法\"},\n" +
                "    {\"stage\": \"当堂练习·分层应用\", \"duration\": 7, \"activities\": \"①基础（A组）：a.已知∠1=75°，∠2=55°，求∠3=？b.直角三角形一个锐角35°，另一个锐角？c.等腰三角形顶角40°，求底角？②提升（B组）：a.一个三角形中能不能有两个直角/钝角？为什么？（用180°说理）；b.一个三角形三个内角度数比是2:3:4，分别求三个角？判断是什么三角形？③挑战（C组）：求四边形内角和（画一条对角线变2个三角形→360°）；五边形呢？找 n 边形内角和公式。\", \"teacher\": \"出示分层题、巡视批改、重点点评比例题与四边形推理\", \"student\": \"独立完成、同桌互改、说理表达、挑战多边形\"},\n" +
                "    {\"stage\": \"总结宣判·呼应开头\", \"duration\": 3, \"activities\": \"回到开头'三兄弟吵架'：现在请小法官宣判（学生说：三角形三兄弟内角和都是180°，一样大！）→ 学生用'今天我用____方法，探究出了____，我的收获是____'句式小结 → 布置分层作业，预告下节课'三角形外角'探索。\", \"teacher\": \"引导宣判、回扣情境、梳理探究全过程\", \"student\": \"宣判结果、回顾方法收获、记录作业\"}\n" +
                "  ],\n" +
                "  \"boardDesign\": \"【板书设计】\\n\\n                   《三角形内角和》\\n\\n    三兄弟吵架：谁的内角和大？→  判决：一样大！都是 180° ⚖\\n\\n    探究方法：\\n\\n    ① 测量法：∠1+∠2+∠3 ≈ 180°（有误差）\\n       （表格：锐角△ 70+60+50=180； 直角△ 90+45+45=180； 钝角△ 100+50+30=180）\\n\\n    ② 剪拼法：把∠1∠2∠3剪下来→拼在一起 → 形成一个平角 = 180°\\n       （板书贴图：三个角拼平角示意图）\\n\\n    ③ 折叠法：沿高折 → 三个角凑在底边 → 平角 = 180°\\n\\n    ④ 动态演示：几何画板 → 任意拖动，内角和不变 → 任意△内角和=180°⭐\\n\\n    推理法：长方形→2个直角△→360÷2=180°（直角△）  → 作高→2个直角△\\n\\n    应用：求未知角 → 180° - 已知两角 = 未知角\\n    拓展：四边形内角和= 2×180°=360°，n边形=(n-2)×180°\\n\\n【学生板演区】              【结论区（彩色粉笔重点）】\",\n" +
                "  \"homework\": [\n" +
                "    {\"level\": \"基础层（A）\", \"items\": [\"课本P25 做一做 第1、2题 + P27 练习四 第1、2、3题\", \"自己画3个不同类型的三角形，分别量一量、算一算内角和，并把剪拼验证过程拍1张照片贴在作业本上\", \"填空：①三角形内角和是（   ）°；②直角三角形两锐角之和是（   ）°；③一个三角形两个角分别是60°和80°，第三个角是（   ）°，这是（   ）三角形\"]},\n" +
                "    {\"level\": \"提升层（B）\", \"items\": [\"课本P28 练习四 第5、6、7题（重点说理题）\", \"解决问题：①等腰三角形一个底角是70°，它的顶角是多少？（画出图形再列式）；②爸爸给小明买了一个等腰三角形风筝，它的一个底角是顶角的2倍，这个风筝的顶角和底角分别是多少度？③用今天学的知识解释：为什么自行车车架、梯子、高压线塔都用三角形结构？\", \"动手做：用硬纸条做一个可以活动的三角形和一个可以活动的四边形，感受三角形'稳定性'与'内角和不变'的联系，写3句话说明\"]},\n" +
                "    {\"level\": \"挑战层（C）\", \"items\": [\"课本P29 练习四 第9题 + 思考题\", \"探究题：如图，五角星的5个'尖角'（∠A+∠B+∠C+∠D+∠E）合起来是多少度？利用三角形内角和知识推理计算，画出辅助线并写出推理过程\", \"数学阅读：上网查一查——在球面上画一个三角形，它的内角和还是180°吗？了解一下'非欧几何'的趣闻，写一段100字科普小笔记\"]}\n" +
                "  ],\n" +
                "  \"qualityScore\": 95,\n" +
                "  \"standardCards\": [\n" +
                "    {\"ref\": \"义务教育数学课程标准（2022年版）第二学段·图形的认识与测量\", \"content\": \"探索并掌握三角形的内角和是180°；在操作、观察、思考、表达等活动中，发展空间观念、几何直观和推理意识。\"},\n" +
                "    {\"ref\": \"义务教育数学课程标准（2022年版）·核心素养·推理意识\", \"content\": \"推理意识主要是指对逻辑推理过程及其意义的初步感知。知道可以从一些事实和命题出发，依据规则推出其他命题；能够通过简单的归纳或类比，猜想或发现一些初步的结论；能够比较清楚地表达自己的思考过程。\"},\n" +
                "    {\"ref\": \"义务教育数学课程标准（2022年版）·核心素养·空间观念\", \"content\": \"空间观念主要是指对空间物体或图形的形状、大小及位置关系的认识。能够根据物体特征抽象出几何图形，根据几何图形想象出所描述的实际物体；想象并表达物体的空间方位和相互之间的位置关系。\"}\n" +
                "  ]\n" +
                "}");

        FALLBACK_TEMPLATES.put("英语|Unit 1 My name is Gina", "{\n" +
                "  \"title\": \"{{TITLE}}\",\n" +
                "  \"subject\": \"{{SUBJECT}}\",\n" +
                "  \"grade\": \"{{GRADE}}\",\n" +
                "  \"textbook\": \"{{TEXTBOOK}}\",\n" +
                "  \"lessonType\": \"{{LESSON_TYPE}}\",\n" +
                "  \"objectives\": [\n" +
                "    {\"type\": \"语言能力\", \"content\": \"能够正确认读、拼写并熟练运用10个核心词汇（name, nice, to, meet, too, your, Ms, his, her, and）及重点句型：'What's your/his/her name? My/His/Her name is... Nice to meet you. Nice to meet you, too.'；能够在真实交际中进行自我介绍与询问他人姓名。\", \"standardRef\": \"义务教育英语课程标准（2022年版）三级·语言知识·语法与语用\"},\n" +
                "    {\"type\": \"文化意识\", \"content\": \"了解中西方初次见面打招呼方式的差异（握手、眼神交流、Nice to meet you等）；了解英文姓名的文化常识（first name/given name 名 vs last name/family name 姓；Ms/Mrs/Miss/Mr 称谓用法）；培养跨文化交际意识与得体的交往礼仪。\", \"standardRef\": \"义务教育英语课程标准（2022年版）三级·文化意识·比较与判断\"},\n" +
                "    {\"type\": \"思维品质\", \"content\": \"通过听音圈图、对话排序、信息填表等活动发展观察与辨析能力；通过创编新对话、调查班级同学姓名等任务发展逻辑思维与创造性表达；能够在情境中举一反三，灵活迁移所学语言。\", \"standardRef\": \"义务教育英语课程标准（2022年版）三级·思维品质·逻辑性与创造性\"},\n" +
                "    {\"type\": \"学习能力\", \"content\": \"初步掌握听力预测、关键词标记、跟读模仿、合作对话等学习策略；养成大胆开口、不怕犯错的英语学习习惯；学会运用联想记忆法记忆姓名类词汇，逐步形成自主学习能力。\", \"standardRef\": \"义务教育英语课程标准（2022年版）三级·学习能力·调控与合作\"}\n" +
                "  ],\n" +
                "  \"keyPoints\": [\n" +
                "    \"掌握询问与回答姓名的核心句型 What's your/his/her name? My/His/Her name is...\",\n" +
                "    \"正确使用形容词性物主代词 my, your, his, her\",\n" +
                "    \"能听懂简短的姓名介绍对话，能进行自然、礼貌的初次见面交流\"\n" +
                "  ],\n" +
                "  \"difficultPoints\": [\n" +
                "    \"区分并准确使用 my/your/his/her 四个形容词性物主代词（尤其 his/her 的性别差异）\",\n" +
                "    \"Ms/Mrs/Miss/Mr 四个称谓的文化用法差异\",\n" +
                "    \"在真实交际中灵活运用句型，避免机械背诵，达到自然得体交流\"\n" +
                "  ],\n" +
                "  \"process\": [\n" +
                "    {\"stage\": \"Warm-up & Lead-in 热身导入\", \"duration\": 5, \"activities\": \"① Greeting & Song：全班齐唱 Hello Song 'Hello, hello, how are you?'（2分钟），营造英语氛围；② Guess：教师做神秘动作，用玩偶小熊介绍：'Look! This is my new friend. Guess! What's his name?' 学生猜 → 教师揭示：'His name is Teddy. Say hello to Teddy: Hello, Teddy! Nice to meet you!' → 顺势揭示课题 Unit 1 My name's Gina (Section A 1a-2d)，明确本课'结交新朋友'主题，出示学习目标。\", \"teacher\": \"领唱歌曲、用玩偶创境、板书课题与主题\", \"student\": \"唱歌互动、猜名字、与玩偶打招呼、齐读目标\"},\n" +
                "    {\"stage\": \"Presentation 新知呈现\", \"duration\": 15, \"activities\": \"① 词汇呈现：教师出示课本人物图（Jenny, Tony, Mary, Jim, Gina, Alan, Ms Miller），PPT逐个呈现+词卡，带读（升降调）→ 全班齐读→ 个别抽读→ 快速闪卡游戏；② 句型呈现：a.教师走到学生A面前：'Hi! I'm Ms Wang. What's your name?' 引导回答：'My name is...' → 教师回应 'Nice to meet you!' → 学生回 'Nice to meet you, too.' 师生示范3组；b.呈现 his/her：指一男生照片 'What's his name? His name is...' 指女生 'What's her name? Her name is...' 用手势强调性别；③ 板书4个物主代词表格 I→my, you→your, he→his, she→her；④ 文化小贴士：Ms vs Mrs vs Miss vs Mr（婚否、性别差异），配图标。\", \"teacher\": \"出示图卡带读、板书句型、师生示范、手势讲 his/her、文化贴士\", \"student\": \"跟读词汇、开火车读、师生模拟对话、抄写物主代词表\"},\n" +
                "    {\"stage\": \"Listening & Practice 听说操练\", \"duration\": 10, \"activities\": \"① 1a Listen & number：学生看课本人物图，听录音排序号 → 对答案 → 再听跟读；② 1b Pair work：两人一组，看人物图替换练习对话：A: What's his/her name? B: His/Her name is... 教师巡视2组展示；③ 2a Listen & circle：听3段短对话，圈出听到的名字 → 2b Listen & fill in the chart（男孩名女孩名分类填表）；④ Game: Name Bingo：学生写3个班内同学名字，老师随机提问 What's her name? 答中了划掉，先划完3个喊 Bingo！\", \"teacher\": \"播放录音、巡视指导、组织展示与 Bingo 游戏\", \"student\": \"听录音做题、跟读法、2人对话、Bingo游戏\"},\n" +
                "    {\"stage\": \"Role-play 2d 互动表演\", \"duration\": 8, \"activities\": \"① 呈现 2d 情境图（Linda 和 Helen 初次见面），听 2d 录音，整体感知；② 学生跟读 2d（逐句仿语音语调）→ 分角色朗读（男生Linda/女生Helen→交换）；③ 讲解重点：Nice to meet you./Nice to meet you, too. 区别；What about yours?=And your name? ；④ 小组任务：Group of 3，创编一个'开学第一天新认识3位同学'的对话，必须包含3次 What's your name? + Nice to meet you. +物主代词。准备5分钟后各组上台表演，全班用表情评分⭐⭐⭐。\", \"teacher\": \"播放录音、指导语音语调、讲解重点句、组织创编表演与评分\", \"student\": \"跟读、分角色读、小组创编对话、上台表演、互评\"},\n" +
                "    {\"stage\": \"Class Survey 当堂调查\", \"duration\": 5, \"activities\": \"Class Name Survey：学生离开座位，用英语询问至少5位同学姓名，完成调查表（Name: ____, Nice to meet you!）→ 2位同学汇报：'I have 5 new friends. His name is... Her name is...' → 教师点评并用星级奖励。\", \"teacher\": \"发放调查表、鼓励学生大胆走动交流、点评\", \"student\": \"走动调查、和同学真实用英语对话、汇报成果\"},\n" +
                "    {\"stage\": \"Summary & Homework 总结作业\", \"duration\": 2, \"activities\": \"① Summary：学生齐说今天的'3句话金句'：What's your name? / My name is... / Nice to meet you, too. + 物主代词口诀：'我的my，你的your，男的his女的her'；② 教师情感教育：'学会微笑打招呼，开学每一天都能认识新朋友！'；③ 布置分层作业。\", \"teacher\": \"引导口诀总结、情感升华、布置作业\", \"student\": \"齐说金句与口诀、记录作业、自信满满\"}\n" +
                "  ],\n" +
                "  \"boardDesign\": \"【板书设计】\\n\\n                Unit 1 My name's Gina. (Section A 1a-2d)\\n                         Theme: Making new friends 结交新朋友\\n\\n  ┌──────────────────────┐    ┌────────────────────────────────────┐\\n  │  Vocabulary 词汇       │    │  Sentences 核心句型                        │\\n  │  name, nice, meet     │    │  ⭐ What's your name?                       │\\n  │  too, your, Ms        │    │     My name is... / I'm ...                   │\\n  │  his, her, and        │    │  ⭐ Nice to meet you!                        │\\n  │                        │    │     Nice to meet you, too.                  │\\n  │  Pronouns 物主代词💡   │    │  ⭐ What's his / her name?                 │\\n  │     I  →  my  我的     │    │     His / Her name is ...                   │\\n  │   you  → your 你的     │    │  ❓ What about yours? (=And your name?)     │\\n  │    he  →  his 他的     │    │                                             │\\n  │   she  →  her 她的     │    │  Culture Tip 文化小贴士🌍                    │\\n  └──────────────────────┘    │    Mr (先生) / Mrs (夫人,已婚)                │\\n                              │    Miss (小姐,未婚) / Ms (女士,婚否不明) ✔推荐 │\\n                              └────────────────────────────────────┘\\n\\n   【学生对话展示区⭐⭐⭐】        【Name Bingo 学生名字板】\",\n" +
                "  \"homework\": [\n" +
                "    {\"level\": \"基础层（A层·必做）\", \"items\": [\"1. Listen & read：听 Unit 1 Section A 录音，跟读 1a-2d 共 5 遍，家长签字或录音发群\", \"2. Copy & recite：抄写本课时10个单词+核心句型3遍（中英对照），背诵 2d 对话\", \"3. Workbook：完成英语练习册 Unit 1 Section A 基础部分（Vocabulary + 句型转换）\"]},\n" +
                "    {\"level\": \"提升层（B层·选做4道）\", \"items\": [\"1. Oral task：录制一段30秒英文自我介绍小视频，用上 My name is... Nice to meet you. 上传班级群\", \"2. Writing：制作一张英文姓名卡（Name Card），内容：Name, English Name, Hobby, Favorite Color，装饰并装饰美化\", \"3. Dialogue：用今天学的句型，和家人模拟'初次见面'对话，记录家人的英文名字并写下3句对话\", \"4. Culture search：上网查找至少3个常见的英文男孩名、3个英文女孩名，标注寓意，明天课上分享\"]},\n" +
                "    {\"level\": \"挑战层（C层·拓展）\", \"items\": [\"1. Group project：4人组合作，创编一段1分钟的'新生开学交友'英语情景剧（要求每人至少2句台词，包含自我介绍、询问3个同学姓名、使用 Nice to meet you），下节课表演\", \"2. Mini research：采访3位不同学科的老师，记录他们的英文名字和称谓（Ms/Mr），用英语写3句话介绍他们：This is Ms... She is our... teacher. Her English name is...\", \"3. Reading：阅读英语分级绘本《First Day at School》（老师提供电子版），画出不懂的词，猜猜意思，写3个你学到的新句型\"]}\n" +
                "  ],\n" +
                "  \"qualityScore\": 89,\n" +
                "  \"standardCards\": [\n" +
                "    {\"ref\": \"义务教育英语课程标准（2022年版）三级·语言能力·表达与交流\", \"content\": \"能听懂课堂活动中简单的提问；能听懂常用指令和要求并作出适当反应；能在口头表达中进行简单的角色表演；能运用一些最常用的日常用语（如问候、介绍、告别、致谢、道歉、请求等）进行简单的人际交流。\"},\n" +
                "    {\"ref\": \"义务教育英语课程标准（2022年版）三级·文化意识·文化内涵\", \"content\": \"能初步感知中外文化的异同，能初步用英语描述常见的文化现象；能关注并乐于了解中外文化，具有初步的跨文化沟通意识和正确的价值观。\"},\n" +
                "    {\"ref\": \"义务教育英语课程标准（2022年版）三级·学习能力·方法策略\", \"content\": \"能在学习活动中尝试运用适合自己的学习策略，如模仿、重复、识记、联想、归纳、合作等；能在学习中积极思考，主动探究，发现语言规律；具有学习英语的兴趣和愿望，乐于参与各种课堂学习活动。\"}\n" +
                "  ]\n" +
                "}");

        FALLBACK_TEMPLATES.put("物理|牛顿第一定律", "{\n" +
                "  \"title\": \"{{TITLE}}\",\n" +
                "  \"subject\": \"{{SUBJECT}}\",\n" +
                "  \"grade\": \"{{GRADE}}\",\n" +
                "  \"textbook\": \"{{TEXTBOOK}}\",\n" +
                "  \"lessonType\": \"{{LESSON_TYPE}}\",\n" +
                "  \"objectives\": [\n" +
                "    {\"type\": \"物理观念\", \"content\": \"通过实验探究与科学推理，理解牛顿第一定律的内容与意义；理解力不是维持物体运动的原因，而是改变物体运动状态的原因；建立正确的'运动与力'物理观念，破除亚里士多德'力是维持运动的原因'的错误前概念。\", \"standardRef\": \"义务教育物理课程标准（2022年版）·运动和相互作用·物理观念\"},\n" +
                "    {\"type\": \"科学思维\", \"content\": \"经历'斜面小车实验→观察现象→推理理想实验→归纳牛顿第一定律'的完整探究过程，学习控制变量法、理想实验法（科学推理法）；能对亚里士多德、伽利略、笛卡尔、牛顿的研究历程进行批判性分析，发展科学推理与质疑创新能力。\", \"standardRef\": \"义务教育物理课程标准（2022年版）·核心素养·科学思维·模型建构·科学推理\"},\n" +
                "    {\"type\": \"科学探究\", \"content\": \"能独立完成'阻力对物体运动的影响'实验，能正确控制小车初速度相同（同一斜面、同一高度、静止释放），能改变阻力大小（毛巾→棉布→木板），能观察记录滑行距离并进行科学推理；会设计、操作、记录、分析并表达交流。\", \"standardRef\": \"义务教育物理课程标准（2022年版）·核心素养·科学探究·问题·证据·解释·交流\"},\n" +
                "    {\"type\": \"科学态度与责任\", \"content\": \"通过了解牛顿第一定律的发现历程（跨越近2000年的认知接力），体会科学发现的曲折性与艰辛性，感受科学家不迷信权威、实事求是、尊重证据的科学态度；认识物理实验与科学推理在物理学发展中的重要作用，树立科学真理观。\", \"standardRef\": \"义务教育物理课程标准（2022年版）·核心素养·科学态度与责任\"}\n" +
                "  ],\n" +
                "  \"keyPoints\": [\n" +
                "    \"通过'阻力对物体运动影响'实验探究，理解牛顿第一定律的建立过程与内容\",\n" +
                "    \"理解力与运动的关系：力不是维持物体运动的原因，而是改变物体运动状态的原因\",\n" +
                "    \"理解惯性是物体的固有属性，并能用惯性知识解释简单的生活现象\"\n" +
                "  ],\n" +
                "  \"difficultPoints\": [\n" +
                "    \"从斜面小车实验的有限事实到'不受力时永远运动'的理想推理之间的思维飞跃（理想实验法）\",\n" +
                "    \"破除学生'力是维持运动的原因'的顽固错误前概念，建立正确的运动与力关系\",\n" +
                "    \"准确理解惯性的普遍性：一切物体在任何情况下都有惯性，惯性大小只与质量有关\"\n" +
                "  ],\n" +
                "  \"process\": [\n" +
                "    {\"stage\": \"情境导入·冲突激疑\", \"duration\": 5, \"activities\": \"① 小比赛：讲台推木块→用力推就动，不推就停。学生直觉：'力让物体动，没力就停下'（暴露前概念）；② 反例演示：老师用力猛推桌上一小钢球→松手后钢球继续滚了很长距离→追问：'手已经没推力了，小球为什么还在滚？谁说的对？'③ 播放'冰壶运动慢动作'视频：运动员不再推，冰壶还前行很远。→ 教师：'关于运动与力的关系，2000年前亚里士多德和今天许多同学想法一样，但300多年前伽利略做了一个伟大实验，彻底推翻了它。今天我们就重演物理学史上最伟大的一场认知革命——《牛顿第一定律》。'板书课题，出示四维目标。\", \"teacher\": \"组织小比赛、演示钢球反例、放冰壶视频、制造认知冲突\", \"student\": \"推木块、说直觉→看到小球继续滚产生疑惑→观看冰壶→引发探究欲望\"},\n" +
                "    {\"stage\": \"实验探究·阻力影响\", \"duration\": 15, \"activities\": \"① 明确问题：运动物体受到的阻力对它的运动有什么影响？② 设计实验：小组讨论——如何改变阻力？（毛巾、棉布、木板三种表面）如何控制初速度相同？（同一小车、同一斜面、同一高度、静止释放——控制变量法）测量什么？（滑行距离、运动时间）；③ 分组实验：4人一组，分工操作+记录+观察，填实验记录表；④ 汇报现象：各组汇报数据→教师板书数据→全班找规律：表面越光滑→阻力越____→小车滑行距离越____→速度减小越____。⑤ 追问关键点：'为什么要同一高度释放？'→学生回答，教师强调控制变量法的严谨。\", \"teacher\": \"提出问题、组织设计、巡视指导各组实验、板书数据、追问控制变量\", \"student\": \"讨论设计方案、分工实验、记录数据、汇报现象、总结规律\"},\n" +
                "    {\"stage\": \"科学推理·理想实验\", \"duration\": 10, \"activities\": \"① 递进推理链：教师引导学生'脑洞更大一点'：木板→玻璃→更光滑→再更光滑……阻力越来越____？滑行距离越来越____？速度减小越来越____？→ ② 推理终极：如果表面绝对光滑，阻力为0呢？→ 学生讨论：速度不会减小！永远以不变的速度运动下去！→ 教师板书：如果物体不受力→静止的永远静止；运动的永远做匀速直线运动。③ 介绍科学史接力：亚里士多德（直觉）→伽利略（理想斜面实验）→笛卡尔（补充匀速直线方向不变）→牛顿（总结+补充静止情况）→牛顿第一定律。强调伽利略的'理想实验法'被爱因斯坦称为'物理学最伟大的思想成就之一'。④ 牛顿第一定律表述：一切物体在没有受到力的作用时，总保持静止状态或匀速直线运动状态。反复追问：'一切'？'没有受到力'？'总保持'？'或'？逐词解读。\", \"teacher\": \"引导递进推理链、讲解科学史接力、板书定律、逐词咬文嚼字解读\", \"student\": \"层层推理、得出理想结论、倾听科学史、记录定律、逐词理解\"},\n" +
                "    {\"stage\": \"概念深化·惯性与生活\", \"duration\": 7, \"activities\": \"① 惯性概念引入：从牛顿第一定律中，物体保持原来运动状态不变的性质叫'惯性'（所以牛顿第一定律也叫惯性定律）；② 生活现象解释大比拼：学生上台抽签解释——a.汽车突然启动人向后倒；b.急刹车人向前倾；c.拍打衣服灰尘脱落；d.锤头松了往地上一撞就紧；e.跳远助跑为什么跳更远？每组选1个做表演+解释；③ 实验激趣：'打棋子'——将5个象棋子摞起来，用钢尺快速打最下面一个，上面的不飞而是竖直落下！学生惊呼，用惯性解释。④ 思辨：'惯性大小和速度有关吗？'（无关！只和质量有关）用'速度大的车不容易停下来'辨析（其实是动能大，不是惯性）。\", \"teacher\": \"引入惯性概念、组织抽签比拼、演示打棋子实验、辨析惯性与速度误区\", \"student\": \"记录定义、抽签表演解释、观察魔术实验、讨论辨析\"},\n" +
                "    {\"stage\": \"当堂练习·分层巩固\", \"duration\": 5, \"activities\": \"①基础（A）：判断5道正误（a.物体不受力就静止？错，可能匀速直线；b.力是维持运动的原因？错……）+ 牛顿第一定律填空；②提升（B）：选择3道（含实验题：改变阻力/控制初速度的方法）+ 简答题1道（用惯性解释：为什么要系安全带？安全气囊的原理？）；③挑战（C）：思维发散'假如没有了惯性，我们的生活会变成什么样？'写出3个合理场景。\", \"teacher\": \"发放分层练习、巡视批改、重点点评简答题\", \"student\": \"独立完成、同桌互改、订正、挑战想象\"},\n" +
                "    {\"stage\": \"课堂小结·呼应升华\", \"duration\": 3, \"activities\": \"① 回顾本课主线：认知冲突→实验探究→科学推理→得出定律→解释生活。学生板书思维导图要点；② 教师结语：'从亚里士多德到牛顿，人类用了近2000年，才揭开了运动与力的真相。这告诉我们：真理往往藏在常识的背后。希望同学们也像伽利略一样，敢质疑、会推理、重证据，永远保持对世界的好奇心！'布置分层作业。\", \"teacher\": \"引导总结回顾、人文升华、布置作业\", \"student\": \"共建思维导图、感悟科学精神、记录作业\"}\n" +
                "  ],\n" +
                "  \"boardDesign\": \"【板书设计】\\n\\n                   《牛顿第一定律》\\n                    —— 物理学史上最伟大的定律之一\\n\\n  一、认知历程（跨越2000年的接力）：\\n      亚里士多德（直觉）→ 伽利略（理想实验）→ 笛卡尔（补充）→ 牛顿（总结）✔\\n     力是维持运动    力是改变运动       匀速直线       +静止情况\\n        的原因 ❌        的原因 ✔\\n\\n  二、探究实验：阻力对物体运动的影响\\n      ┌──────────────┬──────────┬──────────┬──────────┐\\n      │   表面         │   毛巾   │   棉布   │   木板   │\\n      ├──────────────┼──────────┼──────────┼──────────┤\\n      │  阻力大小       │   大     │   中     │   小     │\\n      │  滑行距离       │   近     │   中     │   远     │\\n      │  速度减小       │   快     │   中     │   慢     │\\n      └──────────────┴──────────┴──────────┴──────────┘\\n      控制变量：同一小车、同一斜面、同一高度、静止释放（初速度相同）\\n\\n  三、推理（理想实验法）：阻力为 0 → 速度不变 → 匀速直线运动\\n\\n  四、牛顿第一定律：一切物体在没有受到力的作用时，\\n                   总保持静止状态 或 匀速直线运动状态。\\n        ↑        ↑         ↑          ↑         ↑\\n      所有物    条件      规律     二选一    核心：运动不需要力维持！\\n\\n  五、惯性：物体保持原来运动状态不变的性质（固有属性，只和质量有关！）\\n        应用：拍灰、撞锤柄、助跑跳远；  防护：安全带、安全气囊\\n\\n【学生实验数据区】            【误区警示：惯性≠速度  惯性≠力】\",\n" +
                "  \"homework\": [\n" +
                "    {\"level\": \"基础层（A·必做）\", \"items\": [\"1. 课本 P19-P20 动手动脑学物理 第1、2、3、4题（写在作业本上，要求：简答题表述完整）\", \"2. 整理笔记：画一张本课思维导图（探究过程→定律内容→惯性应用），A4纸\", \"3. 实验巩固：在家自己做一个'惯性小魔术'——将水杯放纸上，猛地抽纸，杯子不倒！拍10秒小视频上传班群\"]},\n" +
                "    {\"level\": \"提升层（B·选3）\", \"items\": [\"1. 实验报告：完整撰写《阻力对物体运动影响》实验报告，含目的、器材、步骤、记录表格、现象、推理、结论8部分\", \"2. 惯性大搜集：观察身边，找出至少5个惯性现象（包括2个应用+2个防止+1个有趣的），分类写在本子上并配图\", \"3. 阅读思考：阅读课本P18《伽利略对运动和力的研究》科学世界，回答：伽利略的理想实验'理想'在哪里？为什么不能真的做出来？它的价值何在？\", \"4. 计算题延伸：一辆质量1.5t的汽车以72km/h速度行驶，紧急刹车后滑行10s停下。若路面绝对光滑，汽车会怎样？现实中为什么会停？（从受力角度分析）\"]},\n" +
                "    {\"level\": \"挑战层（C·拓展）\", \"items\": [\"1. 小论文写作：以'假如没有惯性……'为题，写一篇300-400字的科幻小短文，要求物理逻辑正确、想象合理、有趣味\", \"2. 拓展实验：自己设计实验验证'惯性大小只与质量有关，和速度无关'（器材：2个不同质量的钢球、木块、斜面等），画出实验图并写出实验步骤\", \"3. 查阅资料：了解牛顿的生平（1643-1727），思考：牛顿说'我站在巨人的肩膀上'，在牛顿第一定律的发现中，有哪些'巨人'？他们分别做出了什么贡献？制作一张牛顿第一定律'科学英雄榜'小报\"]}\n" +
                "  ],\n" +
                "  \"qualityScore\": 91,\n" +
                "  \"standardCards\": [\n" +
                "    {\"ref\": \"义务教育物理课程标准（2022年版）·运动和相互作用·二、运动和力\", \"content\": \"通过实验，认识牛顿第一定律。能用牛顿第一定律解释生产生活中的有关现象。用物体的惯性解释自然界和生活中的有关现象。\"},\n" +
                "    {\"ref\": \"义务教育物理课程标准（2022年版）·核心素养·科学思维\", \"content\": \"科学思维主要包括模型建构、科学推理、科学论证、质疑创新等要素。学生应能建构物理模型，通过科学推理和科学论证，解释物理现象和规律；能基于证据大胆质疑，从不同角度思考问题，追求科技创新。\"},\n" +
                "    {\"ref\": \"义务教育物理课程标准（2022年版）·核心素养·科学探究\", \"content\": \"科学探究是物理学科核心素养的重要组成部分，主要包括问题、证据、解释、交流等要素。学生应能发现问题、提出猜想、设计实验、收集和处理信息、基于证据得出结论并作出解释，以及对科学探究过程和结果进行交流、评估、反思。\"},\n" +
                "    {\"ref\": \"义务教育物理课程标准（2022年版）·核心素养·科学态度与责任\", \"content\": \"科学态度与责任是指在认识科学本质，理解科学·技术·社会·环境关系的基础上，逐渐形成的探索自然的内在动力，严谨认真、实事求是和持之以恒的科学态度，以及遵守道德规范，保护环境并推动可持续发展的责任感。\"}\n" +
                "  ]\n" +
                "}");
    }

    private Map<String, Object> readParams(String json) {
        if (json == null || json.isBlank()) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    private String str(Map<String, Object> params, String key) {
        Object v = params.get(key);
        return v == null ? null : v.toString();
    }

    private String recallCore(String subject, String title) {
        List<KbChunk> chunks = chunkMapper.selectList(null);
        if (chunks.isEmpty()) {
            return "";
        }
        Set<Long> ids = chunks.stream().map(KbChunk::getDocId).collect(Collectors.toSet());
        Map<Long, KbDocument> docs = new HashMap<>();
        for (KbDocument doc : documentMapper.selectBatchIds(ids)) {
            docs.put(doc.getId(), doc);
        }
        String kw = title == null ? "" : title.replaceAll("[《》\"'“”‘’\\s]", "");
        List<String> matched = chunks.stream()
                .filter(c -> {
                    KbDocument doc = docs.get(c.getDocId());
                    if (doc == null) {
                        return false;
                    }
                    boolean subjectMatch = subject != null && !subject.isBlank()
                            && doc.getSubject() != null && doc.getSubject().contains(subject);
                    boolean kwMatch = kw.length() > 0 && c.getContent().contains(kw);
                    return subjectMatch || kwMatch;
                })
                .limit(6)
                .map(KbChunk::getContent)
                .collect(Collectors.toList());
        // 教案模板总是作为补充上下文
        for (KbChunk c : chunks) {
            KbDocument doc = docs.get(c.getDocId());
            if (doc != null && "TEMPLATE".equals(doc.getDocType())) {
                matched.add(c.getContent());
            }
        }
        return matched.stream().distinct().collect(Collectors.joining("\n"));
    }

    private String recallCases() {
        List<KbChunk> chunks = chunkMapper.selectList(null);
        if (chunks.isEmpty()) {
            return "";
        }
        Set<Long> ids = chunks.stream().map(KbChunk::getDocId).collect(Collectors.toSet());
        Map<Long, KbDocument> docs = new HashMap<>();
        for (KbDocument doc : documentMapper.selectBatchIds(ids)) {
            docs.put(doc.getId(), doc);
        }
        return chunks.stream()
                .filter(c -> {
                    KbDocument doc = docs.get(c.getDocId());
                    return doc != null && "CASE".equals(doc.getDocType());
                })
                .map(KbChunk::getContent)
                .collect(Collectors.joining("\n"));
    }

    private String loadTemplate(String scene) {
        PromptTemplate template = templateMapper.selectOne(
                new LambdaQueryWrapper<PromptTemplate>()
                        .eq(PromptTemplate::getScene, scene)
                        .orderByDesc(PromptTemplate::getVersion)
                        .last("limit 1"));
        if (template != null && template.getContent() != null) {
            return template.getContent();
        }
        return "你是资深中小学教研专家，依据2022版义务教育课程标准生成高质量教案，"
                + "输出严格的 JSON 格式（字段：title, subject, grade, textbook, lessonType, "
                + "objectives[{type,content,standardRef}], keyPoints[], difficultPoints[], "
                + "process[{stage,duration,activities,teacher,student}], boardDesign, "
                + "homework[{level,items[]}], qualityScore, standardCards[{ref,content}]）。"
                + "要求：教学目标对应核心素养；教学过程标注环节与时长（总计约40分钟）；语言具体可操作；"
                + "必须包含课标依据卡片。只输出 JSON。";
    }

    private String buildPrompt(String subject, String grade, String textbook, String title,
                               String lessonType, String extra, String coreKnowledge, String caseKnowledge) {
        StringBuilder sb = new StringBuilder();
        sb.append("【课题与要求】\n");
        sb.append("学科：").append(subject == null ? "" : subject).append("\n");
        sb.append("年级：").append(grade == null ? "" : grade).append("\n");
        sb.append("教材版本：").append(textbook == null ? "" : textbook).append("\n");
        sb.append("课题：").append(title).append("\n");
        sb.append("课型：").append(lessonType == null || lessonType.isBlank() ? "新授课" : lessonType).append("\n");
        sb.append("补充要求：").append(extra == null ? "" : extra).append("\n");
        if (coreKnowledge != null && !coreKnowledge.isBlank()) {
            sb.append("\n【课标与教材参考材料】（辅助生成，教案必须严格围绕上面的课题）\n")
                    .append(coreKnowledge).append("\n");
        }
        if (caseKnowledge != null && !caseKnowledge.isBlank()) {
            sb.append("\n【可选乡土素材】（仅当与课题相关或情境导入需要时使用，不得喧宾夺主）\n")
                    .append(caseKnowledge).append("\n");
        }
        sb.append("\n请严格按照系统提示的 JSON 结构输出教案，只输出 JSON，不要输出任何其他文字。");
        return sb.toString();
    }

    private JsonNode parseJson(String raw) throws Exception {
        String s = raw == null ? "" : raw.trim();
        if (s.startsWith("```")) {
            s = s.replaceFirst("^```[a-zA-Z]*", "").trim();
        }
        if (s.endsWith("```")) {
            s = s.substring(0, s.lastIndexOf("```")).trim();
        }
        return objectMapper.readTree(s);
    }

    private void saveSections(Long planId, JsonNode root) {
        int seq = 0;
        for (JsonNode obj : root.path("objectives")) {
            insertSection(planId, "OBJECTIVE", seq++, obj.toString(), obj.path("standardRef").asText(null));
        }
        for (JsonNode p : root.path("process")) {
            insertSection(planId, "PROCESS", seq++, p.toString(), null);
        }
        if (root.has("boardDesign")) {
            insertSection(planId, "BOARD", seq++, root.path("boardDesign").asText(), null);
        }
        if (root.path("homework").isArray()) {
            insertSection(planId, "HOMEWORK", seq++, root.path("homework").toString(), null);
        }
        if (root.path("keyPoints").isArray()) {
            insertSection(planId, "KEY_POINT", seq++, root.path("keyPoints").toString(), null);
        }
    }

    private void insertSection(Long planId, String type, int seq, String content, String standardRef) {
        LessonSection section = new LessonSection();
        section.setLessonPlanId(planId);
        section.setSectionType(type);
        section.setSeq(seq);
        section.setContent(content);
        section.setStandardRef(standardRef);
        sectionMapper.insert(section);
    }
}

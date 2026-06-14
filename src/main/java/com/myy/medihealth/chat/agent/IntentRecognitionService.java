package com.myy.medihealth.chat.agent;

import com.myy.medihealth.chat.agent.model.IntentType;
import com.myy.medihealth.chat.agent.model.MedicalIntent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 意图识别服务 - 基于关键词和模式匹配识别用户的医疗健康意图
 * 支持6种意图类型识别和10种实体信息提取
 */
@Slf4j
@Service
public class IntentRecognitionService {

    /** 各意图类型的关键词库 */
    private static final Map<IntentType, List<String>> INTENT_KEYWORDS = new LinkedHashMap<>();

    static {
        INTENT_KEYWORDS.put(IntentType.SYMPTOM_INQUIRY, Arrays.asList(
                "症状", "头疼", "发烧", "咳嗽", "感冒", "肚子疼", "腹泻", "恶心", "呕吐",
                "头晕", "胸闷", "腰疼", "牙疼", "失眠", "疲劳", "过敏", "皮疹", "痒",
                "肿", "痛", "不舒服", "难受", "怎么办", "什么病", "发热", "流鼻涕",
                "咽喉痛", "乏力", "没有胃口", "关节痛", "肌肉酸痛", "呼吸困难", "心跳快"
        ));

        INTENT_KEYWORDS.put(IntentType.DRUG_QUERY, Arrays.asList(
                "药", "药品", "吃什么药", "用药", "剂量", "副作用", "禁忌", "用法", "用量",
                "说明书", "阿莫西林", "头孢", "布洛芬", "对乙酰氨基酚", "处方药", "OTC",
                "怎么吃", "吃几粒", "一天几次", "消炎药", "抗生素", "退烧药", "止痛药",
                "感冒药", "胃药", "过敏药", "中药", "西药", "胶囊", "颗粒", "口服液"
        ));

        INTENT_KEYWORDS.put(IntentType.DISEASE_INFO, Arrays.asList(
                "疾病", "是什么病", "怎么回事", "原因", "传播", "传染", "预防", "疫苗",
                "治愈", "康复", "诊断", "检查", "治疗", "手术", "严重吗", "会死吗",
                "百科", "介绍", "科普", "了解", "什么是", "定义"
        ));

        INTENT_KEYWORDS.put(IntentType.REGISTER_GUIDE, Arrays.asList(
                "挂号", "预约", "看病", "医院", "诊所", "科室", "挂什么科", "哪个科",
                "医生", "专家", "门诊", "急诊", "医保", "就诊", "排队", "预约挂号",
                "网上挂号", "附近医院", "好医院", "三甲"
        ));

        INTENT_KEYWORDS.put(IntentType.HEALTH_ADVICE, Arrays.asList(
                "健康", "饮食", "运动", "减肥", "养生", "保健", "营养", "作息", "睡眠",
                "体检", "预防", "增强", "免疫力", "维生素", "补充", "锻炼", "健身",
                "瑜伽", "跑步", "节食", "瘦身", "调理", "保养"
        ));
    }

    /** 症状模式 */
    private static final List<String> SYMPTOM_PATTERNS = Arrays.asList(
            "头疼", "头痛", "发烧", "发热", "咳嗽", "感冒", "肚子疼", "腹泻", "恶心",
            "呕吐", "头晕", "胸闷", "腰疼", "牙疼", "失眠", "疲劳", "过敏", "皮疹",
            "发痒", "肿了", "疼痛", "咽喉痛", "流鼻涕", "打喷嚏", "全身酸痛",
            "关节痛", "呼吸困难", "心悸", "胃痛", "便秘", "尿频", "视力模糊",
            "耳鸣", "脱发", "口腔溃疡", "抽筋"
    );

    /** 身体部位模式 */
    private static final Pattern BODY_PART_PATTERN = Pattern.compile(
            "(头部|胸部|腹部|背部|腰部|颈部|肩部|膝盖|关节|皮肤|眼睛|耳朵|鼻子|口腔|牙齿|" +
                    "喉咙|心脏|肺部|胃部|肠道|肝脏|肾脏|手腕|脚踝|手臂|腿部|手指|脚趾)"
    );

    /** 时长模式 */
    private static final Pattern DURATION_PATTERN = Pattern.compile(
            "(\\d+天|\\d+周|\\d+个月|\\d+年|\\d+小时|\\d+分钟|今天|昨天|刚才|前几天|好几天|一直)"
    );

    /** 药品名称模式 */
    private static final Pattern DRUG_PATTERN = Pattern.compile(
            "(布洛芬|对乙酰氨基酚|阿莫西林|头孢|奥美拉唑|蒙脱石散|氯雷他定|" +
                    "氨溴索|阿司匹林|二甲双胍|感冒灵|板蓝根|连花清瘟|" +
                    "蒲地蓝|双黄连|藿香正气|复方甘草|牛黄解毒|维生素[ABCDE]|钙片)"
    );

    /** 严重程度模式 */
    private static final Pattern SEVERITY_PATTERN = Pattern.compile(
            "(轻微|有点|轻度|严重|剧烈|非常|特别|隐隐|一阵阵|持续|反复|越来越)"
    );

    /** 城市名称模式 */
    private static final Pattern CITY_PATTERN = Pattern.compile(
            "(北京|上海|广州|深圳|成都|杭州|武汉|南京|天津|重庆|" +
                    "西安|长沙|青岛|大连|厦门|苏州|郑州|昆明|合肥|沈阳)"
    );

    /** 年龄模式 */
    private static final Pattern AGE_PATTERN = Pattern.compile(
            "(\\d+岁|\\d+个月大|婴儿|幼儿|儿童|少年|青年|中年|老年)"
    );

    /** 科室模式 */
    private static final Pattern DEPARTMENT_PATTERN = Pattern.compile(
            "(内科|外科|儿科|妇科|产科|骨科|皮肤科|眼科|耳鼻喉科|" +
                    "口腔科|神经科|心内科|呼吸科|消化科|泌尿科|内分泌科|" +
                    "血液科|肿瘤科|中医科|康复科|急诊科|感染科|精神科)"
    );

    /**
     * 识别用户输入的医疗意图
     *
     * @param userInput 用户原始输入
     * @return 包含意图类型、置信度和提取实体的MedicalIntent
     */
    public MedicalIntent recognize(String userInput) {
        if (userInput == null || userInput.trim().isEmpty()) {
            return MedicalIntent.of(IntentType.GENERAL_CHAT, 0.2, userInput == null ? "" : userInput,
                    "用户输入为空，默认为一般对话");
        }

        String input = userInput.trim();

        // 1. 计算各意图类型的关键词匹配得分
        Map<IntentType, Double> scores = calculateIntentScores(input);

        // 2. 找出得分最高的意图
        Map.Entry<IntentType, Double> bestMatch = scores.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .orElse(null);

        IntentType intentType;
        double confidence;
        String reasoning;

        if (bestMatch == null || bestMatch.getValue() == 0.0) {
            intentType = IntentType.GENERAL_CHAT;
            confidence = 0.3;
            reasoning = "未匹配到任何医疗意图关键词，归类为一般对话";
        } else if (bestMatch.getValue() < 1.0) {
            // 低置信度匹配
            intentType = bestMatch.getKey();
            confidence = 0.4 + (bestMatch.getValue() * 0.1);
            reasoning = String.format("关键词匹配分数较低(%.1f)，推断意图为%s",
                    bestMatch.getValue(), intentType.getName());
        } else {
            intentType = bestMatch.getKey();
            // 计算相对置信度
            double totalScore = scores.values().stream().mapToDouble(Double::doubleValue).sum();
            confidence = Math.min(0.95, bestMatch.getValue() / Math.max(totalScore, 1.0));
            reasoning = String.format("基于关键词匹配推断意图为%s，相对得分%.1f",
                    intentType.getName(), bestMatch.getValue());
        }

        // 3. 检查是否为复合意图（HEALTH_ADVICE + 其他医疗关键词）
        if (intentType != IntentType.HEALTH_ADVICE && intentType != IntentType.GENERAL_CHAT) {
            double healthScore = scores.getOrDefault(IntentType.HEALTH_ADVICE, 0.0);
            if (healthScore > 0) {
                reasoning += "；同时包含健康建议相关关键词，可能存在复合意图";
                // 不改变主意图，但在推理中指出
            }
        }

        // 4. 创建初步意图
        MedicalIntent intent = MedicalIntent.of(intentType, confidence, input, reasoning);

        // 5. 提取实体
        intent = extractEntities(intent, input);

        log.info("意图识别结果: type={}, confidence={:.2f}, entities={}, reasoning={}",
                intentType.getName(), confidence, intent.getEntities().size(), reasoning);

        return intent;
    }

    /**
     * 计算各意图类型的关键词匹配分数
     */
    private Map<IntentType, Double> calculateIntentScores(String input) {
        Map<IntentType, Double> scores = new LinkedHashMap<>();

        for (Map.Entry<IntentType, List<String>> entry : INTENT_KEYWORDS.entrySet()) {
            IntentType type = entry.getKey();
            double score = 0.0;

            for (String keyword : entry.getValue()) {
                if (input.contains(keyword)) {
                    // 长关键词权重更高
                    score += 1.0 + (keyword.length() * 0.1);
                }
            }

            // 复合意图（HEALTH_ADVICE）的权重略低
            if (type.isComposite()) {
                score *= 0.9;
            }

            scores.put(type, score);
        }

        return scores;
    }

    /**
     * 从用户输入中提取医疗相关实体
     */
    private MedicalIntent extractEntities(MedicalIntent intent, String input) {
        MedicalIntent result = intent;

        // 提取症状
        for (String symptom : SYMPTOM_PATTERNS) {
            if (input.contains(symptom)) {
                result = result.withEntity(MedicalIntent.SYMPTOM, symptom);
                break;
            }
        }

        // 提取身体部位
        Matcher bodyMatcher = BODY_PART_PATTERN.matcher(input);
        if (bodyMatcher.find()) {
            result = result.withEntity(MedicalIntent.BODY_PART, bodyMatcher.group(1));
        }

        // 提取时长
        Matcher durationMatcher = DURATION_PATTERN.matcher(input);
        if (durationMatcher.find()) {
            result = result.withEntity(MedicalIntent.DURATION, durationMatcher.group(1));
        }

        // 提取药品名称
        Matcher drugMatcher = DRUG_PATTERN.matcher(input);
        if (drugMatcher.find()) {
            result = result.withEntity(MedicalIntent.DRUG_NAME, drugMatcher.group(1));
        }

        // 提取严重程度
        Matcher severityMatcher = SEVERITY_PATTERN.matcher(input);
        if (severityMatcher.find()) {
            result = result.withEntity(MedicalIntent.SEVERITY, severityMatcher.group(1));
        }

        // 提取城市
        Matcher cityMatcher = CITY_PATTERN.matcher(input);
        if (cityMatcher.find()) {
            result = result.withEntity(MedicalIntent.CITY, cityMatcher.group(1));
        }

        // 提取年龄
        Matcher ageMatcher = AGE_PATTERN.matcher(input);
        if (ageMatcher.find()) {
            result = result.withEntity(MedicalIntent.AGE, ageMatcher.group(1));
        }

        // 提取科室
        Matcher deptMatcher = DEPARTMENT_PATTERN.matcher(input);
        if (deptMatcher.find()) {
            result = result.withEntity(MedicalIntent.DEPARTMENT, deptMatcher.group(1));
        }

        // 推断性别（基于常见称谓）
        if (input.contains("老公") || input.contains("爸爸") || input.contains("爷爷") ||
                input.contains("儿子") || input.contains("男朋友") || input.contains("先生")) {
            result = result.withEntity(MedicalIntent.GENDER, "男");
        } else if (input.contains("老婆") || input.contains("妈妈") || input.contains("奶奶") ||
                input.contains("女儿") || input.contains("女朋友") || input.contains("女士")) {
            result = result.withEntity(MedicalIntent.GENDER, "女");
        }

        return result;
    }
}

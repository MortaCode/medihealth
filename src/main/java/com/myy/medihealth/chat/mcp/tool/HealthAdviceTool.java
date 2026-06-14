package com.myy.medihealth.chat.mcp.tool;

import com.myy.medihealth.chat.mcp.vo.MedicalVO.AdviceSection;
import com.myy.medihealth.chat.mcp.vo.MedicalVO.HealthAdviceRequest;
import com.myy.medihealth.chat.mcp.vo.MedicalVO.HealthAdviceResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 健康建议生成工具 - 提供个性化健康建议
 * 涵盖饮食、运动、作息、预防保健等多个维度
 */
@Slf4j
@Component("healthAdvice")
public class HealthAdviceTool {

    private final RestClient healthRestClient;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public HealthAdviceTool(@Qualifier("healthRestClient") RestClient healthRestClient) {
        this.healthRestClient = healthRestClient;
    }

    /**
     * 生成个性化健康建议
     *
     * @param request 健康建议请求，包含症状、目标、年龄段、性别
     * @return 结构化健康建议响应
     */
    @Tool(description = "生成个性化健康建议，涵盖饮食、运动、作息、预防保健等方面。" +
            "根据用户症状或健康目标（如'减肥'、'增强免疫力'、'改善睡眠'）提供针对性的健康管理方案")
    public HealthAdviceResponse generateAdvice(@ToolParam(description = "健康建议请求，包含症状、健康目标、年龄、性别") HealthAdviceRequest request) {
        String symptom = request.symptom();
        String goal = request.goal();
        String age = request.age();
        String gender = request.gender();

        log.info("健康建议生成: symptom={}, goal={}, age={}, gender={}", symptom, goal, age, gender);

        // 尝试外部API
        try {
            if (healthRestClient != null) {
                String apiResult = healthRestClient.get()
                        .uri(uriBuilder -> uriBuilder
                                .queryParam("symptom", symptom != null ? symptom : "")
                                .queryParam("goal", goal != null ? goal : "")
                                .queryParam("age", age != null ? age : "")
                                .queryParam("gender", gender != null ? gender : "")
                                .build())
                        .retrieve()
                        .body(String.class);
                if (apiResult != null && !apiResult.isEmpty()) {
                    log.info("健康建议API返回: {}", apiResult);
                }
            }
        } catch (Exception e) {
            log.warn("健康建议API调用失败，生成Mock数据: {}", e.getMessage());
        }

        // 生成建议
        String title = buildTitle(symptom, goal);
        String overview = buildOverview(symptom, goal, age, gender);
        List<AdviceSection> sections = buildSections(symptom, goal, age, gender);
        List<String> warnings = buildWarnings(symptom);

        return new HealthAdviceResponse(title, overview, sections, warnings,
                "以上健康建议仅供参考，不能替代专业医疗诊断和治疗。" +
                "在开始任何新的饮食、运动或养生计划前，请咨询专业医生。" +
                "如出现严重或持续的症状，请立即就医。",
                LocalDateTime.now().format(FORMATTER));
    }

    private String buildTitle(String symptom, String goal) {
        if (goal != null && !goal.isEmpty()) {
            return "个性化健康管理方案 - " + goal;
        }
        if (symptom != null && !symptom.isEmpty()) {
            return "针对'" + symptom + "'的健康调理建议";
        }
        return "综合性健康管理建议";
    }

    private String buildOverview(String symptom, String goal, String age, String gender) {
        StringBuilder sb = new StringBuilder();
        sb.append("本健康方案");

        if (age != null && !age.isEmpty()) {
            sb.append("针对").append(parseAgeDescription(age)).append("人群");
        }
        if (gender != null && !gender.isEmpty()) {
            sb.append("（").append(gender).append("性）");
        }
        sb.append("制定，");
        if (symptom != null && !symptom.isEmpty()) {
            sb.append("基于您描述的'").append(symptom).append("'症状，");
        }
        if (goal != null && !goal.isEmpty()) {
            sb.append("以'").append(goal).append("'为目标，");
        }
        sb.append("从饮食营养、运动锻炼、作息调整、预防保健四个维度提供综合建议。");
        sb.append("建议执行周期为4-12周，根据身体反应及时调整方案。");
        return sb.toString();
    }

    private String parseAgeDescription(String ageStr) {
        try {
            int age = Integer.parseInt(ageStr.replaceAll("[^0-9]", ""));
            if (age < 18) return "青少年";
            if (age < 45) return "青壮年";
            if (age < 60) return "中年";
            return "老年";
        } catch (NumberFormatException e) {
            return "成年";
        }
    }

    private List<AdviceSection> buildSections(String symptom, String goal, String age, String gender) {
        List<AdviceSection> sections = new ArrayList<>();

        String lowerSymptom = symptom != null ? symptom.toLowerCase() : "";
        String lowerGoal = goal != null ? goal.toLowerCase() : "";

        // 饮食建议
        sections.add(buildDietSection(lowerSymptom, lowerGoal));

        // 运动建议
        sections.add(buildExerciseSection(lowerSymptom, lowerGoal, age));

        // 作息调整
        sections.add(buildLifestyleSection(lowerSymptom, lowerGoal));

        // 预防保健
        sections.add(buildPreventionSection(lowerSymptom, lowerGoal, age, gender));

        return sections;
    }

    private AdviceSection buildDietSection(String symptom, String goal) {
        String title = "饮食营养建议";
        List<String> items = new ArrayList<>();

        if (containsAny(goal, "减肥", "减重", "瘦身")) {
            items.addAll(Arrays.asList(
                    "控制总热量摄入：每日热量摄入比平时减少300-500千卡",
                    "增加优质蛋白：鸡胸肉、鱼肉、虾仁、豆腐、蛋白，每餐保证一份",
                    "多吃高纤维蔬菜：西兰花、菠菜、芹菜、黄瓜等，每日摄入500g以上",
                    "选择低GI主食：糙米、燕麦、荞麦、红薯替代精白米面",
                    "健康脂肪：适量坚果（每日15g）、牛油果、橄榄油",
                    "戒糖控油：避免含糖饮料、油炸食品、甜点、膨化食品",
                    "进食顺序：先喝汤/水→蔬菜→蛋白质→主食，细嚼慢咽"
            ));
        } else if (containsAny(goal, "增强免疫", "免疫力")) {
            items.addAll(Arrays.asList(
                    "富含维生素C的食物：猕猴桃、橙子、草莓、青椒、西兰花",
                    "补锌食物：牡蛎、瘦牛肉、南瓜子、蛋黄",
                    "优质蛋白质：鱼类、鸡蛋、豆制品，保证每日摄入",
                    "益生菌来源：酸奶、泡菜等发酵食品，维护肠道健康",
                    "深色蔬菜：菠菜、胡萝卜、番茄等抗氧化食物",
                    "充足饮水：每日1500-2000ml温水，少量多次"
            ));
        } else if (containsAny(symptom, "失眠", "睡眠", "睡不")) {
            items.addAll(Arrays.asList(
                    "晚餐宜清淡：七分饱，睡前3小时完成进食",
                    "助眠食物：温牛奶、香蕉、小米粥（含色氨酸）",
                    "避免兴奋性饮品：下午2点后不喝咖啡、浓茶",
                    "补充镁和钙：深绿色蔬菜、坚果、豆制品",
                    "睡前避免饮酒（酒精虽能入睡但破坏深度睡眠）"
            ));
        } else if (containsAny(symptom, "胃", "消化")) {
            items.addAll(Arrays.asList(
                    "少食多餐：每餐七分饱，每日4-5餐",
                    "温和食物：粥、面条、蒸蛋、山药、南瓜",
                    "避免刺激性食物：辛辣、油腻、过冷过热、浓茶咖啡",
                    "充分咀嚼：每口食物咀嚼20-30次",
                    "饭后不立即躺下：保持坐姿至少30分钟"
            ));
        } else if (containsAny(symptom, "高血压", "血压")) {
            items.addAll(Arrays.asList(
                    "严格限盐：每日食盐<6g，使用限盐勺，警惕酱油味精等隐性盐",
                    "高钾食物：香蕉、土豆、菠菜、紫菜、豆类",
                    "DASH饮食模式：多蔬果、低脂奶、全谷物、少红肉",
                    "限制饮酒：男性每日<25g酒精，女性<15g"
            ));
        } else if (containsAny(symptom, "糖尿病", "血糖")) {
            items.addAll(Arrays.asList(
                    "定时定量进食：三餐规律，避免暴饮暴食",
                    "选择低GI食物：全谷物、豆类、大多数蔬菜",
                    "控制碳水化合物总量：主食每餐约50-75g（生重）",
                    "先吃菜后吃主食：延缓血糖上升",
                    "限制果糖：水果选择苹果、柚子、樱桃等，每日不超过200g"
            ));
        } else {
            items.addAll(Arrays.asList(
                    "均衡膳食：每日摄入谷薯类、蔬菜水果、蛋白质、奶豆类四大类食物",
                    "三餐规律：早餐吃好、午餐吃饱、晚餐吃少",
                    "多喝水：每日饮水1500-2000ml，少量多次",
                    "控盐限油：每日食盐<6g，烹饪用油25-30g",
                    "多吃蔬菜水果：每日蔬菜300-500g，水果200-350g",
                    "减少加工食品：选择天然食材，减少添加剂摄入"
            ));
        }

        return new AdviceSection("diet", title, items);
    }

    private AdviceSection buildExerciseSection(String symptom, String goal, String age) {
        String title = "运动锻炼建议";
        List<String> items = new ArrayList<>();

        if (containsAny(goal, "减肥", "减重")) {
            items.addAll(Arrays.asList(
                    "有氧运动为主：每周5次，每次40-60分钟（快走、慢跑、游泳、骑行）",
                    "高强度间歇训练(HIIT)：每周2-3次，每次20-30分钟，高效燃脂",
                    "力量训练：每周2-3次（深蹲、俯卧撑、哑铃训练等），增加基础代谢",
                    "日常活动量：每日步行8000-10000步，能走楼梯不坐电梯",
                    "最佳运动时间：早晨空腹有氧或下午4-6点",
                    "运动前后充分热身和拉伸，避免运动损伤"
            ));
        } else if (containsAny(symptom, "颈椎", "颈肩")) {
            items.addAll(Arrays.asList(
                    "颈椎操：每天2-3次，每次5-10分钟（米字操：头写米字）",
                    "游泳（尤其蛙泳和仰泳）：每周2-3次，每次30分钟",
                    "肩部拉伸：双手抱肩旋转、手臂画圈、靠墙天使",
                    "避免剧烈晃动头部的运动",
                    "工作间隙每45-60分钟活动颈部"
            ));
        } else {
            items.addAll(Arrays.asList(
                    "有氧运动：每周至少150分钟中等强度运动（快走、慢跑、游泳、骑行）",
                    "力量训练：每周2次，增加肌肉力量和骨密度",
                    "柔韧训练：每日10-15分钟拉伸，保持关节灵活性",
                    "循序渐进：从低强度开始，根据体能逐渐增加",
                    "运动前热身5-10分钟，运动后拉伸放松",
                    "避免久坐：每坐1小时起身活动5分钟"
            ));
        }

        // 年龄相关调整
        if (age != null) {
            try {
                int ageNum = Integer.parseInt(age.replaceAll("[^0-9]", ""));
                if (ageNum > 60) {
                    items.add("注意：选择低冲击运动（太极、散步、游泳），避免剧烈运动和摔倒风险");
                }
            } catch (NumberFormatException ignored) {
            }
        }

        return new AdviceSection("exercise", title, items);
    }

    private AdviceSection buildLifestyleSection(String symptom, String goal) {
        String title = "作息与生活调整";
        List<String> items = new ArrayList<>();

        if (containsAny(symptom, "失眠", "睡眠")) {
            items.addAll(Arrays.asList(
                    "固定作息时间：每天同一时间上床和起床（包括周末）",
                    "睡前仪式：泡脚、轻音乐、阅读（纸质书），建立睡眠条件反射",
                    "卧室环境：温度18-22℃，安静、遮光窗帘",
                    "限制午睡：不超过30分钟，下午3点前完成",
                    "睡不着就起床：在床上躺20分钟仍无法入睡，起床到另一个房间做些放松活动",
                    "限制电子屏幕：睡前1小时关闭手机、电脑、电视"
            ));
        } else if (containsAny(goal, "减肥")) {
            items.addAll(Arrays.asList(
                    "保证充足睡眠：每日7-8小时，睡眠不足会增加饥饿素分泌",
                    "规律进餐时间：固定三餐时间，避免夜宵",
                    "记录饮食和运动：使用APP记录，提高自律性",
                    "减压：通过冥想、深呼吸、听音乐等方式缓解压力"
            ));
        } else {
            items.addAll(Arrays.asList(
                    "保证充足睡眠：成人每日7-8小时，固定作息时间",
                    "管理压力：学习冥想、深呼吸、正念等放松技巧",
                    "戒烟限酒：吸烟者尽早戒烟，饮酒应适量",
                    "保持社交：定期与家人朋友交流，维护心理健康",
                    "限制屏幕时间：每日用眼45分钟后远眺休息",
                    "保持良好姿势：坐直站直，避免长时间低头"
            ));
        }

        return new AdviceSection("lifestyle", title, items);
    }

    private AdviceSection buildPreventionSection(String symptom, String goal, String age, String gender) {
        String title = "预防保健建议";
        List<String> items = new ArrayList<>();

        if (gender != null && gender.contains("女")) {
            items.add("妇科检查：每年一次妇科检查（宫颈TCT+HPV筛查）");
            items.add("乳腺检查：每月自检，35岁以上每年乳腺B超或钼靶");
        }
        if (gender != null && gender.contains("男")) {
            items.add("前列腺检查：50岁以上每年PSA筛查");
        }

        if (age != null) {
            try {
                int ageNum = Integer.parseInt(age.replaceAll("[^0-9]", ""));
                if (ageNum > 40) {
                    items.add("每年体检：包含血压、血糖、血脂、肝肾功能、心电图");
                    items.add("肿瘤筛查：胃肠镜（45岁以上建议）、低剂量CT（长期吸烟者）");
                } else {
                    items.add("每年体检：基础项目+根据个人情况增加项目");
                }
                if (ageNum > 60) {
                    items.add("骨密度检查：绝经后女性及老年男性建议检测");
                    items.add("眼科检查：每年眼底检查，预防白内障和青光眼");
                    items.add("流感疫苗和肺炎疫苗：每年接种");
                }
            } catch (NumberFormatException ignored) {
                items.add("定期体检：建议每年至少一次全面体检");
            }
        }

        items.addAll(Arrays.asList(
                "疫苗接种：根据年龄和健康状况及时接种推荐疫苗",
                "口腔检查：每6-12个月洗牙并进行口腔检查",
                "心理健康：关注情绪变化，有需要时寻求专业心理支持",
                "中医调理：根据体质进行季节性调理（春夏养阳，秋冬养阴）"
        ));

        return new AdviceSection("prevention", title, items);
    }

    private List<String> buildWarnings(String symptom) {
        List<String> warnings = new ArrayList<>();

        warnings.add("如出现以下情况，请立即就医：");
        warnings.add("  - 突发剧烈头痛或胸痛");
        warnings.add("  - 呼吸困难或严重气短");
        warnings.add("  - 意识模糊或晕厥");
        warnings.add("  - 持续高热（体温>39℃超过3天）");
        warnings.add("  - 不明原因的大出血");
        warnings.add("  - 症状持续加重或出现新的严重症状");

        return warnings;
    }

    private boolean containsAny(String text, String... keywords) {
        if (text == null) return false;
        for (String keyword : keywords) {
            if (text.contains(keyword)) return true;
        }
        return false;
    }
}

package com.myy.medihealth.chat.mcp.tool;

import com.myy.medihealth.chat.mcp.vo.MedicalVO.DiseaseInfo;
import com.myy.medihealth.chat.mcp.vo.MedicalVO.DiseaseRequest;
import com.myy.medihealth.chat.mcp.vo.MedicalVO.DiseaseResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 疾病百科查询工具 - 提供疾病信息查询，包括症状、病因、治疗、预防等
 * 优先使用外部API，API不可用时使用内置Mock数据
 */
@Slf4j
@Component("diseaseInfo")
public class DiseaseInfoTool {

    private final RestClient diseaseRestClient;

    /** 疾病Mock数据库 - 12种常见疾病 */
    private static final Map<String, DiseaseInfo> DISEASE_DB = Map.ofEntries(
            Map.entry("感冒", new DiseaseInfo(
                    "感冒（普通感冒）", "普通感冒是由多种病毒引起的上呼吸道感染，是最常见的急性呼吸道感染性疾病。" +
                    "全年均可发病，以冬春季多发。呈自限性，病程一般为5-7天。",
                    List.of("鼻塞", "流涕", "打喷嚏", "咽喉痛", "咳嗽", "轻度发热", "乏力", "头痛"),
                    List.of("病毒感染（鼻病毒、冠状病毒等占主要）", "受凉、疲劳导致免疫力下降",
                            "密切接触感冒患者", "季节变化、温度骤变"),
                    List.of("多休息、保证充足睡眠", "多饮温水，每日不少于1500ml",
                            "对症治疗：发热用对乙酰氨基酚，鼻塞用伪麻黄碱",
                            "维生素C补充有助于缩短病程", "一般无需使用抗生素"),
                    List.of("勤洗手，保持手卫生", "季节交替时注意保暖", "加强体育锻炼增强体质",
                            "保持室内通风", "避免与感冒患者密切接触"),
                    "呼吸内科/全科", "轻度"
            )),
            Map.entry("流感", new DiseaseInfo(
                    "流行性感冒（流感）", "流感是由流感病毒引起的急性呼吸道传染病，" +
                    "起病急、传播快、全身症状重，易引起暴发流行。不同于普通感冒。",
                    List.of("高热（体温可达39-40℃）", "剧烈头痛", "全身肌肉酸痛", "极度乏力",
                            "干咳", "咽喉痛", "流涕", "食欲减退"),
                    List.of("流感病毒感染（甲型、乙型为主）", "飞沫传播（咳嗽、打喷嚏）",
                            "接触被污染物品后触摸口鼻", "人群密集场所暴露"),
                    List.of("48小时内使用抗病毒药物（奥司他韦等）效果最佳",
                            "对症治疗：退热、止咳", "严格卧床休息", "补充水分和电解质",
                            "重症患者需住院治疗"),
                    List.of("每年接种流感疫苗（最有效方法）", "流行季节戴口罩",
                            "保持良好卫生习惯", "避免去人群密集场所", "增强自身免疫力"),
                    "呼吸内科/感染科", "中度"
            )),
            Map.entry("高血压", new DiseaseInfo(
                    "高血压", "高血压是以体循环动脉压升高为主要表现的临床综合征，" +
                    "是最常见的慢性病之一，也是心脑血管疾病最主要的危险因素。",
                    List.of("头晕、头痛", "颈项僵硬", "心悸", "疲劳", "视力模糊",
                            "鼻出血（偶见）", "多数患者早期无明显症状"),
                    List.of("原发性：遗传因素、高盐饮食、精神紧张、肥胖、缺乏运动、饮酒过量",
                            "继发性：肾脏疾病、内分泌疾病、药物影响"),
                    List.of("生活方式干预：低盐低脂饮食、控制体重、戒烟限酒、规律运动",
                            "药物治疗：ACEI/ARB类、钙通道阻滞剂、利尿剂、β受体阻滞剂等",
                            "目标血压<140/90mmHg（65岁以上<150/90mmHg）",
                            "长期规律服药，不可随意停药"),
                    List.of("限盐（每日<6g）", "控制体重（BMI<24）", "每周至少150分钟中等强度运动",
                            "戒烟限酒", "保持乐观心态，避免情绪波动", "定期测量血压"),
                    "心内科", "根据分级"
            )),
            Map.entry("糖尿病", new DiseaseInfo(
                    "糖尿病", "糖尿病是以慢性高血糖为特征的代谢性疾病，" +
                    "由胰岛素分泌不足或利用缺陷引起。长期血糖控制不佳可导致眼、肾、神经、心血管等多系统损害。",
                    List.of("三多一少：多饮、多食、多尿、体重减轻", "乏力、疲劳",
                            "视力模糊", "手足麻木或疼痛", "伤口愈合缓慢", "反复感染"),
                    List.of("1型：自身免疫破坏胰岛β细胞，胰岛素绝对缺乏",
                            "2型：胰岛素抵抗伴胰岛素分泌相对不足",
                            "遗传因素、肥胖、不健康饮食、缺乏运动、年龄增长"),
                    List.of("饮食控制：计算每日总热量，均衡营养",
                            "规律运动：每周至少150分钟有氧运动",
                            "口服降糖药：二甲双胍（一线）、磺脲类、DPP-4抑制剂等",
                            "胰岛素治疗（1型必需）", "血糖自我监测：空腹血糖<7.0mmol/L，餐后<10.0mmol/L",
                            "定期筛查并发症"),
                    List.of("健康饮食，控制总热量摄入", "保持理想体重",
                            "坚持规律运动", "定期体检，尤其是有家族史者",
                            "控制血压和血脂"),
                    "内分泌科", "慢性"
            )),
            Map.entry("胃炎", new DiseaseInfo(
                    "胃炎", "胃炎是胃黏膜炎症的统称，可分为急性和慢性两种。" +
                    "常见且多发的消化系统疾病，与饮食、药物、感染等多种因素有关。",
                    List.of("上腹部疼痛或不适", "胃胀、嗳气", "恶心、呕吐",
                            "食欲减退", "反酸烧心", "餐后饱胀"),
                    List.of("幽门螺杆菌感染（最主要）", "长期服用非甾体抗炎药（布洛芬、阿司匹林等）",
                            "饮酒过量", "饮食不规律、暴饮暴食", "精神压力",
                            "胆汁反流"),
                    List.of("根除幽门螺杆菌（四联疗法：PPI+两种抗生素+铋剂）",
                            "抑酸治疗：奥美拉唑等质子泵抑制剂",
                            "保护胃黏膜：硫糖铝、铋剂",
                            "调整饮食：少食多餐、避免刺激性食物",
                            "戒酒"),
                    List.of("规律饮食，定时定量", "使用公筷，预防幽门螺杆菌传播",
                            "慎用对胃有刺激的药物", "戒烟限酒", "保持心情舒畅"),
                    "消化内科", "轻度至中度"
            )),
            Map.entry("湿疹", new DiseaseInfo(
                    "湿疹", "湿疹是由多种内外因素引起的瘙痒剧烈的一种皮肤炎症反应。" +
                    "皮疹呈多形性，有渗出倾向，常反复发作。",
                    List.of("皮肤瘙痒（剧烈）", "红斑", "丘疹", "水疱",
                            "渗出、结痂", "皮肤干燥、脱屑", "皮肤增厚、苔藓化（慢性期）"),
                    List.of("遗传因素（特应性体质）", "免疫功能异常",
                            "环境因素（干燥、冷热刺激）", "过敏原（尘螨、花粉、食物等）",
                            "精神压力", "皮肤屏障功能障碍"),
                    List.of("外用糖皮质激素（一线治疗）", "保湿剂大量涂抹（基础治疗）",
                            "口服抗组胺药止痒（氯雷他定等）",
                            "避免搔抓", "钙调磷酸酶抑制剂（他克莫司等）",
                            "光疗（紫外线治疗）适用于中重度患者"),
                    List.of("保持皮肤湿润，每日涂抹保湿霜", "避免过度洗浴",
                            "选择棉质衣物", "避免已知过敏原", "保持居住环境湿度适宜",
                            "避免精神紧张"),
                    "皮肤科", "轻度至中度"
            )),
            Map.entry("过敏性鼻炎", new DiseaseInfo(
                    "过敏性鼻炎", "过敏性鼻炎是接触过敏原后由IgE介导的鼻黏膜非感染性炎性疾病。" +
                    "我国患病率约18%，且呈逐年上升趋势。",
                    List.of("阵发性打喷嚏（连续多个）", "清水样鼻涕", "鼻塞",
                            "鼻痒", "流泪、眼痒", "嗅觉减退", "部分伴有咳嗽、头痛"),
                    List.of("吸入性过敏原：花粉、尘螨、霉菌、宠物皮屑",
                            "遗传因素（过敏体质家族史）",
                            "空气污染", "气候变化"),
                    List.of("避免接触过敏原（最根本）", "鼻用糖皮质激素（一线治疗）",
                            "口服抗组胺药（氯雷他定、西替利嗪等）",
                            "白三烯受体拮抗剂（孟鲁司特）",
                            "免疫治疗（脱敏治疗，对尘螨等有效）",
                            "鼻腔冲洗（生理盐水）"),
                    List.of("花粉季节减少户外活动", "使用防螨床上用品",
                            "保持室内清洁、通风", "不养宠物或限制活动范围",
                            "出门佩戴口罩"),
                    "耳鼻喉科", "轻度至中度"
            )),
            Map.entry("颈椎病", new DiseaseInfo(
                    "颈椎病", "颈椎病是因颈椎间盘退变及其继发性病理改变，" +
                    "刺激或压迫邻近组织（神经根、脊髓、椎动脉等）引起的一系列症状和体征。",
                    List.of("颈肩部疼痛、僵硬", "上肢放射性疼痛或麻木",
                            "头晕、头痛", "上肢无力", "手指麻木",
                            "行走不稳（脊髓型）", "视物模糊、耳鸣"),
                    List.of("颈椎间盘退行性变（年龄增长）", "长期不良姿势（伏案工作、低头看手机）",
                            "颈部外伤", "受凉", "睡眠姿势不当"),
                    List.of("保守治疗：物理治疗（牵引、按摩、理疗）",
                            "药物治疗：非甾体抗炎药止痛、肌松药",
                            "颈围制动（急性期）",
                            "功能锻炼：颈椎操、游泳",
                            "手术治疗：脊髓型颈椎病或保守治疗无效的神经根型",
                            "改善工作和生活习惯（最重要）"),
                    List.of("保持正确坐姿，电脑屏幕与眼睛持平", "避免长时间低头",
                            "每小时活动颈部5分钟", "选择合适的枕头（高度约10cm）",
                            "加强颈背部肌肉锻炼", "注意颈部保暖"),
                    "骨科/康复科", "根据类型"
            )),
            Map.entry("失眠症", new DiseaseInfo(
                    "失眠症", "失眠症是以频繁而持续的入睡困难和/或睡眠维持困难" +
                    "并导致睡眠感不满意为特征的睡眠障碍。约30%的成年人有失眠症状。",
                    List.of("入睡困难（躺下30分钟以上才能入睡）", "睡眠维持困难、易醒",
                            "早醒且不能再入睡", "睡眠质量差、多梦",
                            "日间功能损害：疲劳、注意力不集中、记忆力下降、情绪不稳"),
                    List.of("心理因素：焦虑、抑郁、压力", "不良睡眠习惯：不规律作息、睡前使用电子产品",
                            "环境因素：噪音、光线、温度不适", "躯体疾病：慢性疼痛、呼吸系统疾病",
                            "药物或物质：咖啡因、酒精、某些药物",
                            "生物节律紊乱（倒班工作、时差）"),
                    List.of("认知行为治疗（CBT-I，首选非药物疗法）",
                            "睡眠卫生教育：固定作息、避免午睡过长",
                            "刺激控制：床只用于睡眠、睡不着就起床",
                            "松弛疗法：渐进性肌肉放松、冥想",
                            "药物治疗（短期使用）：佐匹克隆、唑吡坦等",
                            "褪黑素受体激动剂（调整生物节律）"),
                    List.of("建立规律作息时间", "睡前1小时避免使用电子设备",
                            "限制咖啡因和酒精摄入", "适量运动（但睡前3小时避免剧烈运动）",
                            "营造舒适的睡眠环境（安静、黑暗、适宜温度）",
                            "学习放松技巧，管理压力"),
                    "神经内科/心理科", "轻度至中度"
            )),
            Map.entry("新冠后遗症", new DiseaseInfo(
                    "新冠后遗症（长新冠）", "新冠后遗症指COVID-19感染后出现的持续症状，" +
                    "通常在感染后3个月仍存在，持续至少2个月，且无法用其他诊断解释。约10-30%感染者受影响。",
                    List.of("极度疲劳", "呼吸困难或气短", "认知功能障碍（脑雾）",
                            "胸痛", "心悸", "关节肌肉疼痛",
                            "嗅觉/味觉丧失或异常", "睡眠障碍", "焦虑或抑郁"),
                    List.of("新冠病毒感染后的免疫系统持续激活", "组织损伤未完全修复",
                            "微循环障碍和微血栓形成", "自主神经功能紊乱",
                            "病毒持续存在或再激活"),
                    List.of("对症支持治疗：根据症状进行针对性处理",
                            "渐进式康复训练：呼吸训练、有氧运动",
                            "认知康复训练：改善脑雾症状",
                            "心理支持：心理咨询、必要时药物治疗",
                            "充分休息，避免过度劳累",
                            "多学科综合管理"),
                    List.of("接种新冠疫苗（降低长新冠风险）", "急性感染期充分休息",
                            "感染后逐渐恢复活动，避免过早剧烈运动",
                            "均衡营养、补充维生素D", "定期随访复查"),
                    "呼吸内科/康复科/综合门诊", "中度"
            )),
            Map.entry("贫血", new DiseaseInfo(
                    "贫血", "贫血是指人体外周血红细胞容量减少，低于正常范围下限的一种常见临床症状。" +
                    "以缺铁性贫血最为常见，约占所有贫血的50%。",
                    List.of("面色苍白", "乏力、易疲劳", "头晕、眼花",
                            "心悸、气短", "食欲减退", "注意力不集中",
                            "指甲变薄变脆（反甲）", "异食癖（想吃非食物物品）"),
                    List.of("缺铁：铁摄入不足、吸收障碍、慢性失血（月经过多、消化道出血）",
                            "维生素B12/叶酸缺乏：营养不良、吸收不良",
                            "慢性病贫血：慢性感染、炎症、肿瘤",
                            "溶血性贫血：红细胞破坏过多",
                            "再生障碍性贫血：骨髓造血功能障碍"),
                    List.of("缺铁性贫血：口服铁剂（硫酸亚铁等），补充维生素C促进吸收",
                            "巨幼细胞贫血：补充维生素B12和叶酸",
                            "饮食调整：增加红肉、动物肝脏、深绿色蔬菜",
                            "治疗原发病（如月经过多、消化道出血）",
                            "严重贫血需输血治疗"),
                    List.of("均衡饮食，保证铁和蛋白质摄入",
                            "月经量多者及时就医", "定期体检，关注血常规",
                            "素食者注意补充铁和维生素B12",
                            "儿童和孕妇为重点关注人群"),
                    "血液科", "根据程度"
            )),
            Map.entry("哮喘", new DiseaseInfo(
                    "哮喘", "支气管哮喘（哮喘）是一种以慢性气道炎症和气道高反应性为特征的异质性疾病。" +
                    "表现为反复发作的喘息、气急、胸闷或咳嗽等症状。我国约有4570万哮喘患者。",
                    List.of("反复发作的喘息", "气急、胸闷", "咳嗽（夜间或清晨加重）",
                            "呼气性呼吸困难", "发作时可闻及哮鸣音",
                            "症状可自行缓解或经治疗后缓解"),
                    List.of("遗传因素（过敏体质家族史）", "过敏原：尘螨、花粉、宠物皮屑、霉菌",
                            "呼吸道感染（尤其是病毒感染）", "运动、冷空气刺激",
                            "职业性致敏物", "空气污染、吸烟（包括二手烟）",
                            "精神因素（情绪激动）", "药物（阿司匹林等）"),
                    List.of("长期控制药物：吸入性糖皮质激素（ICS，首选）",
                            "ICS+长效β2受体激动剂（LABA）联合治疗",
                            "白三烯受体拮抗剂（孟鲁司特）",
                            "急救药物：短效β2受体激动剂（沙丁胺醇）",
                            "过敏原特异性免疫治疗（脱敏治疗）",
                            "生物靶向治疗（重度哮喘）"),
                    List.of("避免接触已知过敏原", "保持室内清洁，使用防螨床上用品",
                            "不养宠物或限制接触", "戒烟并避免二手烟",
                            "预防呼吸道感染", "遵医嘱长期规范用药，不可随意停药",
                            "学习正确使用吸入装置"),
                    "呼吸内科", "轻度至重度"
            ))
    );

    public DiseaseInfoTool(@Qualifier("diseaseRestClient") RestClient diseaseRestClient) {
        this.diseaseRestClient = diseaseRestClient;
    }

    /**
     * 查询疾病百科信息
     *
     * @param request 疾病查询请求，包含疾病名称和/或症状
     * @return 疾病信息响应
     */
    @Tool(description = "查询疾病百科信息，包括疾病概述、典型症状、病因、治疗方法、预防措施。" +
            "输入疾病名称（如'糖尿病'）或症状描述（如'头痛发热'）均可查询相关疾病")
    public DiseaseResponse queryDisease(@ToolParam(description = "疾病查询请求，包含疾病名称和可选症状") DiseaseRequest request) {
        String query = buildQueryString(request);
        log.info("疾病查询: query={}", query);

        // 尝试外部API
        try {
            if (diseaseRestClient != null) {
                String apiResult = diseaseRestClient.get()
                        .uri(uriBuilder -> uriBuilder
                                .queryParam("diseaseName", request.diseaseName() != null ? request.diseaseName() : "")
                                .queryParam("symptom", request.symptom() != null ? request.symptom() : "")
                                .build())
                        .retrieve()
                        .body(String.class);
                if (apiResult != null && !apiResult.isEmpty()) {
                    log.info("疾病查询API返回: {}", apiResult);
                }
            }
        } catch (Exception e) {
            log.warn("疾病查询API调用失败，使用Mock数据: {}", e.getMessage());
        }

        // Mock数据
        List<DiseaseInfo> results = searchDiseases(request);
        return new DiseaseResponse(query, results,
                "以上疾病信息仅供参考，不能替代专业医疗诊断。" +
                "如有相关症状，请及时到正规医院就诊，由专业医生进行诊断和治疗。");
    }

    private String buildQueryString(DiseaseRequest request) {
        StringBuilder sb = new StringBuilder();
        if (request.diseaseName() != null && !request.diseaseName().isEmpty()) {
            sb.append(request.diseaseName());
        }
        if (request.symptom() != null && !request.symptom().isEmpty()) {
            if (sb.length() > 0) sb.append(" - ");
            sb.append("症状：").append(request.symptom());
        }
        return sb.isEmpty() ? "通用疾病查询" : sb.toString();
    }

    private List<DiseaseInfo> searchDiseases(DiseaseRequest request) {
        String diseaseName = request.diseaseName();
        String symptom = request.symptom();

        if (diseaseName != null && !diseaseName.isEmpty()) {
            // 精确匹配
            for (DiseaseInfo disease : DISEASE_DB.values()) {
                if (disease.name().contains(diseaseName)) {
                    return List.of(disease);
                }
            }
            // 关键词匹配
            List<DiseaseInfo> matches = DISEASE_DB.values().stream()
                    .filter(disease -> disease.name().contains(diseaseName) ||
                            disease.overview().contains(diseaseName))
                    .collect(Collectors.toList());
            if (!matches.isEmpty()) {
                return matches;
            }
        }

        // 根据症状匹配疾病
        if (symptom != null && !symptom.isEmpty()) {
            List<DiseaseInfo> symptomMatches = DISEASE_DB.values().stream()
                    .filter(disease -> disease.symptoms().stream()
                            .anyMatch(s -> symptom.contains(s) || s.contains(symptom)))
                    .collect(Collectors.toList());
            if (!symptomMatches.isEmpty()) {
                return symptomMatches;
            }
        }

        return new ArrayList<>(DISEASE_DB.values());
    }
}

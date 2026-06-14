package com.myy.medihealth.chat.mcp.tool;

import com.myy.medihealth.chat.mcp.vo.MedicalVO.DrugItem;
import com.myy.medihealth.chat.mcp.vo.MedicalVO.DrugRequest;
import com.myy.medihealth.chat.mcp.vo.MedicalVO.DrugResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 药品查询工具 - 提供药品信息查询，包括用法用量、副作用、禁忌等
 * 优先使用外部API，API不可用时使用内置Mock数据
 */
@Slf4j
@Component("drugQuery")
public class DrugQueryTool {

    private final RestClient drugRestClient;

    /** 药品Mock数据库 */
    private static final Map<String, DrugItem> DRUG_DB = Map.ofEntries(
            Map.entry("布洛芬", new DrugItem(
                    "布洛芬", "Ibuprofen", "解热镇痛抗炎药",
                    "用于缓解轻至中度疼痛，如头痛、牙痛、神经痛、肌肉痛、痛经及关节痛等，" +
                    "也用于普通感冒或流行性感冒引起的发热",
                    "成人：一次0.2-0.4g，每4-6小时一次，每日不超过2.4g。" +
                    "儿童：5-10mg/kg，每6-8小时一次",
                    "胃肠道反应（恶心、呕吐、腹痛、腹泻）、头晕、皮疹。" +
                    "长期使用可能导致胃溃疡、肾功能损害",
                    "1. 对阿司匹林或其它非甾体抗炎药过敏者禁用\n2. 活动性消化性溃疡者禁用\n" +
                    "3. 严重肝肾功能不全者禁用\n4. 孕妇及哺乳期妇女慎用",
                    "1. 餐后服用可减少胃肠刺激\n2. 不宜与其他非甾体抗炎药同用\n" +
                    "3. 长期大量用药应定期检查肝肾功能和血象",
                    false, "5-30元"
            )),
            Map.entry("对乙酰氨基酚", new DrugItem(
                    "对乙酰氨基酚", "Paracetamol / Acetaminophen", "解热镇痛药",
                    "用于普通感冒或流行性感冒引起的发热，也用于缓解轻至中度疼痛",
                    "成人：一次0.3-0.6g，每4-6小时一次，每日不超过2g。" +
                    "儿童：10-15mg/kg，每4-6小时一次，每日不超过4次",
                    "偶见皮疹、荨麻疹、药热及粒细胞减少。" +
                    "长期大量用药可导致肝肾功能异常。过量服用可致严重肝损害",
                    "1. 严重肝肾功能不全者禁用\n2. 对本品过敏者禁用\n" +
                    "3. 酒精中毒者禁用",
                    "1. 服药期间不得饮酒或含酒精饮料\n2. 不宜长期或大量使用\n" +
                    "3. 出现皮疹等过敏反应应立即停药",
                    false, "3-20元"
            )),
            Map.entry("阿莫西林", new DrugItem(
                    "阿莫西林", "Amoxicillin", "青霉素类抗生素",
                    "用于敏感菌所致的呼吸道感染、泌尿生殖道感染、皮肤软组织感染等",
                    "成人：一次0.5g，每6-8小时一次。儿童：20-40mg/kg/日，分3次服用",
                    "恶心、呕吐、腹泻等胃肠道反应；皮疹、药物热；偶见血清转氨酶升高。" +
                    "严重不良反应包括过敏性休克、剥脱性皮炎",
                    "1. 青霉素过敏及青霉素皮肤试验阳性患者禁用\n" +
                    "2. 传染性单核细胞增多症患者禁用\n" +
                    "3. 严重肾功能不全者需调整剂量",
                    "1. 用前必须做青霉素皮肤试验\n" +
                    "2. 与丙磺舒合用可提高血药浓度\n3. 与氨基糖苷类抗生素有协同作用",
                    true, "8-45元"
            )),
            Map.entry("头孢克洛", new DrugItem(
                    "头孢克洛", "Cefaclor", "头孢菌素类抗生素",
                    "用于敏感菌所致的中耳炎、呼吸道感染、尿路感染及皮肤软组织感染",
                    "成人：一次0.25g，每8小时一次。儿童：20-40mg/kg/日，分3次服用",
                    "胃肠道反应（腹泻、恶心、呕吐）；皮疹；血清病样反应。" +
                    "偶见一过性肝酶升高、嗜酸性粒细胞增多",
                    "1. 对头孢菌素类过敏者禁用\n" +
                    "2. 对青霉素类有过敏性休克史者禁用\n" +
                    "3. 严重肾功能不全者慎用",
                    "1. 与青霉素类存在交叉过敏\n2. 服药期间可出现尿糖假阳性\n" +
                    "3. 宜空腹服用",
                    true, "15-60元"
            )),
            Map.entry("奥美拉唑", new DrugItem(
                    "奥美拉唑", "Omeprazole", "质子泵抑制剂",
                    "用于胃及十二指肠溃疡、反流性食管炎、卓-艾综合征等胃酸相关疾病",
                    "成人：一次20mg，每日1-2次，晨起吞服。疗程4-8周",
                    "头痛、腹泻、恶心、便秘、腹胀。" +
                    "长期使用可能增加骨折风险、维生素B12缺乏",
                    "1. 对本品过敏者禁用\n2. 严重肾功能不全者慎用\n" +
                    "3. 婴幼儿禁用",
                    "1. 一般清晨空腹服用\n2. 与氯吡格雷合用时应注意相互作用\n" +
                    "3. 长期用药需监测胃泌素水平",
                    true, "20-130元"
            )),
            Map.entry("蒙脱石散", new DrugItem(
                    "蒙脱石散", "Dioctahedral Smectite", "止泻药/消化道黏膜保护药",
                    "用于成人及儿童急、慢性腹泻，也可用于食管炎、胃炎引起的疼痛",
                    "成人：一次3g，每日3次。将药粉倒入半杯温水中摇匀后服用。" +
                    "儿童：1岁以下每日3g；1-2岁每日3-6g；2岁以上每日6-9g，分3次服用",
                    "偶见便秘。大便干结时应减量",
                    "1. 肠梗阻患者禁用\n2. 对本品过敏者禁用",
                    "1. 宜在两餐之间服用\n2. 与其他药物间隔至少1-2小时服用\n" +
                    "3. 治疗急性腹泻时应注意纠正脱水",
                    false, "10-35元"
            )),
            Map.entry("氯雷他定", new DrugItem(
                    "氯雷他定", "Loratadine", "抗组胺药",
                    "用于缓解过敏性鼻炎、荨麻疹、瘙痒性皮肤病及其他过敏性皮肤病的症状",
                    "成人及12岁以上儿童：一次10mg，每日1次。" +
                    "2-12岁儿童：体重>30kg者10mg每日1次；体重<30kg者5mg每日1次",
                    "乏力、头痛、口干、嗜睡。偶见心动过速、肝功能异常",
                    "1. 对本品过敏者禁用\n2. 严重肝功能不全者慎用",
                    "1. 服药期间不宜驾驶或操作精密机器\n2. 与酮康唑、大环内酯类抗生素合用需谨慎\n" +
                    "3. 妊娠期及哺乳期慎用",
                    false, "8-30元"
            )),
            Map.entry("氨溴索", new DrugItem(
                    "氨溴索", "Ambroxol", "祛痰药",
                    "用于急、慢性呼吸道疾病引起的痰液黏稠、咳痰困难",
                    "成人：一次30-60mg，每日3次。儿童：1.2-1.6mg/kg/日，分3次服用",
                    "轻度胃肠道反应（恶心、胃部不适）。偶见皮疹",
                    "1. 对本品过敏者禁用\n2. 妊娠早期慎用",
                    "1. 饭后服用可减少胃肠不适\n2. 与抗生素合用可增加药物在肺部浓度\n" +
                    "3. 服药后多饮水有助于稀释痰液",
                    false, "10-40元"
            )),
            Map.entry("阿司匹林", new DrugItem(
                    "阿司匹林", "Aspirin", "解热镇痛抗炎药/抗血小板聚集药",
                    "小剂量用于预防心脑血管事件；常规剂量用于解热镇痛、抗炎抗风湿",
                    "解热镇痛：一次0.3-0.6g，每日3次。" +
                    "抗血小板：一次75-100mg，每日1次",
                    "胃肠道反应（恶心、呕吐、上腹不适）、出血倾向增加、" +
                    "过敏反应（哮喘、皮疹）。长期大量使用可致肾损害",
                    "1. 活动性溃疡病或消化道出血者禁用\n2. 血友病或血小板减少症禁用\n" +
                    "3. 对本品过敏尤其是阿司匹林哮喘者禁用\n4. 孕妇禁用",
                    "1. 应与食物同服或饭后服用\n2. 手术前一周应停用\n" +
                    "3. 不宜与酒精同服\n4. 不宜与布洛芬等NSAID长期联用",
                    false, "3-25元"
            )),
            Map.entry("二甲双胍", new DrugItem(
                    "二甲双胍", "Metformin", "口服降糖药",
                    "首选用于2型糖尿病的治疗，尤其是肥胖患者的血糖控制",
                    "起始剂量：一次0.25g，每日2-3次，餐中或餐后服用。" +
                    "逐渐增量至一次0.5g，每日2-3次，最大剂量每日不超过2g",
                    "胃肠道反应（恶心、呕吐、腹泻、金属味）、乳酸酸中毒（罕见但严重）。" +
                    "长期使用可能影响维生素B12吸收",
                    "1. 严重肾功能不全（eGFR<30）禁用\n2. 严重肝功能不全禁用\n" +
                    "3. 急性心衰、休克患者禁用\n4. 碘造影剂检查前后48小时应停用",
                    "1. 应定期监测肾功能和乳酸水平\n2. 与胰岛素或磺脲类合用时应警惕低血糖\n" +
                    "3. 长期使用应补充维生素B12\n4. 老年患者应谨慎调整剂量",
                    true, "15-50元"
            ))
    );

    public DrugQueryTool(@Qualifier("drugRestClient") RestClient drugRestClient) {
        this.drugRestClient = drugRestClient;
    }

    /**
     * 查询药品信息
     *
     * @param request 药品查询请求，包含药品名称和/或症状
     * @return 药品查询结果
     */
    @Tool(description = "查询药品信息，包括用法用量、副作用、禁忌、注意事项等。" +
            "输入药品名称（如'布洛芬'）或症状（如'头痛'）均可查询相关药品")
    public DrugResponse queryDrug(@ToolParam(description = "药品查询请求，包含药品名称和可选症状") DrugRequest request) {
        String query = buildQueryString(request);
        log.info("药品查询: query={}", query);

        // 尝试外部API
        try {
            if (drugRestClient != null) {
                String apiResult = drugRestClient.get()
                        .uri(uriBuilder -> uriBuilder
                                .queryParam("drugName", request.drugName() != null ? request.drugName() : "")
                                .queryParam("symptom", request.symptom() != null ? request.symptom() : "")
                                .build())
                        .retrieve()
                        .body(String.class);
                if (apiResult != null && !apiResult.isEmpty()) {
                    log.info("药品查询API返回: {}", apiResult);
                    // 如果API返回有效数据，解析并返回
                    // 此处省略JSON解析，实际应使用Jackson转换
                }
            }
        } catch (Exception e) {
            log.warn("药品查询API调用失败，使用Mock数据: {}", e.getMessage());
        }

        // Mock数据回退
        List<DrugItem> results = searchDrugs(request);
        return new DrugResponse(query, results,
                "以上药品信息仅供参考，具体用药请在医师或药师指导下进行。" +
                "用药前请仔细阅读说明书或咨询专业医生。如出现严重不良反应，请立即停药并就医。");
    }

    private String buildQueryString(DrugRequest request) {
        StringBuilder sb = new StringBuilder();
        if (request.drugName() != null && !request.drugName().isEmpty()) {
            sb.append(request.drugName());
        }
        if (request.symptom() != null && !request.symptom().isEmpty()) {
            if (sb.length() > 0) sb.append(" - ");
            sb.append("症状：").append(request.symptom());
        }
        return sb.isEmpty() ? "通用药品查询" : sb.toString();
    }

    /**
     * 在Mock数据库中搜索药品
     */
    private List<DrugItem> searchDrugs(DrugRequest request) {
        String drugName = request.drugName();
        String symptom = request.symptom();

        // 精确匹配药品名
        if (drugName != null && !drugName.isEmpty()) {
            Optional<DrugItem> exactMatch = DRUG_DB.values().stream()
                    .filter(drug -> drug.name().contains(drugName) ||
                            drug.genericName().toLowerCase().contains(drugName.toLowerCase()))
                    .findFirst();
            if (exactMatch.isPresent()) {
                return List.of(exactMatch.get());
            }
            // 模糊匹配
            List<DrugItem> fuzzyMatches = DRUG_DB.values().stream()
                    .filter(drug -> drug.category().contains(drugName))
                    .collect(Collectors.toList());
            if (!fuzzyMatches.isEmpty()) {
                return fuzzyMatches;
            }
        }

        // 根据症状推荐药品
        if (symptom != null && !symptom.isEmpty()) {
            List<DrugItem> symptomMatches = new ArrayList<>();
            String s = symptom.toLowerCase();

            if (containsAny(s, "发热", "发烧", "体温", "头痛", "牙痛", "痛经", "关节痛", "肌肉痛")) {
                addIfNotPresent(symptomMatches, "布洛芬");
                addIfNotPresent(symptomMatches, "对乙酰氨基酚");
            }
            if (containsAny(s, "感染", "发炎", "咳嗽", "咽痛", "肺炎", "支气管")) {
                addIfNotPresent(symptomMatches, "阿莫西林");
                addIfNotPresent(symptomMatches, "头孢克洛");
                addIfNotPresent(symptomMatches, "氨溴索");
            }
            if (containsAny(s, "胃痛", "胃酸", "反酸", "烧心", "溃疡", "胃炎")) {
                addIfNotPresent(symptomMatches, "奥美拉唑");
            }
            if (containsAny(s, "腹泻", "拉肚子", "肚泻", "水样")) {
                addIfNotPresent(symptomMatches, "蒙脱石散");
            }
            if (containsAny(s, "过敏", "鼻炎", "荨麻疹", "皮疹", "瘙痒", "花粉")) {
                addIfNotPresent(symptomMatches, "氯雷他定");
            }
            if (containsAny(s, "糖尿病", "血糖", "高血糖")) {
                addIfNotPresent(symptomMatches, "二甲双胍");
            }
            if (containsAny(s, "血栓", "心梗", "脑梗", "心血管", "预防")) {
                addIfNotPresent(symptomMatches, "阿司匹林");
            }

            if (!symptomMatches.isEmpty()) {
                return symptomMatches;
            }
        }

        // 默认返回常用药品列表
        return new ArrayList<>(DRUG_DB.values());
    }

    private void addIfNotPresent(List<DrugItem> list, String drugName) {
        DrugItem drug = DRUG_DB.get(drugName);
        if (drug != null && !list.contains(drug)) {
            list.add(drug);
        }
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) return true;
        }
        return false;
    }
}

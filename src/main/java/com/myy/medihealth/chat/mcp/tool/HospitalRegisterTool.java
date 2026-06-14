package com.myy.medihealth.chat.mcp.tool;

import com.myy.medihealth.chat.mcp.vo.MedicalVO.HospitalItem;
import com.myy.medihealth.chat.mcp.vo.MedicalVO.HospitalRequest;
import com.myy.medihealth.chat.mcp.vo.MedicalVO.HospitalResponse;
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
 * 医院挂号查询工具 - 提供医院查询、科室推荐、排队信息等服务
 * 覆盖5个主要城市，每个城市3-5家医院
 */
@Slf4j
@Component("hospitalRegister")
public class HospitalRegisterTool {

    private final RestClient hospitalRestClient;

    /** 医院Mock数据库 - 5城市，共20+家医院 */
    private static final Map<String, List<HospitalItem>> HOSPITAL_DB = Map.of(
            "北京", List.of(
                    new HospitalItem("北京协和医院", "北京市东城区东单北大街53号",
                            "010-69156114", "三甲",
                            "综合（内科、外科、妇产科、儿科、皮肤科、眼科等）",
                            true, 4.8, 156),
                    new HospitalItem("北京大学第一医院", "北京市西城区西什库大街8号",
                            "010-83572211", "三甲",
                            "综合（心内科、肾内科、泌尿外科、妇产科等）",
                            true, 4.7, 98),
                    new HospitalItem("北京朝阳医院", "北京市朝阳区工体南路8号",
                            "010-85231000", "三甲",
                            "综合（呼吸科、心脏中心、急诊科、骨科等）",
                            true, 4.5, 120),
                    new HospitalItem("北京中医药大学东直门医院", "北京市东城区海运仓5号",
                            "010-84013276", "三甲",
                            "中医综合（中医内科、针灸科、推拿科、中西医结合等）",
                            true, 4.4, 65),
                    new HospitalItem("北京市海淀医院", "北京市海淀区中关村大街29号",
                            "010-82619999", "二甲",
                            "综合（内科、外科、妇产科、儿科、口腔科等）",
                            true, 4.2, 42)
            ),
            "上海", List.of(
                    new HospitalItem("复旦大学附属华山医院", "上海市静安区乌鲁木齐中路12号",
                            "021-52889999", "三甲",
                            "综合（神经外科、皮肤科、手外科、感染科等）",
                            true, 4.9, 180),
                    new HospitalItem("上海交通大学医学院附属瑞金医院", "上海市黄浦区瑞金二路197号",
                            "021-64370045", "三甲",
                            "综合（内分泌科、血液科、心内科、骨科等）",
                            true, 4.8, 145),
                    new HospitalItem("上海市第一人民医院", "上海市虹口区海宁路100号",
                            "021-63240090", "三甲",
                            "综合（眼科、器官移植、泌尿外科等）",
                            true, 4.6, 110),
                    new HospitalItem("上海长海医院", "上海市杨浦区长海路168号",
                            "021-31166666", "三甲",
                            "综合（烧伤科、肝胆外科、胸心外科等）",
                            true, 4.5, 95),
                    new HospitalItem("上海市徐汇区中心医院", "上海市徐汇区淮海中路966号",
                            "021-54030000", "二甲",
                            "综合（内科、外科、妇科、儿科等）",
                            true, 4.1, 30)
            ),
            "广州", List.of(
                    new HospitalItem("中山大学附属第一医院", "广州市越秀区中山二路58号",
                            "020-87755766", "三甲",
                            "综合（器官移植、心内科、神经科、妇产科等）",
                            true, 4.7, 135),
                    new HospitalItem("南方医科大学南方医院", "广州市白云区广州大道北1838号",
                            "020-61641888", "三甲",
                            "综合（消化科、肾内科、感染内科、骨科等）",
                            true, 4.6, 105),
                    new HospitalItem("广州市第一人民医院", "广州市越秀区盘福路1号",
                            "020-81048888", "三甲",
                            "综合（老年病科、消化内科、心血管内科等）",
                            true, 4.4, 85),
                    new HospitalItem("广州市越秀区中医院", "广州市越秀区海珠中路83号",
                            "020-81888888", "二甲",
                            "中医综合（中医内科、针灸推拿、康复科等）",
                            false, 4.0, 15)
            ),
            "深圳", List.of(
                    new HospitalItem("深圳市人民医院", "深圳市罗湖区东门北路1017号",
                            "0755-25533018", "三甲",
                            "综合（心内科、呼吸科、消化内科、骨科等）",
                            true, 4.5, 95),
                    new HospitalItem("北京大学深圳医院", "深圳市福田区莲花路1120号",
                            "0755-83923333", "三甲",
                            "综合（肿瘤科、心内科、妇产科、泌尿外科等）",
                            true, 4.5, 88),
                    new HospitalItem("深圳市第二人民医院", "深圳市福田区笋岗西路3002号",
                            "0755-83366388", "三甲",
                            "综合（骨科、烧伤科、神经外科、内分泌科等）",
                            true, 4.3, 78),
                    new HospitalItem("深圳市南山区人民医院", "深圳市南山区桃园路89号",
                            "0755-26553111", "二甲",
                            "综合（内科、外科、妇产科、儿科等）",
                            true, 4.2, 45)
            ),
            "成都", List.of(
                    new HospitalItem("四川大学华西医院", "成都市武侯区国学巷37号",
                            "028-85422114", "三甲",
                            "综合（麻醉科、放射科、病理科、神经外科等，全国排名前列）",
                            true, 4.9, 210),
                    new HospitalItem("四川省人民医院", "成都市青羊区一环路西二段32号",
                            "028-87393999", "三甲",
                            "综合（心血管内科、呼吸科、急救中心等）",
                            true, 4.5, 120),
                    new HospitalItem("成都中医药大学附属医院", "成都市金牛区十二桥路39号",
                            "028-87769902", "三甲",
                            "中医综合（中医内科、妇科、针灸科、推拿科等）",
                            true, 4.4, 72),
                    new HospitalItem("成都市第一人民医院", "成都市高新区万象北路18号",
                            "028-85328122", "三甲",
                            "综合（中西医结合重点学科）",
                            true, 4.3, 65),
                    new HospitalItem("成都市锦江区妇幼保健院", "成都市锦江区三官堂街3号",
                            "028-84448000", "二甲",
                            "专科（妇产科、儿科、妇幼保健、产后康复）",
                            false, 4.2, 38)
            )
    );

    /** 科室到症状的关键词映射 */
    private static final Map<String, List<String>> DEPARTMENT_SYMPTOMS = Map.ofEntries(
            Map.entry("内科", List.of("发热", "咳嗽", "头晕", "乏力", "胸闷", "腹痛", "恶心")),
            Map.entry("外科", List.of("外伤", "骨折", "肿物", "烧烫伤", "阑尾炎", "疝气")),
            Map.entry("儿科", List.of("小儿发热", "小儿咳嗽", "小儿腹泻", "小儿皮疹")),
            Map.entry("妇产科", List.of("月经不调", "痛经", "白带异常", "备孕", "孕产检", "更年期")),
            Map.entry("骨科", List.of("颈肩痛", "腰腿痛", "关节痛", "骨折", "骨质疏松", "扭伤")),
            Map.entry("皮肤科", List.of("皮疹", "瘙痒", "脱发", "痤疮", "湿疹", "色斑", "癣")),
            Map.entry("眼科", List.of("视力模糊", "眼红", "眼痛", "干眼", "流泪", "眼异物感")),
            Map.entry("耳鼻喉科", List.of("耳痛", "耳鸣", "听力下降", "鼻塞", "流鼻血", "咽喉痛", "声音嘶哑")),
            Map.entry("口腔科", List.of("牙痛", "牙龈出血", "口腔溃疡", "拔牙", "洗牙", "正畸")),
            Map.entry("神经科", List.of("头痛", "眩晕", "失眠", "手脚麻木", "抽搐", "记忆力下降")),
            Map.entry("心内科", List.of("胸痛", "心悸", "气短", "高血压", "高血脂")),
            Map.entry("呼吸科", List.of("咳嗽", "咳痰", "气喘", "呼吸困难", "胸痛")),
            Map.entry("消化科", List.of("胃痛", "反酸", "腹胀", "腹泻", "便秘", "恶心呕吐"))
    );

    public HospitalRegisterTool(@Qualifier("hospitalRestClient") RestClient hospitalRestClient) {
        this.hospitalRestClient = hospitalRestClient;
    }

    /**
     * 查询医院挂号信息
     *
     * @param request 医院查询请求，包含城市、科室、症状
     * @return 医院信息响应
     */
    @Tool(description = "查询医院挂号信息，包括科室设置、地址、电话、医院等级、急诊信息、排队情况。" +
            "输入城市名称（如'北京'）和需要的科室（如'心内科'）或症状（如'胸痛'）获取推荐医院")
    public HospitalResponse queryHospital(@ToolParam(description = "医院查询请求，包含城市、科室和可选症状") HospitalRequest request) {
        String city = request.city();
        String department = request.department();
        log.info("医院查询: city={}, department={}, symptom={}", city, department, request.symptom());

        // 尝试外部API
        try {
            if (hospitalRestClient != null) {
                String apiResult = hospitalRestClient.get()
                        .uri(uriBuilder -> uriBuilder
                                .queryParam("city", city != null ? city : "")
                                .queryParam("department", department != null ? department : "")
                                .queryParam("symptom", request.symptom() != null ? request.symptom() : "")
                                .build())
                        .retrieve()
                        .body(String.class);
                if (apiResult != null && !apiResult.isEmpty()) {
                    log.info("医院查询API返回: {}", apiResult);
                }
            }
        } catch (Exception e) {
            log.warn("医院查询API调用失败，使用Mock数据: {}", e.getMessage());
        }

        // Mock数据
        List<HospitalItem> hospitals = searchHospitals(city, department, request.symptom());
        String resolvedCity = city != null && !city.isEmpty() ? city : "全部城市";
        String resolvedDept = department != null && !department.isEmpty() ? department : "综合科室";

        String tip = buildTip(hospitals);
        return new HospitalResponse(resolvedCity, resolvedDept, hospitals, tip);
    }

    private List<HospitalItem> searchHospitals(String city, String department, String symptom) {
        List<HospitalItem> results = new ArrayList<>();

        // 如果指定了城市，按城市筛选
        if (city != null && !city.isEmpty()) {
            String key = findCityKey(city);
            if (key != null) {
                results.addAll(HOSPITAL_DB.get(key));
            }
        }

        // 如果没有指定城市或城市无结果，返回所有城市
        if (results.isEmpty()) {
            for (List<HospitalItem> cityHospitals : HOSPITAL_DB.values()) {
                results.addAll(cityHospitals);
            }
        }

        // 按科室筛选
        if (department != null && !department.isEmpty()) {
            results = results.stream()
                    .filter(hospital -> hospital.department().contains(department))
                    .collect(Collectors.toList());
        }

        // 根据症状推荐科室并筛选
        if (symptom != null && !symptom.isEmpty() && (department == null || department.isEmpty())) {
            String recommendedDept = recommendDepartment(symptom);
            if (recommendedDept != null) {
                List<HospitalItem> deptResults = results.stream()
                        .filter(hospital -> hospital.department().contains(recommendedDept))
                        .collect(Collectors.toList());
                if (!deptResults.isEmpty()) {
                    results = deptResults;
                }
            }
        }

        // 按评分排序
        results.sort((a, b) -> Double.compare(b.rating(), a.rating()));

        // 最多返回10条
        if (results.size() > 10) {
            results = results.subList(0, 10);
        }

        return results;
    }

    private String findCityKey(String cityInput) {
        for (String key : HOSPITAL_DB.keySet()) {
            if (cityInput.contains(key) || key.contains(cityInput)) {
                return key;
            }
        }
        return null;
    }

    private String recommendDepartment(String symptom) {
        for (Map.Entry<String, List<String>> entry : DEPARTMENT_SYMPTOMS.entrySet()) {
            for (String keyword : entry.getValue()) {
                if (symptom.contains(keyword)) {
                    return entry.getKey();
                }
            }
        }
        return null;
    }

    private String buildTip(List<HospitalItem> hospitals) {
        if (hospitals.isEmpty()) {
            return "未找到符合条件的医院，请尝试扩大搜索范围或使用不同关键词。";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("挂号提示：\n");
        sb.append("1. 三甲医院通常需要提前1-7天预约，建议通过医院官方公众号或APP挂号\n");
        sb.append("2. 急诊科24小时开放，紧急情况请拨打120急救电话\n");
        sb.append("3. 社区医院可处理常见病，排队时间短且医保报销比例更高\n");
        sb.append("4. 如症状严重（胸痛、呼吸困难、大出血等），请立即前往最近医院急诊科\n");
        return sb.toString();
    }
}

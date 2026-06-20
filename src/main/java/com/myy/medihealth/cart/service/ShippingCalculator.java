package com.myy.medihealth.cart.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.myy.medihealth.cart.entity.ShippingTemplate;
import com.myy.medihealth.cart.entity.Warehouse;
import com.myy.medihealth.cart.mapper.ShippingTemplateMapper;
import com.myy.medihealth.cart.mapper.WarehouseMapper;
import com.myy.medihealth.cart.vo.ShippingOptionVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 运费计算引擎 — 京东/阿里级物流计费
 *
 * 计费流程：
 *   1. 确定发货仓库（按 sku → warehouse 映射）
 *   2. 匹配店铺运费模板
 *   3. 按模板规则计算运费（首重+续重 / 首件+续件 / 满包邮）
 *   4. 跨仓运费合并规则：同仓合并计费，跨仓独立计费
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShippingCalculator {

    private final ShippingTemplateMapper templateMapper;
    private final WarehouseMapper warehouseMapper;

    /**
     * 计算一个结算分组的运费
     *
     * @param storeId        店铺 ID
     * @param warehouseId    首选仓库 ID（根据商品就近匹配）
     * @param totalAmount    商品小计（用于判断包邮门槛）
     * @param totalQuantity  总件数
     * @param totalWeightKg  总重量（kg）
     * @return 运费方案列表（通常 1-3 个选项）
     */
    public List<ShippingOptionVo> calculate(String storeId, String warehouseId,
                                             BigDecimal totalAmount, int totalQuantity,
                                             BigDecimal totalWeightKg) {
        // 查店铺运费模板
        ShippingTemplate template = templateMapper.selectOne(
                new LambdaQueryWrapper<ShippingTemplate>()
                        .eq(ShippingTemplate::getStoreId, storeId));

        // 查仓库信息
        Warehouse warehouse = warehouseId != null
                ? warehouseMapper.selectById(warehouseId) : null;

        List<ShippingOptionVo> options = new ArrayList<>();
        ShippingOptionVo option = new ShippingOptionVo();
        option.setWarehouseId(warehouseId);
        option.setWarehouseName(warehouse != null ? warehouse.getWarehouseName() : "默认仓");
        option.setEstimatedDays(estimateDays(warehouse));
        option.setRecommended(true);

        // 未配置模板 → 免运费
        if (template == null) {
            option.setShippingFee(BigDecimal.ZERO);
            options.add(option);
            return options;
        }

        BigDecimal fee = switch (template.getChargeType()) {
            case "FREE" -> BigDecimal.ZERO;
            case "AMOUNT" -> calcAmountFee(template, totalAmount);
            case "PIECE" -> calcPieceFee(template, totalQuantity);
            case "WEIGHT" -> calcWeightFee(template, totalWeightKg);
            default -> BigDecimal.ZERO;
        };

        option.setShippingFee(fee);
        options.add(option);
        return options;
    }

    /**
     * 跨仓合并运费计算
     *
     * 同城仓库 → 合并为一个包裹计费
     * 跨城仓库 → 独立计费后累加
     *
     * @param warehouseGroups 按仓库分组的 [warehouseId → (quantity, weight, amount)]
     * @return 总运费
     */
    public BigDecimal calculateMultiWarehouse(String storeId,
                                               Map<String, WarehouseGroup> warehouseGroups) {
        // 查所有仓库
        List<String> wIds = new ArrayList<>(warehouseGroups.keySet());
        List<Warehouse> warehouses = warehouseMapper.selectBatchIds(wIds);
        Map<String, String> cityMap = warehouses.stream()
                .collect(Collectors.toMap(Warehouse::getId, w ->
                        w.getCity() != null ? w.getCity() : ""));

        // 按城市分组合并
        Map<String, List<Map.Entry<String, WarehouseGroup>>> cityGroups =
                warehouseGroups.entrySet().stream()
                        .collect(Collectors.groupingBy(e ->
                                cityMap.getOrDefault(e.getKey(), e.getKey())));

        BigDecimal totalFee = BigDecimal.ZERO;
        for (var cityEntry : cityGroups.entrySet()) {
            // 同城合并：累加数量/金额/重量
            int totalQty = 0;
            BigDecimal totalAmt = BigDecimal.ZERO;
            BigDecimal totalWt = BigDecimal.ZERO;
            String firstWid = null;
            for (var e : cityEntry.getValue()) {
                totalQty += e.getValue().quantity;
                totalAmt = totalAmt.add(e.getValue().amount);
                totalWt = totalWt.add(e.getValue().weight);
                if (firstWid == null) firstWid = e.getKey();
            }
            List<ShippingOptionVo> opts = calculate(storeId, firstWid, totalAmt, totalQty, totalWt);
            totalFee = totalFee.add(opts.stream()
                    .map(ShippingOptionVo::getShippingFee)
                    .reduce(BigDecimal.ZERO, BigDecimal::add));
        }
        return totalFee;
    }

    // ---- 计费公式 ----

    /** 满金额包邮：达到门槛免邮，否则收固定运费 */
    private BigDecimal calcAmountFee(ShippingTemplate t, BigDecimal amount) {
        if (t.getFreeThreshold() != null && amount.compareTo(t.getFreeThreshold()) >= 0) {
            return BigDecimal.ZERO;
        }
        return t.getFirstFee() != null ? t.getFirstFee() : BigDecimal.ZERO;
    }

    /** 按件计费：首件X元 + ceil((件数-首件)/续件单位) × 续件费 */
    private BigDecimal calcPieceFee(ShippingTemplate t, int quantity) {
        if (quantity <= 0) return BigDecimal.ZERO;
        BigDecimal firstUnit = safeBigDecimal(t.getFirstUnit(), 1);
        BigDecimal firstFee = safeBigDecimal(t.getFirstFee(), 0);
        BigDecimal contUnit = safeBigDecimal(t.getContinueUnit(), 1);
        BigDecimal contFee = safeBigDecimal(t.getContinueFee(), 0);

        if (BigDecimal.valueOf(quantity).compareTo(firstUnit) <= 0) {
            return firstFee;
        }
        BigDecimal extraUnits = BigDecimal.valueOf(quantity).subtract(firstUnit);
        BigDecimal extraCharges = extraUnits.divide(contUnit, 0, RoundingMode.UP)
                .multiply(contFee);
        return firstFee.add(extraCharges);
    }

    /** 按重量计费：首重X元 + ceil((总重-首重)/续重) × 续重费 */
    private BigDecimal calcWeightFee(ShippingTemplate t, BigDecimal weightKg) {
        if (weightKg == null || weightKg.compareTo(BigDecimal.ZERO) <= 0) return BigDecimal.ZERO;
        BigDecimal firstUnit = safeBigDecimal(t.getFirstUnit(), 1);
        BigDecimal firstFee = safeBigDecimal(t.getFirstFee(), 0);
        BigDecimal contUnit = safeBigDecimal(t.getContinueUnit(), 1);
        BigDecimal contFee = safeBigDecimal(t.getContinueFee(), 0);

        if (weightKg.compareTo(firstUnit) <= 0) {
            return firstFee;
        }
        BigDecimal extraWeight = weightKg.subtract(firstUnit);
        BigDecimal extraCharges = extraWeight.divide(contUnit, 0, RoundingMode.UP)
                .multiply(contFee);
        return firstFee.add(extraCharges);
    }

    /** 预估配送天数（同城1天，同省2天，跨省3-5天） */
    private int estimateDays(Warehouse w) {
        // 简化：根据仓库位置和收货地址计算
        // 实际项目中需要调用地图API或维护距离矩阵
        if (w == null) return 3;
        return 2; // 默认省内2天
    }

    private BigDecimal safeBigDecimal(BigDecimal val, int defaultVal) {
        return val != null ? val : BigDecimal.valueOf(defaultVal);
    }

    /** 仓库分组数据 */
    public record WarehouseGroup(int quantity, BigDecimal amount, BigDecimal weight) {}
}

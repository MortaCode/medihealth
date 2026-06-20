package com.myy.medihealth.cart.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myy.medihealth.cart.entity.Coupon;
import com.myy.medihealth.cart.entity.Promotion;
import com.myy.medihealth.cart.mapper.CouponMapper;
import com.myy.medihealth.cart.mapper.PromotionMapper;
import com.myy.medihealth.cart.vo.CartGroupVo;
import com.myy.medihealth.cart.vo.CouponVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 促销引擎 — 京东/阿里级优惠计算
 *
 * 叠加规则（参照天猫规则）：
 *   1. 平台券 + 店铺券 = 可叠加
 *   2. 同类型券取最优（多张店铺券只取最优一张）
 *   3. 满减活动 + 折扣活动：满减先算，折扣后算
 *   4. 平台满减 + 店铺满减 = 可叠加
 *   5. 优惠分摊：按商品金额比例分摊到各 SKU
 *   6. 计算顺序：店铺满减 → 平台满减 → 店铺券 → 平台券
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PromotionEngine {

    private final CouponMapper couponMapper;
    private final PromotionMapper promotionMapper;
    private final ObjectMapper objectMapper;

    /**
     * 查询用户可用优惠券列表
     */
    public List<CouponVo> getAvailableCoupons(String userId) {
        List<Coupon> coupons = couponMapper.selectList(
                new LambdaQueryWrapper<Coupon>()
                        .eq(Coupon::getUserId, userId)
                        .eq(Coupon::getUsed, 0)
                        .lt(Coupon::getStartTime, LocalDateTime.now())
                        .gt(Coupon::getExpireTime, LocalDateTime.now()));

        return coupons.stream().map(c -> {
            CouponVo vo = new CouponVo();
            vo.setCouponId(c.getId());
            vo.setCouponName(c.getCouponName());
            vo.setCouponType(c.getCouponType());
            vo.setDiscountType(c.getDiscountType());
            vo.setThreshold(c.getThreshold());
            vo.setDiscountValue(c.getDiscountValue());
            vo.setExpireDesc("有效期至 " + c.getExpireTime().format(DateTimeFormatter.ofPattern("MM-dd HH:mm")));
            vo.setAvailable(true);
            return vo;
        }).toList();
    }

    /**
     * 匹配并计算一个结算分组的最优优惠组合
     *
     * @param group     结算分组（含商品明细和金额）
     * @param userId    用户 ID
     * @param allGroups 所有分组（用于平台券分配决策）
     */
    public void calculate(CartGroupVo group, String userId, List<CartGroupVo> allGroups) {
        BigDecimal itemAmount = group.getItemAmount();
        String storeId = group.getStoreId();

        // 1. 匹配店铺级促销活动（满减 / 满折）
        List<Promotion> storePromotions = promotionMapper.selectList(
                new LambdaQueryWrapper<Promotion>()
                        .eq(Promotion::getStoreId, storeId)
                        .eq(Promotion::getStatus, 1)
                        .lt(Promotion::getStartTime, LocalDateTime.now())
                        .gt(Promotion::getEndTime, LocalDateTime.now()));

        BigDecimal storeDiscount = calcBestPromotion(storePromotions, itemAmount, group.getItems().size());

        if (storeDiscount.compareTo(BigDecimal.ZERO) > 0) {
            CartGroupVo.DiscountDetail d = new CartGroupVo.DiscountDetail();
            d.setLabel("店铺满减");
            d.setAmount(storeDiscount);
            group.setStorePromotion(d);
        }

        // 2. 匹配平台级促销
        List<Promotion> platformPromotions = promotionMapper.selectList(
                new LambdaQueryWrapper<Promotion>()
                        .isNull(Promotion::getStoreId)
                        .eq(Promotion::getStatus, 1)
                        .lt(Promotion::getStartTime, LocalDateTime.now())
                        .gt(Promotion::getEndTime, LocalDateTime.now()));

        BigDecimal platformDiscount = calcBestPromotion(platformPromotions, itemAmount, group.getItems().size());

        if (platformDiscount.compareTo(BigDecimal.ZERO) > 0) {
            CartGroupVo.DiscountDetail d = new CartGroupVo.DiscountDetail();
            d.setLabel("平台满减");
            d.setAmount(platformDiscount);
            group.setPlatformPromotion(d);
        }

        // 3. 匹配优惠券（店铺券 → 平台券，各类取最优）
        BigDecimal afterPromoAmount = itemAmount.subtract(storeDiscount).subtract(platformDiscount);
        BigDecimal couponDiscount = calcBestCoupon(userId, storeId, group, afterPromoAmount);

        if (couponDiscount.compareTo(BigDecimal.ZERO) > 0) {
            CartGroupVo.DiscountDetail d = new CartGroupVo.DiscountDetail();
            d.setLabel("优惠券");
            d.setAmount(couponDiscount);
            group.setCouponDiscount(d);
        }

        // 4. 计算分组实付
        BigDecimal totalDiscount = storeDiscount.add(platformDiscount).add(couponDiscount);
        group.setGroupTotal(itemAmount.subtract(totalDiscount).add(
                group.getShippingFee() != null ? group.getShippingFee() : BigDecimal.ZERO));
    }

    // ---- 促销计算 ----

    /**
     * 从促销列表中取最优（取减免金额最大的）
     */
    private BigDecimal calcBestPromotion(List<Promotion> promotions, BigDecimal amount, int itemCount) {
        BigDecimal best = BigDecimal.ZERO;
        for (Promotion p : promotions) {
            try {
                List<PromotionRule> rules = objectMapper.readValue(p.getRules(),
                        new TypeReference<List<PromotionRule>>() {});
                for (PromotionRule rule : rules) {
                    BigDecimal threshold = BigDecimal.valueOf(rule.threshold);
                    BigDecimal value = BigDecimal.valueOf(rule.value);

                    // 判断门槛（threshold 可用于金额或件数）
                    boolean met = p.getPromotionType().equals("FULL_REDUCE")
                            || p.getPromotionType().equals("FULL_DISCOUNT")
                            ? amount.compareTo(threshold) >= 0
                            : itemCount >= rule.threshold;

                    if (met) {
                        BigDecimal discount;
                        if ("FULL_DISCOUNT".equals(p.getPromotionType())
                                || "N_PER_N_DISCOUNT".equals(p.getPromotionType())) {
                            // 折扣：amount × (1 - value)
                            discount = amount.multiply(BigDecimal.ONE.subtract(value));
                        } else {
                            // 满减：直接减 value
                            discount = value;
                        }
                        if (discount.compareTo(best) > 0) {
                            best = discount;
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("解析促销规则失败 promotionId={}", p.getId(), e);
            }
        }
        // 优惠不能超过商品金额
        return best.min(amount);
    }

    /**
     * 匹配最优优惠券（店铺券 → 平台券，各取最优，两者可叠加）
     */
    private BigDecimal calcBestCoupon(String userId, String storeId,
                                       CartGroupVo group, BigDecimal amount) {
        List<Coupon> allCoupons = couponMapper.selectList(
                new LambdaQueryWrapper<Coupon>()
                        .eq(Coupon::getUserId, userId)
                        .eq(Coupon::getUsed, 0)
                        .lt(Coupon::getStartTime, LocalDateTime.now())
                        .gt(Coupon::getExpireTime, LocalDateTime.now()));

        BigDecimal storeCouponBest = BigDecimal.ZERO;
        BigDecimal platformCouponBest = BigDecimal.ZERO;

        for (Coupon c : allCoupons) {
            if (amount.compareTo(c.getThreshold()) < 0) continue;

            BigDecimal benefit = calcCouponBenefit(c, amount);
            if ("STORE".equals(c.getCouponType()) && isApplicableToStore(c, storeId)) {
                if (benefit.compareTo(storeCouponBest) > 0) storeCouponBest = benefit;
            } else if ("PLATFORM".equals(c.getCouponType())) {
                if (benefit.compareTo(platformCouponBest) > 0) platformCouponBest = benefit;
            }
        }

        return storeCouponBest.add(platformCouponBest).min(amount);
    }

    private BigDecimal calcCouponBenefit(Coupon c, BigDecimal amount) {
        return "FULL_REDUCE".equals(c.getDiscountType())
                ? c.getDiscountValue()
                : amount.multiply(BigDecimal.ONE.subtract(c.getDiscountValue()));
    }

    private boolean isApplicableToStore(Coupon c, String storeId) {
        if (c.getApplicableStoreIds() == null || c.getApplicableStoreIds().isEmpty()) return true;
        try {
            List<String> ids = objectMapper.readValue(c.getApplicableStoreIds(),
                    new TypeReference<List<String>>() {});
            return ids.contains(storeId);
        } catch (Exception e) {
            return false;
        }
    }

    // ---- 内嵌 ----

    /** 促销规则条目 */
    private static class PromotionRule {
        public int threshold;
        public double value;
    }
}

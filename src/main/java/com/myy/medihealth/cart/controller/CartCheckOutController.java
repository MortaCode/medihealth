package com.myy.medihealth.cart.controller;

import com.myy.medihealth.cart.entity.CartItem;
import com.myy.medihealth.cart.service.CartCheckoutService;
import com.myy.medihealth.cart.service.CartService;
import com.myy.medihealth.cart.service.PromotionEngine;
import com.myy.medihealth.cart.vo.CheckoutResultVo;
import com.myy.medihealth.cart.vo.CouponVo;
import com.myy.medihealth.common.result.Result;
import com.myy.medihealth.login.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 购物车结算预览
 */
@RestController
@RequestMapping("medical/cart")
@RequiredArgsConstructor
public class CartCheckOutController {

    private final CartService cartService;
    private final CartCheckoutService checkoutService;
    private final PromotionEngine promotionEngine;
    private final UserService userService;

    /**
     * 购物车结算预览
     *
     * 返回：按店铺分组 → 运费计算 → 优惠匹配(满减/折扣/券) → 拆单建议 → 售后责任
     */
    @GetMapping("/checkout")
    public Result<CheckoutResultVo> checkout(HttpServletRequest request) {
        String userId = userService.getLoginUserId(request);
        return Result.success(checkoutService.checkout(userId));
    }

    /**
     * 获取用户可用优惠券列表
     */
    @GetMapping("/coupons")
    public Result<List<CouponVo>> coupons(HttpServletRequest request) {
        String userId = userService.getLoginUserId(request);
        return Result.success(promotionEngine.getAvailableCoupons(userId));
    }

    /**
     * 获取用户购物车统计（勾选件数 + 合计金额）
     */
    @GetMapping("/summary")
    public Result<CartSummaryVo> summary(HttpServletRequest request) {
        String userId = userService.getLoginUserId(request);
        List<CartItem> items = cartService.list(userId);
        long selectedCount = items.stream().filter(i -> i.getSelected() == 1).count();
        return Result.success(new CartSummaryVo(items.size(), (int) selectedCount));
    }

    /** 购物车摘要 VO */
    public record CartSummaryVo(int totalItems, int selectedItems) {}
}

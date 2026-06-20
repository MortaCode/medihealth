package com.myy.medihealth.cart.controller;

import com.myy.medihealth.cart.entity.CartItem;
import com.myy.medihealth.cart.service.CartCheckoutService;
import com.myy.medihealth.cart.service.CartService;
import com.myy.medihealth.cart.service.PromotionEngine;
import com.myy.medihealth.cart.vo.*;
import com.myy.medihealth.common.result.Result;
import com.myy.medihealth.login.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("medical/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;
    private final CartCheckoutService checkoutService;
    private final PromotionEngine promotionEngine;
    private final UserService userService;

    // ======================== CRUD ========================

    @GetMapping("/list")
    public Result<List<CartItem>> list(HttpServletRequest request) {
        String userId = userService.getLoginUserId(request);
        return Result.success(cartService.list(userId));
    }

    @PostMapping("/add")
    public Result<CartItem> add(@Valid @RequestBody CartAddVo vo, HttpServletRequest request) {
        String userId = userService.getLoginUserId(request);
        return Result.success(cartService.add(userId, vo.productId(), vo.quantity()));
    }

    @PutMapping("/item/{id}/qty/{quantity}")
    public Result<CartItem> updateQty(@PathVariable String id,
                                      @PathVariable int quantity,
                                      HttpServletRequest request) {
        String userId = userService.getLoginUserId(request);
        return Result.success(cartService.updateQuantity(userId, id, quantity));
    }

    @PutMapping("/item/{id}/toggle")
    public Result<CartItem> toggleSelect(@PathVariable String id,
                                         HttpServletRequest request) {
        String userId = userService.getLoginUserId(request);
        return Result.success(cartService.toggleSelect(userId, id));
    }

    @DeleteMapping("/item/{id}")
    public Result<String> remove(@PathVariable String id, HttpServletRequest request) {
        String userId = userService.getLoginUserId(request);
        cartService.remove(userId, id);
        return Result.success("删除成功");
    }
}

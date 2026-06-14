package com.myy.medihealth.cart.controller;

import com.myy.medihealth.cart.entity.CartItem;
import com.myy.medihealth.cart.service.CartService;
import com.myy.medihealth.cart.vo.CartAddVo;
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
    private final UserService userService;

    @GetMapping("/list")
    public Result<List<CartItem>> list(HttpServletRequest request) {
        String userId = userService.getLoginUserId(request);
        List<CartItem> items = cartService.list(userId);
        return Result.success(items);
    }

    @PostMapping("/add")
    public Result<CartItem> add(@Valid @RequestBody CartAddVo vo, HttpServletRequest request) {
        String userId = userService.getLoginUserId(request);
        CartItem cartItem = cartService.add(userId, vo.productId(), vo.quantity());
        return Result.success(cartItem);
    }

    @PutMapping("/item/{id}/qty/{quantity}")
    public Result<CartItem> updateQty(@PathVariable String id,
                                      @PathVariable int quantity,
                                      HttpServletRequest request) {
        String userId = userService.getLoginUserId(request);
        CartItem cartItem = cartService.updateQuantity(userId, id, quantity);
        return Result.success(cartItem);
    }

    @PutMapping("/item/{id}/toggle")
    public Result<CartItem> toggleSelect(@PathVariable String id,
                                         HttpServletRequest request) {
        String userId = userService.getLoginUserId(request);
        CartItem cartItem = cartService.toggleSelect(userId, id);
        return Result.success(cartItem);
    }

    @DeleteMapping("/item/{id}")
    public Result<String> remove(@PathVariable String id, HttpServletRequest request) {
        String userId = userService.getLoginUserId(request);
        cartService.remove(userId, id);
        return Result.success("删除成功");
    }
}

package com.myy.medihealth.payment.controller;

import com.myy.medihealth.common.result.Result;
import com.myy.medihealth.login.service.UserService;
import com.myy.medihealth.order.entity.Order;
import com.myy.medihealth.order.entity.OrderItem;
import com.myy.medihealth.payment.service.PaymentService;
import com.myy.medihealth.payment.vo.OrderSubmitV2Vo;
import com.myy.medihealth.payment.vo.OrderSubmitVo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("medical/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final UserService userService;

    @PostMapping("/submit")
    public Result<Order> submit(@RequestBody OrderSubmitVo vo, HttpServletRequest request) {
        String userId = userService.getLoginUserId(request);
        Order order = paymentService.createOrder(userId, vo);
        return Result.success(order);
    }

    @PostMapping("/submit/v2")
    public Result<Order> submitV2(@Valid @RequestBody OrderSubmitV2Vo vo, HttpServletRequest request) {
        String userId = userService.getLoginUserId(request);
        Order order = paymentService.createOrderV2(userId, vo);
        return Result.success(order);
    }

    @GetMapping("/order/{orderId}")
    public Result<Order> getOrder(@PathVariable String orderId, HttpServletRequest request) {
        String userId = userService.getLoginUserId(request);
        Order order = paymentService.getOrder(orderId, userId);
        return Result.success(order);
    }

    @GetMapping("/order/{orderId}/items")
    public Result<List<OrderItem>> getOrderItems(@PathVariable String orderId, HttpServletRequest request) {
        userService.getLoginUserId(request);
        List<OrderItem> items = paymentService.getOrderItems(orderId);
        return Result.success(items);
    }

    @PostMapping("/callback")
    public Result<String> callback(@RequestParam String orderId,
                                   @RequestParam String channel,
                                   @RequestParam String channelOrderNo) {
        paymentService.paySuccess(orderId, channel, channelOrderNo);
        return Result.success("支付成功");
    }
}

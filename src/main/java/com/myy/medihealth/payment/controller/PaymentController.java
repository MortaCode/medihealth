package com.myy.medihealth.payment.controller;

import com.myy.medihealth.common.result.Result;
import com.myy.medihealth.login.service.UserService;
import com.myy.medihealth.order.entity.Order;
import com.myy.medihealth.order.entity.OrderItem;
import com.myy.medihealth.payment.service.PaymentService;
import com.myy.medihealth.payment.vo.*;
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

    // ================================================================
    //  V3 全链路下单
    // ================================================================

    /**
     * 订单提交 V3 — 跨店拆单 + 全链路防重
     *
     * 不同店铺商品自动拆为独立订单，一笔提交可能生成多笔订单。
     * 所有店铺订单在同一事务中，全成功或全回滚。
     */
    @PostMapping("/submit/v3")
    public Result<OrderSubmitV3ResultVo> submitV3(@Valid @RequestBody OrderSubmitV3Vo vo,
                                                   HttpServletRequest request) {
        String userId = userService.getLoginUserId(request);
        OrderSubmitV3ResultVo result = paymentService.createOrderV3(userId, vo);
        return Result.success(result);
    }

    // ================================================================
    //  V1 / V2 (向后兼容)
    // ================================================================

    @PostMapping("/submit")
    public Result<Order> submit(@RequestBody OrderSubmitVo vo, HttpServletRequest request) {
        String userId = userService.getLoginUserId(request);
        return Result.success(paymentService.createOrder(userId, vo));
    }

    @PostMapping("/submit/v2")
    public Result<Order> submitV2(@Valid @RequestBody OrderSubmitV2Vo vo,
                                   HttpServletRequest request) {
        String userId = userService.getLoginUserId(request);
        return Result.success(paymentService.createOrderV2(userId, vo));
    }

    // ================================================================
    //  支付回调 / 订单查询
    // ================================================================

    /**
     * 支付回调 — 更新订单状态为已支付
     */
    @PostMapping("/callback")
    public Result<String> callback(@RequestParam String orderId,
                                   @RequestParam String channel,
                                   @RequestParam String channelOrderNo) {
        paymentService.paySuccess(orderId, channel, channelOrderNo);
        return Result.success("支付成功");
    }

    @GetMapping("/order/{orderId}")
    public Result<Order> getOrder(@PathVariable String orderId, HttpServletRequest request) {
        String userId = userService.getLoginUserId(request);
        return Result.success(paymentService.getOrder(orderId, userId));
    }

    @GetMapping("/order/{orderId}/items")
    public Result<List<OrderItem>> getOrderItems(@PathVariable String orderId,
                                                  HttpServletRequest request) {
        userService.getLoginUserId(request);
        return Result.success(paymentService.getOrderItems(orderId));
    }
}

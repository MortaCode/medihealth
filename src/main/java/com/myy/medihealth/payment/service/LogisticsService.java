package com.myy.medihealth.payment.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * 物流服务 — 模拟接口
 *
 * 真实场景：对接物流平台（顺丰/京东物流/菜鸟），创建运单、查询轨迹
 * 此处仅模拟调用效果
 */
@Slf4j
@Service
public class LogisticsService {

    /**
     * 创建发货单
     *
     * @param orderId      订单号
     * @param warehouseId  发货仓库 ID
     * @param address      收货地址
     * @param itemCount    商品件数
     * @return 物流单号
     */
    public String createShipment(String orderId, String warehouseId,
                                  String address, int itemCount) {
        String trackingNo = "SF" + System.currentTimeMillis();
        log.info("[物流] 模拟创建发货单 orderId={}, warehouse={}, address={}, items={}, trackingNo={}",
                orderId, warehouseId, address, itemCount, trackingNo);
        return trackingNo;
    }

    /**
     * 批量创建发货单（拆单场景）
     *
     * @param shipments 发货信息列表
     * @return 物流单号列表
     */
    public List<String> createShipments(List<ShipmentRequest> shipments) {
        return shipments.stream()
                .map(s -> createShipment(s.orderId, s.warehouseId, s.address, s.itemCount))
                .toList();
    }

    /**
     * 取消发货单
     */
    public void cancelShipment(String trackingNo) {
        log.info("[物流] 模拟取消发货单 trackingNo={}", trackingNo);
    }

    public record ShipmentRequest(String orderId, String warehouseId,
                                   String address, int itemCount) {}
}

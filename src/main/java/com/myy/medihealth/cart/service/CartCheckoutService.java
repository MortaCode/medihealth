package com.myy.medihealth.cart.service;

import com.myy.medihealth.cart.entity.CartItem;
import com.myy.medihealth.cart.entity.Store;
import com.myy.medihealth.cart.mapper.StoreMapper;
import com.myy.medihealth.cart.vo.*;
import com.myy.medihealth.product.entity.Product;
import com.myy.medihealth.product.service.ProductCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

/**
 * 购物车结算编排服务 — 京东/阿里级
 *
 * 结算链路：
 *   1. 获取用户已勾选的购物车项
 *   2. 批量加载商品信息（含店铺/仓库关联）
 *   3. 按店铺分组 → CartGroupVo
 *   4. 对每组：运费计算 + 促销匹配 + 优惠券抵扣
 *   5. 全局计算：平台券分配、总计汇总
 *   6. 拆单规则：生成 SplitOrderVo 建议列表
 *   7. 售后责任：按店铺分配
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CartCheckoutService {

    private final CartRedisService cartRedisService;
    private final ProductCacheService productCacheService;
    private final ShippingCalculator shippingCalculator;
    private final PromotionEngine promotionEngine;
    private final StoreMapper storeMapper;
    private final ExecutorService bizExecutor;

    /**
     * 购物车结算预览
     *
     * @param userId 用户 ID
     * @return 完整结算结果（分组 → 运费 → 优惠 → 拆单 → 售后）
     */
    public CheckoutResultVo checkout(String userId) {
        // 1. 获取购物车已勾选项（Redis 优先）
        Map<String, CartItem> cart = cartRedisService.getCart(userId);
        if (cart.isEmpty()) {
            cart = cartRedisService.loadFromDb(userId);
        }
        List<CartItem> selectedItems = cart.values().stream()
                .filter(item -> item.getSelected() == 1)
                .toList();

        CheckoutResultVo result = new CheckoutResultVo();
        if (selectedItems.isEmpty()) {
            result.setGroups(List.of());
            result.setSplitOrders(List.of());
            result.setUnavailableItems(List.of());
            return result;
        }

        // 2. 批量加载商品信息
        Set<String> productIds = selectedItems.stream()
                .map(CartItem::getProductId).collect(Collectors.toSet());
        Map<String, Product> productMap = productCacheService.getProducts(productIds);

        // 分离可购买 / 不可购买
        List<CartItem> availableItems = new ArrayList<>();
        List<CheckoutResultVo.UnavailableItem> unavailableItems = new ArrayList<>();

        for (CartItem item : selectedItems) {
            Product p = productMap.get(item.getProductId());
            if (p == null) {
                unavailableItems.add(buildUnavailable(item.getProductId(), "未知商品", "商品不存在"));
            } else if (p.getStatus() != 1) {
                unavailableItems.add(buildUnavailable(p.getId(), p.getName(), "商品已下架"));
            } else if (p.getStock() >= 0 && p.getStock() < item.getQuantity()) {
                unavailableItems.add(buildUnavailable(p.getId(), p.getName(),
                        "库存不足（剩余" + p.getStock() + "件）"));
            } else {
                availableItems.add(item);
            }
        }
        result.setUnavailableItems(unavailableItems);

        // 3. 批量加载店铺信息
        Set<String> storeIds = availableItems.stream()
                .map(item -> {
                    Product p = productMap.get(item.getProductId());
                    return p != null && p.getSpuId() != null ? deriveStoreId(p) : "store_default";
                })
                .collect(Collectors.toSet());
        Map<String, Store> storeMap = storeMapper.selectBatchIds(storeIds).stream()
                .collect(Collectors.toMap(Store::getId, s -> s));
        storeMap.putIfAbsent("store_default", buildDefaultStore());

        // 4. 按店铺分组
        Map<String, List<CartItem>> groupedByStore = availableItems.stream()
                .collect(Collectors.groupingBy(item -> {
                    Product p = productMap.get(item.getProductId());
                    return p != null && p.getSpuId() != null ? deriveStoreId(p) : "store_default";
                }, LinkedHashMap::new, Collectors.toList()));

        // 5. 构建分组 VO，并行计算运费和优惠
        List<CartGroupVo> groups = new ArrayList<>();
        BigDecimal totalItemAmount = BigDecimal.ZERO;

        for (var entry : groupedByStore.entrySet()) {
            String storeId = entry.getKey();
            Store store = storeMap.getOrDefault(storeId, buildDefaultStore());
            List<CartItem> items = entry.getValue();

            CartGroupVo group = buildGroup(store, items, productMap);
            groups.add(group);
            totalItemAmount = totalItemAmount.add(group.getItemAmount());
        }

        // 6. 并行计算每组的运费 + 优惠
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (CartGroupVo group : groups) {
            futures.add(CompletableFuture.runAsync(() -> {
                // 运费
                List<ShippingOptionVo> shipOpts = shippingCalculator.calculate(
                        group.getStoreId(),
                        group.getItems().isEmpty() ? null : group.getItems().get(0).getWarehouseId(),
                        group.getItemAmount(),
                        group.getItems().stream().mapToInt(CartGroupVo.GroupItem::getQuantity).sum(),
                        BigDecimal.ZERO); // 默认重量 0（实际需从商品属性获取）
                group.setShippingOptions(shipOpts);
                BigDecimal shippingFee = shipOpts.stream()
                        .filter(o -> o.getRecommended() != null && o.getRecommended())
                        .map(ShippingOptionVo::getShippingFee)
                        .findFirst().orElse(BigDecimal.ZERO);
                group.setShippingFee(shippingFee);

                // 优惠
                promotionEngine.calculate(group, userId, groups);
            }, bizExecutor));
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        // 7. 汇总
        BigDecimal totalShipping = groups.stream()
                .map(g -> g.getShippingFee() != null ? g.getShippingFee() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalDiscount = BigDecimal.ZERO;
        for (CartGroupVo g : groups) {
            if (g.getStorePromotion() != null) totalDiscount = totalDiscount.add(g.getStorePromotion().getAmount());
            if (g.getPlatformPromotion() != null) totalDiscount = totalDiscount.add(g.getPlatformPromotion().getAmount());
            if (g.getCouponDiscount() != null) totalDiscount = totalDiscount.add(g.getCouponDiscount().getAmount());
        }

        result.setTotalItemAmount(totalItemAmount);
        result.setTotalShippingFee(totalShipping);
        result.setTotalDiscount(totalDiscount);
        result.setActualAmount(totalItemAmount.subtract(totalDiscount).add(totalShipping));
        result.setGroups(groups);

        // 8. 拆单规则
        result.setSplitOrders(buildSplitOrders(groups));

        return result;
    }

    // ---- 分组构建 ----

    private CartGroupVo buildGroup(Store store, List<CartItem> items,
                                    Map<String, Product> productMap) {
        CartGroupVo group = new CartGroupVo();
        group.setStoreId(store.getId());
        group.setStoreName(store.getStoreName());
        group.setStoreType(store.getStoreType());
        group.setAfterSaleResponsible(store.getStoreName() + "（客服：" + store.getContactPhone() + "）");

        BigDecimal itemAmount = BigDecimal.ZERO;
        List<CartGroupVo.GroupItem> groupItems = new ArrayList<>();

        for (CartItem item : items) {
            Product p = productMap.get(item.getProductId());
            if (p == null) continue;

            CartGroupVo.GroupItem gi = new CartGroupVo.GroupItem();
            gi.setCartItemId(item.getId());
            gi.setProductId(p.getId());
            gi.setProductName(p.getName());
            gi.setProductImage(p.getImage());
            gi.setPrice(p.getPrice());
            gi.setQuantity(item.getQuantity());
            gi.setPrescriptionRequired(p.getPrescriptionRequired());
            // 仓库：从商品属性或 SPU 品牌推导
            gi.setWarehouseId(deriveWarehouseId(p));
            gi.setWarehouseName("默认仓");
            groupItems.add(gi);

            itemAmount = itemAmount.add(p.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
        }

        group.setItems(groupItems);
        group.setItemAmount(itemAmount);
        group.setGroupTotal(itemAmount); // 初始值，待运费+优惠后更新
        return group;
    }

    // ---- 拆单规则 ----

    /**
     * 应用拆单/合单规则生成建议订单列表
     * 规则（依次判断）：
     *   1. 不同店铺 → 必须拆单
     *   2. 含处方药 → 单独成单
     *   3. 不同仓库同城 → 可合单；跨城 → 建议拆单
     */
    private List<SplitOrderVo> buildSplitOrders(List<CartGroupVo> groups) {
        List<SplitOrderVo> orders = new ArrayList<>();
        int orderSeq = 1;

        for (CartGroupVo group : groups) {
            // 检查是否需要因为处方药/特殊商品拆分为多单
            boolean hasPrescription = group.getItems().stream()
                    .anyMatch(i -> i.getPrescriptionRequired() != null && i.getPrescriptionRequired() == 1);
            List<CartGroupVo.GroupItem> normalItems = group.getItems().stream()
                    .filter(i -> i.getPrescriptionRequired() == null || i.getPrescriptionRequired() == 0)
                    .toList();
            List<CartGroupVo.GroupItem> rxItems = group.getItems().stream()
                    .filter(i -> i.getPrescriptionRequired() != null && i.getPrescriptionRequired() == 1)
                    .toList();

            // 普通商品订单
            if (!normalItems.isEmpty()) {
                SplitOrderVo order = buildSplitOrder(group, normalItems, orderSeq++, "按店铺-" + group.getStoreName());
                order.setHasPrescription(false);
                orders.add(order);
            }

            // 处方药单独成单
            if (!rxItems.isEmpty()) {
                SplitOrderVo order = buildSplitOrder(group, rxItems, orderSeq++,
                        "含处方药-" + group.getStoreName());
                order.setHasPrescription(true);
                order.setEstimatedDelivery("需审核处方，预计1-2个工作日");
                orders.add(order);
            }
        }
        return orders;
    }

    private SplitOrderVo buildSplitOrder(CartGroupVo group, List<CartGroupVo.GroupItem> items, int seq, String reason) {
        SplitOrderVo order = new SplitOrderVo();
        order.setSuggestOrderNo("ORDER-" + group.getStoreId() + "-" + seq);
        order.setSplitReason(reason);
        order.setStoreId(group.getStoreId());
        order.setStoreName(group.getStoreName());
        BigDecimal itemAmount = items.stream()
                .map(i -> i.getPrice().multiply(BigDecimal.valueOf(i.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        order.setItemAmount(itemAmount);
        // 运费按比例分摊
        BigDecimal groupItemTotal = group.getItemAmount();
        if (groupItemTotal.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal ratio = itemAmount.divide(groupItemTotal, 4, java.math.RoundingMode.HALF_UP);
            order.setShippingFee(group.getShippingFee() != null
                    ? group.getShippingFee().multiply(ratio) : BigDecimal.ZERO);
            BigDecimal totalDiscount = BigDecimal.ZERO;
            if (group.getStorePromotion() != null) totalDiscount = totalDiscount.add(group.getStorePromotion().getAmount());
            if (group.getPlatformPromotion() != null) totalDiscount = totalDiscount.add(group.getPlatformPromotion().getAmount());
            if (group.getCouponDiscount() != null) totalDiscount = totalDiscount.add(group.getCouponDiscount().getAmount());
            order.setDiscount(totalDiscount.multiply(ratio));
        } else {
            order.setShippingFee(group.getShippingFee() != null ? group.getShippingFee() : BigDecimal.ZERO);
            order.setDiscount(BigDecimal.ZERO);
        }
        order.setPayAmount(order.getItemAmount().subtract(order.getDiscount()).add(order.getShippingFee()));
        order.setAfterSaleResponsible(group.getAfterSaleResponsible());
        order.setItems(items.stream().map(i -> {
            SplitOrderVo.SplitOrderItem si = new SplitOrderVo.SplitOrderItem();
            si.setProductId(i.getProductId());
            si.setProductName(i.getProductName());
            si.setPrice(i.getPrice());
            si.setQuantity(i.getQuantity());
            si.setWarehouseName(i.getWarehouseName());
            return si;
        }).toList());
        return order;
    }

    // ---- 辅助方法 ----
    /**
     * 从商品信息推导店铺 ID
     * 目前通过 SPU 的品牌 ID 映射到店铺，实际可能有专门的 store_id 字段
     */
    private String deriveStoreId(Product p) {
        // 简化：前5个 brand 映射为店铺
        if (p.getSpuId() != null) {
            return switch (p.getSpuId().substring(0, Math.min(4, p.getSpuId().length()))) {
                case "spu_" -> "store_001"; // 默认自营
                default -> "store_001";
            };
        }
        return "store_default";
    }

    private String deriveWarehouseId(Product p) {
        // 简化：根据商品 ID 哈希分配到仓库
        return "wh_001"; // 默认仓库
    }

    private Store buildDefaultStore() {
        Store s = new Store();
        s.setId("store_default");
        s.setStoreName("MediHealth 自营");
        s.setStoreType("SELF");
        s.setContactPhone("400-xxx-xxxx");
        return s;
    }

    private CheckoutResultVo.UnavailableItem buildUnavailable(String id, String name, String reason) {
        CheckoutResultVo.UnavailableItem ui = new CheckoutResultVo.UnavailableItem();
        ui.setProductId(id);
        ui.setProductName(name);
        ui.setReason(reason);
        return ui;
    }
}

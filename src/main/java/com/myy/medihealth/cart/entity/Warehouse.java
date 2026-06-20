package com.myy.medihealth.cart.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 仓库 — 发货履约节点
 *
 * 每个 SKU 绑定一个发货仓库。结算时按仓库分组决定物流方案：
 * 同城仓库可合单配送，跨城仓库拆单发货
 */
@Data
@TableName("t_warehouse")
public class Warehouse {

    @TableId
    private String id;

    /** 仓库名称 */
    private String warehouseName;

    /** 仓库地址（省/市/区） */
    private String province;
    private String city;
    private String district;
    private String address;

    /** 所属店铺 ID（自营仓=平台，第三方仓=对应店铺） */
    private String storeId;

    /** 覆盖区域：JSON 数组，如 ["北京","天津","河北"]；ALL=全国 */
    private String coverageArea;

    /** 状态：0=停用 1=启用 */
    private Integer status;
}

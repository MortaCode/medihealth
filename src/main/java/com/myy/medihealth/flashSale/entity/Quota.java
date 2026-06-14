package com.myy.medihealth.flashSale.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("t_quota")
public class Quota implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId
    private String id;

    /** 名额编号 */
    private String quotaId;

    /** 名额名称，例如"专家义诊号" */
    private String quotaName;

    /** 总名额数量 */
    private Integer totalQuota;

    /** 剩余名额数量 */
    private Integer remainingQuota;

    /** 乐观锁版本号 */
    @Version
    private Integer version;

    /** 更新时间 */
    private LocalDateTime updateTime;
}

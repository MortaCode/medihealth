package com.myy.medihealth.flashSale.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("t_quota_record")
public class QuotaRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId
    private String id;

    private String userId;

    private String quotaId;

    /** 来源：0-秒杀 1-MQ补偿 */
    private Integer source;

    private LocalDateTime createTime;
}

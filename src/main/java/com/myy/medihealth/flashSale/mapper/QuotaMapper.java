package com.myy.medihealth.flashSale.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.myy.medihealth.flashSale.entity.Quota;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface QuotaMapper extends BaseMapper<Quota> {

    /**
     * 使用乐观锁扣减名额。
     * 只有当 remaining_quota > 0 且版本匹配时才执行扣减。
     *
     * @param quotaId 名额编号
     * @param version 当前版本号（乐观锁）
     * @return 受影响行数（1=成功，0=失败）
     */
    @Update("UPDATE t_quota SET remaining_quota = remaining_quota - 1, version = version + 1 " +
            "WHERE quota_id = #{quotaId} AND remaining_quota > 0 AND version = #{version}")
    int deductWithOptimisticLock(@Param("quotaId") String quotaId, @Param("version") int version);
}

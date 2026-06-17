package com.myy.medihealth.thumb.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.myy.medihealth.thumb.entity.HealthArticle;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface HealthArticleMapper extends BaseMapper<HealthArticle> {

    @Update("UPDATE t_health_article SET like_count = like_count + #{delta}, update_time = NOW() WHERE id = #{id}")
    int incrLikeCount(@Param("id") String id, @Param("delta") Long delta);

    @Update("UPDATE t_health_article SET view_count = view_count + 1 WHERE id = #{id}")
    int incrViewCount(@Param("id") String id);
}

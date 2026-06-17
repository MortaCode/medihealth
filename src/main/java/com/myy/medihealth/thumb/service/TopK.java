package com.myy.medihealth.thumb.service;

import java.util.List;

/**
 * TopK 热门数据接口。
 */
public interface TopK {

    /**
     * 添加一条数据记录（累加计数）。
     *
     * @param key   数据键
     * @param count 增量计数
     */
    void add(String key, int count);

    /**
     * 获取前 K 个热门数据键。
     *
     * @param k 返回数量
     * @return 热门数据键列表（按热度降序）
     */
    List<String> top(int k);

    /**
     * 判断指定 key 是否在当前热门集合中。
     */
    boolean isHot(String key);
}

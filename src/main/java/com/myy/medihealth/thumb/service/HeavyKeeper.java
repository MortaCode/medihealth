package com.myy.medihealth.thumb.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * HeavyKeeper 算法实现 —— 数据流热门项检测。
 * <p>
 * 基于"HeavyKeeper: An Accurate Algorithm for Finding Top-k Elephant Flows"
 * 论文思想，使用二维桶数组 + 指数衰减 + 最小堆维护 Top 100 热门键。
 * </p>
 */
@Service
public class HeavyKeeper implements TopK {

    private static final Logger log = LoggerFactory.getLogger(HeavyKeeper.class);
    /** 桶深度（行数） */
    private static final int DEPTH = 4;
    /** 桶宽度（列数） */
    private static final int WIDTH = 500_000;
    /** 衰减系数 */
    private static final double DECAY = 0.9;

    /** 热门项上限 */
    private static final int TOP_K_MAX = 100;

    /** 二维桶数组：buckets[depth][width] */
    private final Bucket[][] buckets;
    /** 热门项最小堆（按 count 排序） */
    private final PriorityQueue<HotItem> minHeap;
    /** 热门项计数映射（用于去重和快速查找） */
    private final Map<String, Long> hotCountMap;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public HeavyKeeper() {
        this.buckets = new Bucket[DEPTH][WIDTH];
        for (int i = 0; i < DEPTH; i++) {
            for (int j = 0; j < WIDTH; j++) {
                buckets[i][j] = new Bucket();
            }
        }
        this.minHeap = new PriorityQueue<>(TOP_K_MAX, Comparator.comparingLong(HotItem::count));
        this.hotCountMap = new ConcurrentHashMap<>(TOP_K_MAX);
        log.info("HeavyKeeper 初始化完成 depth={}, width={}", DEPTH, WIDTH);
    }

    @Override
    public void add(String key, int count) {
        if (key == null || count <= 0) {
            return;
        }

        lock.readLock().lock();
        try {
            long fingerprint = hash(key);
            long minBucketCount = Long.MAX_VALUE;
            int minDepth = -1;

            // 查找最小计数的桶
            for (int d = 0; d < DEPTH; d++) {
                int idx = (int) ((fingerprint ^ (d * 0x9e3779b9L)) % WIDTH);
                if (idx < 0) idx += WIDTH;

                Bucket bucket = buckets[d][idx];
                if (bucket.key == null || bucket.key.equals(key)) {
                    // 空桶或相同键，直接累加
                    bucket.key = key;
                    bucket.count += count;
                    trackTopK(key, bucket.count);
                    return;
                }

                if (bucket.count < minBucketCount) {
                    minBucketCount = bucket.count;
                    minDepth = d;
                }
            }

            // 所有桶都被占用，对最小桶进行指数衰减后替换
            if (minDepth >= 0) {
                int idx = (int) ((fingerprint ^ (minDepth * 0x9e3779b9L)) % WIDTH);
                if (idx < 0) idx += WIDTH;

                Bucket bucket = buckets[minDepth][idx];
                bucket.count = (long) (bucket.count * DECAY);
                if (bucket.count <= 0) {
                    bucket.key = key;
                    bucket.count = count;
                }
                trackTopK(key, count);
            }
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public List<String> top(int k) {
        lock.readLock().lock();
        try {
            // 复制堆中数据并按计数降序排序
            List<HotItem> items = new ArrayList<>(minHeap);
            items.sort((a, b) -> Long.compare(b.count, a.count));

            List<String> result = new ArrayList<>();
            int limit = Math.min(k, items.size());
            for (int i = 0; i < limit; i++) {
                result.add(items.get(i).key);
            }
            return result;
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public boolean isHot(String key) {
        if (key == null) {
            return false;
        }
        lock.readLock().lock();
        try {
            return hotCountMap.containsKey(key);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 对所有桶执行衰减（定时调用）。
     */
    public void decayAll() {
        lock.writeLock().lock();
        try {
            for (int i = 0; i < DEPTH; i++) {
                for (int j = 0; j < WIDTH; j++) {
                    Bucket bucket = buckets[i][j];
                    if (bucket.key != null) {
                        bucket.count = (long) (bucket.count * DECAY);
                        if (bucket.count <= 0) {
                            bucket.key = null;
                            bucket.count = 0;
                        }
                    }
                }
            }
            log.debug("HeavyKeeper 全局衰减完成");
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 获取当前热门项数量。
     */
    public int getHotItemCount() {
        return minHeap.size();
    }

    // ---------- 内部方法 ----------

    /**
     * 跟踪热门项，维护最小堆。
     */
    private void trackTopK(String key, long count) {
        Long existing = hotCountMap.get(key);
        if (existing != null) {
            // 更新已有项
            hotCountMap.put(key, count);
            // 重建堆中的该项
            rebuildHeapItem(key, count);
        } else if (minHeap.size() < TOP_K_MAX) {
            // 堆未满，直接加入
            HotItem item = new HotItem(key, count);
            minHeap.offer(item);
            hotCountMap.put(key, count);
        } else {
            // 堆已满，与堆顶（最小值）比较
            HotItem peek = minHeap.peek();
            if (peek != null && count > peek.count) {
                HotItem removed = minHeap.poll();
                if (removed != null) {
                    hotCountMap.remove(removed.key);
                }
                HotItem item = new HotItem(key, count);
                minHeap.offer(item);
                hotCountMap.put(key, count);
            }
        }
    }

    private void rebuildHeapItem(String key, long newCount) {
        // 移除旧项并重新插入（PriorityQueue 无直接更新方法）
        minHeap.removeIf(item -> item.key.equals(key));
        minHeap.offer(new HotItem(key, newCount));
    }

    /**
     * FNV-1a 哈希（64位）。
     */
    private long hash(String key) {
        long hash = 0xcbf29ce484222325L;
        for (int i = 0; i < key.length(); i++) {
            hash ^= key.charAt(i);
            hash *= 0x100000001b3L;
        }
        return hash;
    }

    // ---------- 内部类 ----------

    /** 桶结构 */
    static class Bucket {
        String key;
        long count;
    }

    /** 热门项结构 */
    static class HotItem {
        final String key;
        final long count;

        HotItem(String key, long count) {
            this.key = key;
            this.count = count;
        }

        long count() {
            return count;
        }
    }
}

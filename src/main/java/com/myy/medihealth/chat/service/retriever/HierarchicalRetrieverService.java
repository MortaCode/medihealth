package com.myy.medihealth.chat.service.retriever;

import com.myy.medihealth.common.config.HybridRetrievalConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 分级检索服务 - 两阶段检索策略
 * 第一阶段：BM25 快速召回（高召回率，候选集大）
 * 第二阶段：向量重排序（高精度，对候选集精确评分）
 * <p>
 * 这种分级策略在保证检索质量的同时，大幅减少向量计算开销
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HierarchicalRetrieverService {

    private final Bm25Retriever bm25Retriever;
    private final HybridRetrievalConfig config;

    /**
     * 第一阶段检索的候选集倍数（相对于最终结果数）
     */
    private static final int FIRST_STAGE_MULTIPLIER = 5;

    /**
     * 两阶段分级检索
     *
     * @param query 查询字符串
     * @param topK  最终返回结果数量
     * @return 重排序后的搜索结果
     */
    public List<SearchResult> search(String query, int topK) {
        if (query == null || query.isBlank()) {
            return Collections.emptyList();
        }

        int finalTopK = topK > 0 ? topK : config.getFinalTopK();
        int candidateSize = finalTopK * FIRST_STAGE_MULTIPLIER;

        // ============ 第一阶段：BM25 快速召回 ============
        List<Bm25Retriever.SearchResult> bm25Results = bm25Retriever.search(query, candidateSize);

        if (bm25Results.isEmpty()) {
            log.debug("Hierarchical retrieval: no BM25 results for '{}'", query);
            return Collections.emptyList();
        }

        log.debug("Hierarchical stage-1: BM25 recalled {} candidates for '{}'",
                bm25Results.size(), query);

        // ============ 第二阶段：向量重排序 ============
        List<ReRankedResult> reRanked = vectorReRank(query, bm25Results);

        // 如果向量重排序不可用，直接使用 BM25 结果
        if (reRanked.isEmpty()) {
            log.debug("Hierarchical stage-2: vector re-rank unavailable, using BM25 scores");
            return bm25Results.stream()
                    .limit(finalTopK)
                    .map(r -> new SearchResult(r.id(), r.content(), r.source(),
                            r.normalizedScore(), getBm25Rank(r.id(), bm25Results),
                            "bm25_only"))
                    .collect(Collectors.toList());
        }

        // 按重排序得分降序排列，取 topK
        List<ReRankedResult> sorted = reRanked.stream()
                .sorted(Comparator.comparingDouble(ReRankedResult::rerankScore).reversed())
                .limit(finalTopK)
                .toList();

        // 构建最终结果
        List<SearchResult> results = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            ReRankedResult rr = sorted.get(i);
            results.add(new SearchResult(
                    rr.bm25Result().id(),
                    rr.bm25Result().content(),
                    rr.bm25Result().source(),
                    rr.rerankScore(),
                    i + 1,
                    "hierarchical"
            ));
        }

        log.info("Hierarchical retrieval '{}' returned {} results (candidates: {}, re-ranked: {})",
                query, results.size(), bm25Results.size(), reRanked.size());

        return results;
    }

    /**
     * 向量重排序 - 对候选集进行精确语义评分
     * 当前为预留实现，后续可接入 Cross-Encoder 或双塔模型
     *
     * @param query    查询字符串
     * @param candidates BM25 召回的候选集
     * @return 重排序后的结果列表
     */
    private List<ReRankedResult> vectorReRank(String query, List<Bm25Retriever.SearchResult> candidates) {
        // 预留：后续接入向量重排序模型
        // 可选方案：
        // 1. Cross-Encoder 模型（如 BGE-Reranker）计算 query-doc 相关性
        // 2. 双塔模型将 query 和 docs 编码为向量后计算余弦相似度
        // 3. 使用 Spring AI 的 VectorStore 进行精确匹配
        log.debug("Vector re-rank not yet configured, {} candidates pending",
                candidates.size());
        return Collections.emptyList();
    }

    /**
     * 获取 BM25 排名
     */
    private int getBm25Rank(String id, List<Bm25Retriever.SearchResult> results) {
        for (int i = 0; i < results.size(); i++) {
            if (results.get(i).id().equals(id)) {
                return i + 1;
            }
        }
        return results.size();
    }

    /**
     * 分级检索 - 带多样性控制
     * 确保返回结果覆盖不同的知识来源，避免信息冗余
     *
     * @param query                       查询字符串
     * @param topK                        返回结果数量
     * @param maxResultsPerSource         每个来源的最大结果数
     * @return 多样性控制后的搜索结果
     */
    public List<SearchResult> searchWithDiversity(String query, int topK, int maxResultsPerSource) {
        List<SearchResult> primary = search(query, topK);

        if (primary.size() <= topK) {
            return primary;
        }

        // 按来源控制多样性
        Map<String, List<SearchResult>> bySource = new LinkedHashMap<>();
        List<SearchResult> diverse = new ArrayList<>();

        for (SearchResult result : primary) {
            String source = result.source() != null ? result.source() : "unknown";
            List<SearchResult> sourceList = bySource.computeIfAbsent(source, k -> new ArrayList<>());

            if (sourceList.size() < maxResultsPerSource) {
                sourceList.add(result);
                diverse.add(result);
            }

            if (diverse.size() >= topK) {
                break;
            }
        }

        log.debug("Diversity control: {} results -> {} diverse results (max {}/source)",
                primary.size(), diverse.size(), maxResultsPerSource);

        return diverse;
    }

    /**
     * 重排序结果记录
     */
    private record ReRankedResult(Bm25Retriever.SearchResult bm25Result, double rerankScore) {
    }

    /**
     * 搜索结果记录
     */
    public record SearchResult(String id, String content, String source,
                                double score, int rank, String retrievalMethod) {
    }
}

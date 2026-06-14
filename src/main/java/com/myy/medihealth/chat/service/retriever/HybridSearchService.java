package com.myy.medihealth.chat.service.retriever;

import com.myy.medihealth.common.config.HybridRetrievalConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 混合搜索服务 - 组合向量搜索和 BM25 关键词搜索
 * 使用加权融合（Weighted Fusion）将两路结果合并排序
 * <p>
 * 当前实现：BM25 为主，向量搜索预留
 * 后续可接入向量数据库（Milvus / Elasticsearch）实现语义搜索
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HybridSearchService {

    private final Bm25Retriever bm25Retriever;
    private final HybridRetrievalConfig config;

    /**
     * 混合搜索 - BM25 + 向量加权融合
     *
     * @param query 查询字符串
     * @param topK  返回结果数量
     * @return 按融合得分降序排列的搜索结果
     */
    public List<SearchResult> search(String query, int topK) {
        if (query == null || query.isBlank()) {
            return Collections.emptyList();
        }

        int finalTopK = topK > 0 ? topK : config.getFinalTopK();

        // 1. BM25 关键词搜索
        int bm25TopK = Math.max(config.getBm25TopK(), finalTopK * 2);
        List<Bm25Retriever.SearchResult> bm25Results = bm25Retriever.search(query, bm25TopK);
        Map<String, Float> bm25Scores = bm25Results.stream()
                .collect(Collectors.toMap(
                        Bm25Retriever.SearchResult::id,
                        Bm25Retriever.SearchResult::normalizedScore,
                        (a, b) -> a,
                        HashMap::new
                ));

        // 2. 向量搜索（预留，当前返回空）
        Map<String, Float> vectorScores = vectorSearch(query, config.getVectorTopK());

        // 3. 加权融合
        Map<String, Double> fusionScores = weightedFusion(bm25Scores, vectorScores);

        // 4. 排序并取 topK
        List<String> rankedIds = fusionScores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(finalTopK)
                .map(Map.Entry::getKey)
                .toList();

        // 5. 构建结果列表
        Map<String, Bm25Retriever.SearchResult> bm25ResultMap = bm25Results.stream()
                .collect(Collectors.toMap(Bm25Retriever.SearchResult::id, r -> r, (a, b) -> a));

        List<SearchResult> results = new ArrayList<>();
        for (String id : rankedIds) {
            double fusionScore = fusionScores.getOrDefault(id, 0.0);
            Integer vectorRank = null;
            int rank = 1;
            for (Map.Entry<String, Double> entry : fusionScores.entrySet()) {
                if (entry.getKey().equals(id)) {
                    break;
                }
                rank++;
            }

            Bm25Retriever.SearchResult bm25Result = bm25ResultMap.get(id);
            if (bm25Result != null) {
                results.add(new SearchResult(
                        bm25Result.id(),
                        bm25Result.content(),
                        bm25Result.source(),
                        fusionScore,
                        rank,
                        "hybrid"
                ));
            }
        }

        log.debug("Hybrid search '{}' returned {} results (BM25: {}, Vector: {})",
                query, results.size(), bm25Results.size(), vectorScores.size());

        return results;
    }

    /**
     * 向量搜索（预留实现）
     * 后续可接入 Spring AI VectorStore 或直接调用向量数据库
     *
     * @param query 查询字符串
     * @param topK  返回数量
     * @return 文档ID → 归一化得分的映射
     */
    private Map<String, Float> vectorSearch(String query, int topK) {
        // 预留：后续接入向量数据库
        // 可使用的 Spring AI 接口：
        // - VectorStore.similaritySearch(SearchRequest.query(query).withTopK(topK))
        // - 或将 query 转为向量后调用 Milvus / Qdrant / Elasticsearch
        log.debug("Vector search not yet configured, returning empty results");
        return Collections.emptyMap();
    }

    /**
     * 加权融合 - 将 BM25 和向量得分按权重合并
     *
     * @param bm25Scores   BM25 得分映射
     * @param vectorScores 向量得分映射
     * @return 文档ID → 融合得分的映射
     */
    private Map<String, Double> weightedFusion(Map<String, Float> bm25Scores,
                                                Map<String, Float> vectorScores) {
        Map<String, Double> fusionScores = new HashMap<>();
        Set<String> allIds = new HashSet<>();
        allIds.addAll(bm25Scores.keySet());
        allIds.addAll(vectorScores.keySet());

        double bm25Weight = config.getBm25Weight();
        double vectorWeight = config.getVectorWeight();

        for (String id : allIds) {
            double bm25Score = bm25Scores.getOrDefault(id, 0.0f);
            double vectorScore = vectorScores.getOrDefault(id, 0.0f);
            double fusionScore = bm25Weight * bm25Score + vectorWeight * vectorScore;

            // 如果向量搜索未配置，只使用 BM25 得分
            if (vectorScores.isEmpty()) {
                fusionScore = bm25Score;
            }

            fusionScores.put(id, fusionScore);
        }

        return fusionScores;
    }

    /**
     * 混合搜索 - 带相关性阈值过滤
     *
     * @param query 查询字符串
     * @param topK  返回结果数量
     * @return 过滤后的搜索结果
     */
    public List<SearchResult> searchWithThreshold(String query, int topK) {
        List<SearchResult> results = search(query, topK);
        double threshold = config.getRelevanceThreshold();

        return results.stream()
                .filter(r -> r.score() >= threshold)
                .collect(Collectors.toList());
    }

    /**
     * 搜索结果记录
     */
    public record SearchResult(String id, String content, String source,
                                double score, int rank, String retrievalMethod) {
    }
}

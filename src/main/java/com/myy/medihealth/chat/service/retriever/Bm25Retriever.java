package com.myy.medihealth.chat.service.retriever;

import com.myy.medihealth.chat.service.handler.MarkdownReaderHandler;
import com.myy.medihealth.chat.service.handler.RecursiveSplitHandler;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.analysis.cn.smart.SmartChineseAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;

/**
 * Lucene BM25 关键词检索器
 * 基于内存索引的医学知识库 BM25 搜索
 */
@Slf4j
@Component
public class Bm25Retriever {

    private static final String FIELD_ID = "id";
    private static final String FIELD_CONTENT = "content";
    private static final String FIELD_SOURCE = "source";
    private static final String FIELD_HEADER = "header";

    private final MarkdownReaderHandler markdownReaderHandler;
    private final RecursiveSplitHandler recursiveSplitHandler;

    @Getter
    private Directory indexDirectory;
    @Getter
    private volatile boolean indexReady = false;

    public Bm25Retriever(MarkdownReaderHandler markdownReaderHandler,
                         RecursiveSplitHandler recursiveSplitHandler) {
        this.markdownReaderHandler = markdownReaderHandler;
        this.recursiveSplitHandler = recursiveSplitHandler;
    }

    /**
     * 系统启动时构建内存索引
     */
    @PostConstruct
    public void init() {
        try {
            buildIndex();
        } catch (Exception e) {
            log.error("Failed to build BM25 index on startup", e);
        }
    }

    /**
     * 从医学文档构建内存索引
     */
    public void buildIndex() {
        try {
            // 获取并分割文档
            List<Document> rawDocs = markdownReaderHandler.getAllDocuments();
            if (rawDocs.isEmpty()) {
                log.warn("No documents available for BM25 indexing");
                return;
            }

            List<Document> chunks = recursiveSplitHandler.recursiveSplit(rawDocs);
            log.info("Building BM25 index from {} document chunks...", chunks.size());

            // 创建内存索引
            Directory directory = new ByteBuffersDirectory();
            SmartChineseAnalyzer analyzer = new SmartChineseAnalyzer();
            IndexWriterConfig config = new IndexWriterConfig(analyzer);
            config.setOpenMode(IndexWriterConfig.OpenMode.CREATE);

            try (IndexWriter writer = new IndexWriter(directory, config)) {
                int indexed = 0;
                for (Document chunk : chunks) {
                    if (chunk.getText() == null || chunk.getText().isBlank()) {
                        continue;
                    }

                    org.apache.lucene.document.Document luceneDoc = new org.apache.lucene.document.Document();

                    // 文档ID
                    luceneDoc.add(new StringField(FIELD_ID, chunk.getId(), Field.Store.YES));

                    // 文档内容（索引 + 存储）
                    luceneDoc.add(new TextField(FIELD_CONTENT, chunk.getText(), Field.Store.YES));

                    // 来源文件
                    String source = (String) chunk.getMetadata().getOrDefault("source", "unknown");
                    luceneDoc.add(new StringField(FIELD_SOURCE, source, Field.Store.YES));

                    // 标题/章节
                    String header = (String) chunk.getMetadata().getOrDefault("header", "");
                    if (!header.isEmpty()) {
                        luceneDoc.add(new StringField(FIELD_HEADER, header, Field.Store.YES));
                    }

                    // 类型标识
                    String type = (String) chunk.getMetadata().getOrDefault("type", "medical_knowledge");
                    luceneDoc.add(new StringField("type", type, Field.Store.YES));

                    writer.addDocument(luceneDoc);
                    indexed++;
                }

                writer.commit();
                log.info("BM25 index built successfully: {} documents indexed", indexed);
            }

            this.indexDirectory = directory;
            this.indexReady = true;

        } catch (IOException e) {
            log.error("Failed to build BM25 index", e);
            this.indexReady = false;
        }
    }

    /**
     * BM25 关键词搜索
     *
     * @param query 查询字符串
     * @param topK  返回结果数量
     * @return 搜索结果（按 BM25 得分降序排列）
     */
    public List<SearchResult> search(String query, int topK) {
        if (!indexReady || indexDirectory == null) {
            log.warn("BM25 index not ready, rebuilding...");
            try {
                buildIndex();
            } catch (Exception e) {
                log.error("Failed to rebuild index", e);
                return Collections.emptyList();
            }
            if (!indexReady) {
                return Collections.emptyList();
            }
        }

        if (query == null || query.isBlank()) {
            return Collections.emptyList();
        }

        try {
            SmartChineseAnalyzer analyzer = new SmartChineseAnalyzer();
            DirectoryReader reader = DirectoryReader.open(indexDirectory);
            IndexSearcher searcher = new IndexSearcher(reader);

            // 使用 BM25 相似度（Lucene 9.x 默认）
            searcher.setSimilarity(new BM25Similarity());

            // 构建查询
            QueryParser parser = new QueryParser(FIELD_CONTENT, analyzer);
            parser.setDefaultOperator(QueryParser.Operator.OR);
            Query luceneQuery = parser.parse(QueryParser.escape(query));

            // 执行搜索
            int actualTopK = Math.min(topK, reader.numDocs());
            TopDocs topDocs = searcher.search(luceneQuery, actualTopK);

            // 收集结果
            List<SearchResult> results = new ArrayList<>();
            float maxScore = topDocs.scoreDocs.length > 0 ? topDocs.scoreDocs[0].score : 0;

            for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
                org.apache.lucene.document.Document doc = searcher.storedFields().document(scoreDoc.doc);
                // 归一化得分
                float normalizedScore = maxScore > 0 ? scoreDoc.score / maxScore : 0;

                SearchResult result = new SearchResult(
                        doc.get(FIELD_ID),
                        doc.get(FIELD_CONTENT),
                        doc.get(FIELD_SOURCE),
                        normalizedScore,
                        scoreDoc.score
                );
                results.add(result);
            }

            reader.close();
            log.debug("BM25 search '{}' returned {} results", query, results.size());
            return results;

        } catch (Exception e) {
            log.error("BM25 search failed for query: {}", query, e);
            return Collections.emptyList();
        }
    }

    /**
     * 获取索引文档总数
     *
     * @return 文档数量
     */
    public int getIndexSize() {
        if (!indexReady || indexDirectory == null) {
            return 0;
        }
        try (DirectoryReader reader = DirectoryReader.open(indexDirectory)) {
            return reader.numDocs();
        } catch (IOException e) {
            log.error("Failed to get index size", e);
            return 0;
        }
    }

    /**
     * 搜索结果记录
     */
    public record SearchResult(String id, String content, String source,
                                float normalizedScore, float rawScore) {
    }
}

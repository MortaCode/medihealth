package com.myy.medihealth.chat.service.handler;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Markdown 文档读取处理器
 * 从 resources/document/ 目录读取医学知识库 Markdown 文档
 * 解析为 Spring AI Document 对象供 RAG 检索使用
 */
@Slf4j
@Service
public class MarkdownReaderHandler {

    private final ResourcePatternResolver resourcePatternResolver;
    private final RecursiveSplitHandler recursiveSplitHandler;

    private final List<Document> documents = new ArrayList<>();

    public MarkdownReaderHandler(ResourcePatternResolver resourcePatternResolver, RecursiveSplitHandler recursiveSplitHandler) {
        this.resourcePatternResolver = resourcePatternResolver;
        this.recursiveSplitHandler = recursiveSplitHandler;
    }

    /**
     * 系统启动时加载所有 Markdown 文档
     */
    @PostConstruct
    public void init() {
        loadMarkdown();
        log.info("Loaded {} medical documents from resources/document/", documents.size());
    }

    /**
     * 从 classpath 加载并分割所有 Markdown 文档
     */
    public List<Document> loadMarkdown() {
        List<Document> list = new ArrayList<>();
        try {
            Resource[] resources = resourcePatternResolver.getResources("classpath:document/*.md");
            for (Resource resource : resources) {
                String filename = resource.getFilename();
                MarkdownDocumentReaderConfig config = MarkdownDocumentReaderConfig.builder()
                        .withHorizontalRuleCreateDocument(true)
                        .withIncludeCodeBlock(true)
                        .withIncludeBlockquote(true)
                        .withAdditionalMetadata("filename", filename)
                        .withAdditionalMetadata("type", "medical_knowledge")
                        .build();
                MarkdownDocumentReader reader = new MarkdownDocumentReader(resource, config);
                // 超过目标Token大小，递归分块
                List<Document> splitDocument = recursiveSplitHandler.recursiveSplit(reader.get());
                list.addAll(splitDocument);
            }
        } catch (IOException e) {
            log.error("Markdown加载失败={}", e);
            throw new RuntimeException(e);
        }
        this.documents.clear();
        this.documents.addAll(list);
        return list;
    }

    /**
     * 获取所有已加载的文档（已分割）
     *
     * @return 文档列表（不可修改）
     */
    public List<Document> getAllDocuments() {
        return Collections.unmodifiableList(documents);
    }

    /**
     * 获取文档总数
     *
     * @return 文档数量
     */
    public int getDocumentCount() {
        return documents.size();
    }

    /**
     * 按来源文件名过滤文档
     *
     * @param sourceFilename 来源文件名
     * @return 匹配的文档列表
     */
    public List<Document> getDocumentsBySource(String sourceFilename) {
        return documents.stream()
                .filter(doc -> sourceFilename.equals(doc.getMetadata().get("filename")))
                .toList();
    }

    /**
     * 按类型过滤文档
     *
     * @param type 文档类型
     * @return 匹配的文档列表
     */
    public List<Document> getDocumentsByType(String type) {
        return documents.stream()
                .filter(doc -> type.equals(doc.getMetadata().get("type")))
                .toList();
    }

    /**
     * 重新加载所有文档（用于热更新）
     */
    public void reload() {
        log.info("Reloading medical documents...");
        loadMarkdown();
    }
}

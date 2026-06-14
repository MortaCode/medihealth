package com.myy.medihealth.chat.service.handler;

import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.regex.Pattern;

@Component
public class RecursiveSplitHandler {

    private static final int TARGET_CHUNK_SIZE = 512;   // 目标token数

    // 分隔符优先级：段落 > 句子 > 分句 > 单词
    private final String[][] separators = {
            {"\n\n", "paragraph"},      // 段落分隔符
            {"。", "sentence"},         // 句子分隔符(中文)
            {".", "sentence"},          // 句子分隔符(英文)
            {"，", "clause"},           // 分句分隔符(中文)
            {",", "clause"},            // 分句分隔符(英文)
            {" ", "word"}               // 单词分隔符
    };

    /**
     * 递归分割超大块
     * @param documents 待分割的文档列表
     * @return 分割后的文档列表
     */
    /**
     * splitAll 是 recursiveSplit 的别名，方便外部调用
     */
    public List<Document> splitAll(List<Document> documents) {
        return recursiveSplit(documents);
    }

    public List<Document> recursiveSplit(List<Document> documents) {
        List<Document> result = new ArrayList<>();
        for (Document doc : documents) {
            if (estimateTokenCount(doc.getText()) <= TARGET_CHUNK_SIZE) {
                result.add(doc);
            } else {
                result.addAll(recursiveSplitDocument(doc));
            }
        }
        return result;
    }

    /**
     * 递归分割单个文档
     */
    private List<Document> recursiveSplitDocument(Document document) {

        // 小于目标token大小，直接返回
        if (estimateTokenCount(document.getText()) <= TARGET_CHUNK_SIZE) {
            return Collections.singletonList(document);
        }

        // 分隔符分块
        for (String[] separatorInfo : separators) {
            String separator = separatorInfo[0];
            String level = separatorInfo[1];
            if (separator.isEmpty()) continue;

            /**
             * 尝试使用分隔符A（如段落分隔符）
             *     ├─ 如果成功（所有块 ≤ 目标大小）→ 返回
             *     └─ 如果存在超大块 → 降级使用更细粒度的分隔符B（如句子分隔符）
             *          └─ 递归处理超大块
             */
            String[] parts = document.getText().split(Pattern.quote(separator), -1);
            if (parts.length > 1) {
                List<Document> splitResult = trySplitWithSeparator(document, parts, separator, level);//贪心算法：尽可能填满目标token值
                if (!CollectionUtils.isEmpty(splitResult)) {
                    List<Document> finalResult = new ArrayList<>();
                    for (Document subDoc : splitResult) {
                        if (estimateTokenCount(subDoc.getText()) > TARGET_CHUNK_SIZE) {
                            finalResult.addAll(recursiveSplitDocument(subDoc));// 如果子文档仍然过大，递归处理
                        } else {
                            finalResult.add(subDoc);
                        }
                    }
                    return finalResult;  //退出循环，不在分块
                }
            }
        }

        /**
         * 精确字符切分（兜底）
         *     └─ 尝试在边界附近寻找更优切点
         *          ├─ 优先：空格（英语等空格分隔语言）
         *          └─ 备选：标点符号（中英文标点）
         * 应对情况：①完全没有分隔符，②分割成至少2个部分[parts.length > 1], ③所有分隔符分割后都有超大块
         */
        return splitByCharacterExact(document);
    }

    /**
     * 尝试使用指定分隔符拆分文档
     */
    private List<Document> trySplitWithSeparator(Document document, String[] parts,
                                                 String separator, String level) {
        List<Document> result = new ArrayList<>();
        StringBuilder currentChunk = new StringBuilder();
        int currentTokenCount = 0;

        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];

            // 空
            if (part.trim().isEmpty() && i < parts.length - 1) {
                if (currentChunk.length() > 0) {
                    currentChunk.append(separator);
                }
                continue;
            }

            //  估值token
            int partTokenCount = estimateTokenCount(part);

            // 单个part已经大于目标token值
            if (partTokenCount > TARGET_CHUNK_SIZE) {
                // 先保存当前累积的块
                if (currentChunk.length() > 0) {
                    result.add(createDocument(document, currentChunk.toString().trim(), level));
                    currentChunk = new StringBuilder();
                    currentTokenCount = 0;
                }
                // 将这个超大块作为独立文档添加，递归时会进一步处理
                result.add(createDocument(document, part, level + "_needs_finer"));
                continue;
            }

            // 合并token情况
            if (currentTokenCount + partTokenCount > TARGET_CHUNK_SIZE &&
                    currentChunk.length() > 0) {
                result.add(createDocument(document, currentChunk.toString().trim(), level));
                currentChunk = new StringBuilder();
                currentChunk.append(part);
                currentTokenCount = partTokenCount;
            } else {
                // 添加到当前块
                if (currentChunk.length() > 0) {
                    currentChunk.append(separator);
                }
                currentChunk.append(part);
                currentTokenCount += partTokenCount;
            }
        }

        // 添加最后一个块[上面都是add上一个的]
        if (currentChunk.length() > 0) {
            result.add(createDocument(document, currentChunk.toString().trim(), level));
        }

        return result;
    }

    /**
     * 按字符精确切分（兜底策略）
     */
    private List<Document> splitByCharacterExact(Document document) {
        List<Document> result = new ArrayList<>();
        String content = document.getText();
        int contentLength = content.length();

        for (int i = 0; i < contentLength; i += TARGET_CHUNK_SIZE) {
            int end = Math.min(i + TARGET_CHUNK_SIZE, contentLength);
            String subContent = content.substring(i, end);

            // 尽量在空格或标点处切分，避免切在单词中间
            if (end < content.length() && !isNaturalBoundary(content.charAt(end-1))) { //不是最后一轮 且 最后一位不是空格or标点处
                int newEnd = findBetterBreak(content, end, Math.max(i, end-20)); //仅在末尾20字符区域内寻找断点
                if (newEnd > i) {
                    end = newEnd;
                    subContent = content.substring(i, end);
                }
            }
            result.add(createDocument(document, subContent, "character"));
        }

        return result;
    }

    /**
     * 最后一位字符不是空格or标点处
     * @param c
     * @return
     */
    private boolean isNaturalBoundary(char c) {
        return Character.isWhitespace(c) || ".,!?;:，。；：！？".indexOf(c) >= 0;
    }

    /**
     * 查找合适的断点位置（避免切在单词中间）
     */
    private int findBetterBreak(String content, int currentEnd, int searchStart) {
        String searchArea = content.substring(searchStart, currentEnd);

        // 优先查找空格
        int lastSpace = searchArea.lastIndexOf(' ');
        if (lastSpace > 0) {
            return searchStart + lastSpace + 1;
        }

        // 查找标点符号
        String punctuations = "。，；：！？、.,;:!?";
        for (int i = searchArea.length() - 1; i >= 0; i--) {
            if (punctuations.indexOf(searchArea.charAt(i)) >= 0) {
                return searchStart + i + 1;
            }
        }
        return currentEnd;
    }

    /**
     *  继承原Document的MetaData，并生成新Document对象
     */
    private Document createDocument(Document original, String content, String splitLevel) {
        // 复制原Document的元数据
        Map<String, Object> metadata = new HashMap<>(original.getMetadata());

        // 添加分割相关的元数据
        metadata.put("split_level", splitLevel);
        metadata.put("original_length", original.getText().length());
        metadata.put("chunk_length", content.length());

        // 创建新的Document对象
        Document newDocument = new Document(content, metadata);

        return newDocument;
    }

    /**
     * 估算token数
     */
    private int estimateTokenCount(String text) {
        if (text == null || text.isEmpty()) return 0;
        String[] words = text.split("\\s+");//一个或多个空白字符[空格  \t  \n  \r  \f]
        int englishTokens = words.length;
        /**
         * []：括号内的任一字符
         * ^：取反
         * \\u4e00和\\u9fa5： Unicode中的中文起点、中文终点
         * -：范围连接符
         */
        int chineseChars = text.replaceAll("[^\\u4e00-\\u9fa5]", "").length();
        return englishTokens + chineseChars;
    }
}

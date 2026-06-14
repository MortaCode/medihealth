package com.myy.medihealth.chat.service.memory;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myy.medihealth.chat.entity.ChatMessage;
import com.myy.medihealth.chat.entity.ChatSession;
import com.myy.medihealth.chat.mapper.ChatMessageMapper;
import com.myy.medihealth.chat.mapper.ChatSessionMapper;
import com.myy.medihealth.chat.service.ChatMessageService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 聊天记忆服务 - 三层缓存架构
 * L1: 本地 ConcurrentHashMap
 * L2: Redis (JSON序列化, 24h TTL)
 * L3: MySQL 持久化
 */
@Slf4j
@Service
@AllArgsConstructor
public class ChatMemoryService {

    public static final String REDIS_KEY_PREFIX = "chat:memory:";
    public static final int REDIS_TTL_HOURS = 24;
    public static final int LOCAL_CACHE_MAX_SIZE = 1000;

    private final ChatSessionMapper sessionMapper;
    private final ChatMessageMapper messageMapper;
    private final ChatMessageService messageService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * L1 本地缓存
     */
    private final ConcurrentHashMap<String, List<Message>> localCache = new ConcurrentHashMap<>();

    /**
     * 保存聊天记忆到三层缓存 + 持久化
     *
     * @param sessionId 会话ID
     * @param messages  消息列表
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveMemory(String sessionId, List<Message> messages) {
        if (sessionId == null || sessionId.isEmpty() || messages == null || messages.isEmpty()) {
            return;
        }

        // 1. 更新 L1 本地缓存
        updateLocalCache(sessionId, messages);

        // 2. 更新 L2 Redis 缓存
        updateRedisCache(sessionId, messages);

        // 3. 持久化到 L3 MySQL
        persistToDatabase(sessionId, messages);

        log.debug("Chat memory saved for session: {}, message count: {}", sessionId, messages.size());
    }

    /**
     * 获取聊天记忆 - L1 → L2 → L3 逐层查询，命中则回填上层
     *
     * @param sessionId 会话ID
     * @return 消息列表，若全部未命中则返回空列表
     */
    public List<Message> getMemory(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            return Collections.emptyList();
        }

        // 1. 尝试 L1 本地缓存
        List<Message> cached = localCache.get(sessionId);
        if (cached != null && !cached.isEmpty()) {
            log.debug("Chat memory hit L1 cache for session: {}", sessionId);
            return new ArrayList<>(cached);
        }

        // 2. 尝试 L2 Redis 缓存
        List<Message> redisMessages = getFromRedis(sessionId);
        if (redisMessages != null && !redisMessages.isEmpty()) {
            log.debug("Chat memory hit L2 Redis cache for session: {}", sessionId);
            // 回填 L1
            updateLocalCache(sessionId, redisMessages);
            return redisMessages;
        }

        // 3. 尝试 L3 MySQL
        List<Message> dbMessages = getFromDatabase(sessionId);
        if (!dbMessages.isEmpty()) {
            log.debug("Chat memory hit L3 MySQL for session: {}", sessionId);
            // 回填 L2 和 L1
            updateRedisCache(sessionId, dbMessages);
            updateLocalCache(sessionId, dbMessages);
            return dbMessages;
        }

        log.debug("Chat memory miss all layers for session: {}", sessionId);
        return Collections.emptyList();
    }

    /**
     * 更新 L1 本地缓存，超出最大容量时淘汰最旧条目
     *
     * @param sessionId 会话ID
     * @param messages  消息列表
     */
    public void updateLocalCache(String sessionId, List<Message> messages) {
        // 超出容量时淘汰最旧的条目
        if (localCache.size() >= LOCAL_CACHE_MAX_SIZE) {
            String oldestKey = localCache.keySet().stream().findFirst().orElse(null);
            if (oldestKey != null) {
                localCache.remove(oldestKey);
            }
        }
        localCache.put(sessionId, new ArrayList<>(messages));
    }

    /**
     * 更新 L2 Redis 缓存，JSON序列化后设置24h TTL
     *
     * @param sessionId 会话ID
     * @param messages  消息列表
     */
    public void updateRedisCache(String sessionId, List<Message> messages) {
        try {
            List<Map<String, String>> messageData = messagesToJsonList(messages);
            String json = objectMapper.writeValueAsString(messageData);
            String key = REDIS_KEY_PREFIX + sessionId;
            redisTemplate.opsForValue().set(key, json, Duration.ofHours(REDIS_TTL_HOURS));
        } catch (Exception e) {
            log.error("Failed to update Redis cache for session: {}", sessionId, e);
        }
    }

    /**
     * 从 Redis 获取并反序列化消息列表
     *
     * @param sessionId 会话ID
     * @return 消息列表，失败或不存在时返回null
     */
    public List<Message> getFromRedis(String sessionId) {
        try {
            String key = REDIS_KEY_PREFIX + sessionId;
            Object value = redisTemplate.opsForValue().get(key);
            if (value == null) {
                return null;
            }
            String json;
            if (value instanceof String s) {
                json = s;
            } else if (value instanceof byte[] b) {
                json = new String(b, StandardCharsets.UTF_8);
            } else {
                return null;
            }
            List<Map<String, String>> messageData = objectMapper.readValue(json, new TypeReference<List<Map<String, String>>>() {});
            return jsonListToMessages(messageData);
        } catch (Exception e) {
            log.error("Failed to get memory from Redis for session: {}", sessionId, e);
        }
        return null;
    }

    /**
     * 从 MySQL 查询会话消息并转换为 Spring AI Message 列表
     *
     * @param sessionId 会话ID
     * @return 消息列表
     */
    public List<Message> getFromDatabase(String sessionId) {
        try {
            List<ChatMessage> entities = messageService.getBySessionId(sessionId);
            if (entities == null || entities.isEmpty()) {
                return Collections.emptyList();
            }
            return entities.stream()
                    .map(this::convertToAiMessage)
                    .filter(java.util.Objects::nonNull)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Failed to get memory from database for session: {}", sessionId, e);
            return Collections.emptyList();
        }
    }

    /**
     * 持久化消息到 MySQL - 创建或更新 ChatSession，保存 ChatMessage
     *
     * @param sessionId 会话ID
     * @param messages  消息列表
     */
    @Transactional(rollbackFor = Exception.class)
    public void persistToDatabase(String sessionId, List<Message> messages) {
        LocalDateTime now = LocalDateTime.now();

        // 确保会话存在，不存在则创建
        ChatSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            session = new ChatSession();
            session.setId(sessionId);
            session.setTitle("会话");
            session.setModelName("deepseek");
            session.setMessageCount(0);
            session.setDelFlag(0);
            session.setCreateTime(now);
            session.setUpdateTime(now);
            sessionMapper.insert(session);
        }

        // 保存所有消息（幂等性：按 sessionId + content + role 去重）
        for (Message msg : messages) {
            // 检查是否已存在相同消息
            boolean exists = messageMapper.selectCount(
                new LambdaQueryWrapper<ChatMessage>()
                    .eq(ChatMessage::getSessionId, sessionId)
                    .eq(ChatMessage::getRole, msg.getMessageType().name().toLowerCase())
                    .eq(ChatMessage::getContent, msg.getText())
            ) > 0;
            if (!exists) {
                ChatMessage entity = convertToEntity(sessionId, msg);
                entity.setCreateTime(now);
                entity.setUpdateTime(now);
                messageMapper.insert(entity);
            }
        }

        // 更新会话消息计数和时间
        long totalCount = messageMapper.selectCount(
            new LambdaQueryWrapper<ChatMessage>().eq(ChatMessage::getSessionId, sessionId));
        session.setMessageCount((int) totalCount);
        session.setUpdateTime(now);
        sessionMapper.updateById(session);
    }

    /**
     * 将 Message 列表转换为 JSON 友好的格式
     */
    private List<Map<String, String>> messagesToJsonList(List<Message> messages) {
        return messages.stream().map(msg -> {
            Map<String, String> map = new HashMap<>();
            map.put("type", msg.getMessageType().name());
            map.put("text", msg.getText() != null ? msg.getText() : "");
            return map;
        }).collect(Collectors.toList());
    }

    /**
     * 将 JSON 数据还原为 Message 列表
     */
    private List<Message> jsonListToMessages(List<Map<String, String>> messageData) {
        if (messageData == null) {
            return Collections.emptyList();
        }
        return messageData.stream().map(map -> {
            String type = map.get("type");
            String text = map.get("text") != null ? map.get("text") : "";
            return switch (type) {
                case "USER" -> new UserMessage(text);
                case "ASSISTANT" -> new AssistantMessage(text);
                case "SYSTEM" -> new SystemMessage(text);
                default -> new UserMessage(text);
            };
        }).collect(Collectors.toList());
    }

    /**
     * 将 ChatMessage 实体转换为 Spring AI Message
     *
     * @param entity 数据库实体
     * @return Spring AI Message
     */
    public Message convertToAiMessage(ChatMessage entity) {
        if (entity == null || entity.getRole() == null) {
            return null;
        }
        String role = entity.getRole().toLowerCase();
        String content = entity.getContent() != null ? entity.getContent() : "";
        return switch (role) {
            case "user" -> new UserMessage(content);
            case "assistant" -> new AssistantMessage(content);
            case "system" -> new SystemMessage(content);
            default -> new UserMessage(content);
        };
    }

    /**
     * 将 Spring AI Message 转换为 ChatMessage 实体
     *
     * @param sessionId 会话ID
     * @param message   Spring AI Message
     * @return 数据库实体
     */
    public ChatMessage convertToEntity(String sessionId, Message message) {
        ChatMessage entity = new ChatMessage();
        entity.setId(IdUtil.fastSimpleUUID());
        entity.setSessionId(sessionId);
        entity.setContent(message.getText() != null ? message.getText() : "");
        entity.setMessageType("text");
        entity.setTokenCount(0);
        entity.setDelFlag(0);

        if (message instanceof UserMessage) {
            entity.setRole("user");
        } else if (message instanceof AssistantMessage) {
            entity.setRole("assistant");
        } else if (message instanceof SystemMessage) {
            entity.setRole("system");
        } else {
            entity.setRole("user");
        }

        return entity;
    }

    /**
     * 清除指定会话的 L1 + L2 缓存
     *
     * @param sessionId 会话ID
     */
    public void clearCache(String sessionId) {
        localCache.remove(sessionId);
        try {
            String key = REDIS_KEY_PREFIX + sessionId;
            redisTemplate.delete(key);
            log.debug("Cleared cache for session: {}", sessionId);
        } catch (Exception e) {
            log.error("Failed to clear Redis cache for session: {}", sessionId, e);
        }
    }

    /**
     * 清除记忆 - 清除缓存 + 软删除数据库记录
     *
     * @param sessionId 会话ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void clearMemory(String sessionId) {
        // 清除缓存
        clearCache(sessionId);

        // 软删除会话
        ChatSession session = sessionMapper.selectById(sessionId);
        if (session != null) {
            session.setDelFlag(1);
            session.setUpdateTime(LocalDateTime.now());
            sessionMapper.updateById(session);
        }

        // 软删除所有消息
        List<ChatMessage> messages = messageService.getBySessionId(sessionId);
        for (ChatMessage msg : messages) {
            msg.setDelFlag(1);
            msg.setUpdateTime(LocalDateTime.now());
            messageMapper.updateById(msg);
        }

        log.info("Cleared memory for session: {}", sessionId);
    }
}

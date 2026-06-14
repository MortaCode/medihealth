package com.myy.medihealth.chat.service;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.myy.medihealth.chat.entity.ChatMessage;
import com.myy.medihealth.chat.mapper.ChatMessageMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 聊天消息服务
 */
@Service
public class ChatMessageService extends ServiceImpl<ChatMessageMapper, ChatMessage> {

    /**
     * 根据会话ID查询消息列表，按创建时间升序
     *
     * @param sessionId 会话ID
     * @return 消息列表
     */
    public List<ChatMessage> getBySessionId(String sessionId) {
        LambdaQueryWrapper<ChatMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatMessage::getSessionId, sessionId);
        wrapper.orderByAsc(ChatMessage::getCreateTime);
        return list(wrapper);
    }

    /**
     * 保存一条聊天消息
     *
     * @param sessionId 会话ID
     * @param role      消息角色（user / assistant / system）
     * @param content   消息内容
     * @return 保存后的消息实体
     */
    @Transactional(rollbackFor = Exception.class)
    public ChatMessage saveMessage(String sessionId, String role, String content) {
        LocalDateTime now = LocalDateTime.now();
        ChatMessage message = new ChatMessage();
        message.setId(IdUtil.fastSimpleUUID());
        message.setSessionId(sessionId);
        message.setRole(role);
        message.setContent(content);
        message.setMessageType("text");
        message.setTokenCount(0);
        message.setDelFlag(0);
        message.setCreateTime(now);
        message.setUpdateTime(now);
        save(message);
        return message;
    }

    /**
     * 保存聊天消息（带token计数）
     *
     * @param sessionId 会话ID
     * @param role      消息角色
     * @param content   消息内容
     * @param tokenCount Token数量
     * @return 保存后的消息实体
     */
    @Transactional(rollbackFor = Exception.class)
    public ChatMessage saveMessage(String sessionId, String role, String content, int tokenCount) {
        ChatMessage message = saveMessage(sessionId, role, content);
        message.setTokenCount(tokenCount);
        updateById(message);
        return message;
    }
}

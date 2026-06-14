package com.myy.medihealth.chat.service;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.myy.medihealth.chat.entity.ChatSession;
import com.myy.medihealth.chat.mapper.ChatSessionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 聊天会话服务
 */
@Service
public class ChatSessionService extends ServiceImpl<ChatSessionMapper, ChatSession> {

    /**
     * 创建新的聊天会话
     *
     * @param userId    用户ID
     * @param modelName 模型名称
     * @return 新创建的会话
     */
    @Transactional(rollbackFor = Exception.class)
    public ChatSession create(String userId, String modelName) {
        LocalDateTime now = LocalDateTime.now();
        ChatSession session = new ChatSession();
        session.setId(IdUtil.fastSimpleUUID());
        session.setUserId(userId);
        session.setTitle("新会话");
        session.setModelName(modelName);
        session.setMessageCount(0);
        session.setDelFlag(0);
        session.setCreateTime(now);
        session.setUpdateTime(now);
        save(session);
        return session;
    }

    /**
     * 根据用户ID查询会话列表，按更新时间倒序
     *
     * @param userId 用户ID
     * @return 会话列表
     */
    public List<ChatSession> getByUserId(String userId) {
        LambdaQueryWrapper<ChatSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatSession::getUserId, userId);
        wrapper.orderByDesc(ChatSession::getUpdateTime);
        return list(wrapper);
    }

    /**
     * 更新会话的消息计数
     *
     * @param sessionId 会话ID
     * @param count     新的消息数量
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateMessageCount(String sessionId, int count) {
        ChatSession session = new ChatSession();
        session.setId(sessionId);
        session.setMessageCount(count);
        session.setUpdateTime(LocalDateTime.now());
        updateById(session);
    }
}

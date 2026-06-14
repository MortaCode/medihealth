package com.myy.medihealth.chat.service;

import cn.hutool.core.util.IdUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.myy.medihealth.chat.entity.ChatSnapshot;
import com.myy.medihealth.chat.mapper.ChatSnapshotMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 聊天快照服务 - 管理对话历史的压缩快照存储
 */
@Service
public class ChatSnapshotService extends ServiceImpl<ChatSnapshotMapper, ChatSnapshot> {

    /**
     * 保存对话快照
     *
     * @param sessionId 会话ID
     * @param data      序列化后的快照数据
     * @param count     快照时的消息数量
     * @return 保存后的快照实体
     */
    @Transactional(rollbackFor = Exception.class)
    public ChatSnapshot saveSnapshot(String sessionId, byte[] data, int count) {
        LocalDateTime now = LocalDateTime.now();
        String checksum = DigestUtil.md5Hex(data);

        ChatSnapshot snapshot = new ChatSnapshot();
        snapshot.setId(IdUtil.fastSimpleUUID());
        snapshot.setSessionId(sessionId);
        snapshot.setSnapshotData(data);
        snapshot.setMessageCount(count);
        snapshot.setChecksum(checksum);
        snapshot.setDelFlag(0);
        snapshot.setCreateTime(now);
        snapshot.setUpdateTime(now);
        save(snapshot);
        return snapshot;
    }

    /**
     * 获取会话的最新快照
     *
     * @param sessionId 会话ID
     * @return 最新的快照实体，若无则返回null
     */
    public ChatSnapshot getLatestSnapshot(String sessionId) {
        LambdaQueryWrapper<ChatSnapshot> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatSnapshot::getSessionId, sessionId);
        wrapper.orderByDesc(ChatSnapshot::getCreateTime);
        wrapper.last("LIMIT 1");
        return getOne(wrapper);
    }
}

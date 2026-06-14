package com.myy.medihealth.chat.controller;

import com.myy.medihealth.chat.service.memory.ChatMemoryService;
import com.myy.medihealth.common.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 对话记忆管理控制器
 * 管理AI对话的会话缓存和记忆
 */
@Slf4j
@RestController
@RequestMapping("chat")
@RequiredArgsConstructor
public class ChatMemoryController {

    private final ChatMemoryService chatMemoryService;

    /**
     * 清除指定会话的聊天缓存（L1本地 + L2 Redis）
     * 不清除MySQL持久化数据，仅清除运行时缓存
     *
     * @param sessionId 会话ID
     * @return 操作结果
     */
    @GetMapping("/memory/clear/cache")
    public Result<Map<String, Object>> clearCache(
            @RequestParam(required = false) String sessionId) {

        if (sessionId == null || sessionId.isBlank()) {
            log.warn("清除缓存请求缺少sessionId参数");
            return Result.error("sessionId参数不能为空");
        }

        log.info("清除会话缓存: sessionId={}", sessionId);

        chatMemoryService.clearCache(sessionId);

        Map<String, Object> result = Map.of(
                "sessionId", sessionId,
                "cleared", true,
                "message", "会话缓存已清除（L1本地缓存 + L2 Redis缓存）",
                "timestamp", System.currentTimeMillis()
        );

        return Result.success(result);
    }

    /**
     * 清除指定会话的全部记忆（缓存 + 数据库软删除）
     *
     * @param sessionId 会话ID
     * @return 操作结果
     */
    @DeleteMapping("/memory/clear")
    public Result<Map<String, Object>> clearMemory(
            @RequestParam(required = false) String sessionId) {

        if (sessionId == null || sessionId.isBlank()) {
            log.warn("清除记忆请求缺少sessionId参数");
            return Result.error("sessionId参数不能为空");
        }

        log.info("清除会话记忆: sessionId={}", sessionId);

        chatMemoryService.clearMemory(sessionId);

        Map<String, Object> result = Map.of(
                "sessionId", sessionId,
                "cleared", true,
                "message", "会话记忆已清除（缓存 + 数据库）",
                "timestamp", System.currentTimeMillis()
        );

        return Result.success(result);
    }

    /**
     * 获取会话记忆状态
     *
     * @param sessionId 会话ID
     * @return 会话状态信息（包含消息数量、最后活跃时间等）
     */
    @GetMapping("/memory/status")
    public Result<Map<String, Object>> sessionStatus(
            @RequestParam(required = false) String sessionId) {

        if (sessionId == null || sessionId.isBlank()) {
            return Result.error("sessionId参数不能为空");
        }

        log.debug("查询会话状态: sessionId={}", sessionId);

        // 从记忆服务获取消息，判断会话是否存在且活跃
        int messageCount = chatMemoryService.getMemory(sessionId).size();
        boolean active = messageCount > 0;

        Map<String, Object> status = Map.of(
                "sessionId", sessionId,
                "active", active,
                "messageCount", messageCount,
                "lastActivity", System.currentTimeMillis()
        );

        return Result.success(status);
    }
}

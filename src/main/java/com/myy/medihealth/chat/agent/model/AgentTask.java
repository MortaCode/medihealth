package com.myy.medihealth.chat.agent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Agent 任务 - ReAct模式下单个工具调用任务的定义
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentTask {

    /** 任务唯一标识 */
    private String id;

    /** 任务描述 */
    private String description;

    /** 对应MCP工具名称 */
    private String toolName;

    /** 工具调用目的说明 */
    private String toolPurpose;

    /** 执行顺序（从0开始） */
    private int order;

    /** 依赖的任务ID列表 */
    private List<String> dependencies;

    /** 工具调用参数 */
    private Map<String, String> params;

    /** 是否为必须任务 */
    private boolean required;

    /** 任务当前状态 */
    private TaskStatus status;

    /**
     * 任务状态枚举
     */
    public enum TaskStatus {
        PENDING("待执行"),
        RUNNING("执行中"),
        COMPLETED("已完成"),
        FAILED("执行失败"),
        SKIPPED("已跳过");

        private final String description;

        TaskStatus(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }
}

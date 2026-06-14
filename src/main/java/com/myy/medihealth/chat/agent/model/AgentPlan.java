package com.myy.medihealth.chat.agent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Agent 执行计划 - ReAct模式下的任务编排计划
 * 包含意图、任务列表、推理过程和提示词构建
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentPlan {

    /** 识别的医疗意图 */
    private MedicalIntent intent;

    /** 计划执行的任务列表 */
    private List<AgentTask> tasks;

    /** 计划编排的推理过程说明 */
    private String reasoning;

    /** 计划创建时间 */
    private LocalDateTime createdAt;

    /**
     * 构建 ReAct 风格的 LLM 提示词指令
     * 将任务列表转换为模型可理解和执行的指令格式
     */
    public String toPromptInstructions() {
        if (tasks == null || tasks.isEmpty()) {
            return "## 当前任务\n无需调用工具，请直接根据您的医学知识回答用户的问题。\n";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("## 任务执行计划\n\n");
        sb.append("你需要按以下顺序执行任务，获取必要的医疗信息后给出专业建议：\n\n");

        // 按 order 排序
        List<AgentTask> sortedTasks = tasks.stream()
                .sorted(java.util.Comparator.comparingInt(AgentTask::getOrder))
                .collect(Collectors.toList());

        for (int i = 0; i < sortedTasks.size(); i++) {
            AgentTask task = sortedTasks.get(i);
            sb.append("### 步骤 ").append(i + 1).append("：").append(task.getDescription()).append("\n");
            sb.append("- **工具名称**：`").append(task.getToolName()).append("`\n");
            sb.append("- **调用目的**：").append(task.getToolPurpose()).append("\n");

            if (task.getDependencies() != null && !task.getDependencies().isEmpty()) {
                sb.append("- **依赖步骤**：");
                sb.append(task.getDependencies().stream()
                        .map(depId -> {
                            // 找到依赖任务的顺序号
                            for (int j = 0; j < sortedTasks.size(); j++) {
                                if (sortedTasks.get(j).getId().equals(depId)) {
                                    return "步骤" + (j + 1);
                                }
                            }
                            return depId;
                        })
                        .collect(Collectors.joining("、")));
                sb.append("\n");
            }

            if (task.getParams() != null && !task.getParams().isEmpty()) {
                sb.append("- **参数**：");
                task.getParams().forEach((k, v) -> sb.append(k).append("=").append(v).append("; "));
                sb.append("\n");
            }

            if (!task.isRequired()) {
                sb.append("- ⚠️ 此步骤为可选，如不需要可跳过\n");
            }
            sb.append("\n");
        }

        sb.append("### 执行规则\n");
        sb.append("1. 严格按照步骤顺序执行，每次只调用一个工具\n");
        sb.append("2. 等待每个工具返回结果后，分析数据再决定下一步\n");
        sb.append("3. 依赖步骤必须先完成，才能执行当前步骤\n");
        sb.append("4. 所有步骤完成后，汇总信息给出最终建议\n");
        sb.append("5. 最终回答必须包含专业的医疗免责声明\n");

        return sb.toString();
    }
}

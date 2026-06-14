package com.myy.medihealth.chat.controller;

import com.myy.medihealth.chat.vo.ChatModel;
import com.myy.medihealth.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI模型管理控制器
 * 提供可用模型列表和模型信息查询
 */
@Slf4j
@RestController
@RequestMapping("model")
public class ModelController {

    /**
     * 获取可用的AI模型列表
     * 返回当前系统所有支持的大语言模型及其详细信息
     *
     * @return 模型列表
     */
    @GetMapping("/list")
    public Result<Map<String, Object>> listModels() {
        log.debug("查询可用模型列表");

        List<Map<String, Object>> models = new ArrayList<>();

        for (ChatModel model : ChatModel.values()) {
            Map<String, Object> modelInfo = new LinkedHashMap<>();
            modelInfo.put("code", model.getCode());
            modelInfo.put("name", model.getDisplayName());

            // 模型特性描述
            Map<String, Object> features = new LinkedHashMap<>();
            switch (model) {
                case DEEPSEEK -> {
                    features.put("provider", "DeepSeek");
                    features.put("description", "国产大语言模型，擅长逻辑推理和代码生成，性价比高");
                    features.put("contextLength", "128K");
                    features.put("supportStreaming", true);
                    features.put("supportFunctionCalling", true);
                    features.put("defaultTemperature", 0.7);
                }
                case QWEN -> {
                    features.put("provider", "阿里云通义千问");
                    features.put("description", "阿里自研大语言模型，中文理解能力强，适合医疗等专业场景");
                    features.put("contextLength", "32K");
                    features.put("supportStreaming", true);
                    features.put("supportFunctionCalling", true);
                    features.put("defaultTemperature", 0.5);
                }
            }
            modelInfo.put("features", features);
            models.add(modelInfo);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("count", models.size());
        result.put("default", ChatModel.DEEPSEEK.getCode());
        result.put("models", models);

        return Result.success(result);
    }
}

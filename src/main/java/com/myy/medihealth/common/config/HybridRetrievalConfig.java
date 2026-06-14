package com.myy.medihealth.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "retrieval.hybrid")
public class HybridRetrievalConfig {

    private int vectorTopK = 5;
    private int bm25TopK = 5;
    private double vectorWeight = 0.6;
    private double bm25Weight = 0.4;
    private double relevanceThreshold = 0.7;
    private int finalTopK = 5;
}

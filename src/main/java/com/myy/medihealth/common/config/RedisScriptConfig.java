package com.myy.medihealth.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;

@Configuration
public class RedisScriptConfig {

    @Bean("thumbScript")
    public DefaultRedisScript<Long> thumbScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/thumb.lua")));
        script.setResultType(Long.class);
        return script;
    }

    @Bean("unthumbScript")
    public DefaultRedisScript<Long> unthumbScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/unthumb.lua")));
        script.setResultType(Long.class);
        return script;
    }
}

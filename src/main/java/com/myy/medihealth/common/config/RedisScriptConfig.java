package com.myy.medihealth.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;

@Configuration
public class RedisScriptConfig {

    @Bean("thumbToggleScript")
    public DefaultRedisScript<Long> thumbToggleScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/thumb_toggle.lua")));
        script.setResultType(Long.class);
        return script;
    }


    @Bean("deductScript")
    public DefaultRedisScript<Long> deductScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/dudect.lua")));
        script.setResultType(Long.class);
        return script;
    }
}

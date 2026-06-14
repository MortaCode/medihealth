package com.myy.medihealth.login.sms;

import com.myy.medihealth.common.config.SmsConfig;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class SmsService {

    @Autowired(required = false)
    private SmsProvider smsProvider;
    private final SmsConfig smsConfig;
    private final StringRedisTemplate stringRedisTemplate;

    private static final String SMS_CODE_PREFIX = "sms:code:";

    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * 每分钟发送次数计数器 key: mobile, value: [timestamp, count]
     */
    private final ConcurrentHashMap<String, long[]> perMinuteCounters = new ConcurrentHashMap<>();

    /**
     * 每小时发送次数计数器 key: mobile, value: [hourStartEpoch, count]
     */
    private final ConcurrentHashMap<String, long[]> perHourCounters = new ConcurrentHashMap<>();

    /**
     * 每日发送次数计数器 key: mobile, value: [dayStartEpoch, count]
     */
    private final ConcurrentHashMap<String, long[]> perDayCounters = new ConcurrentHashMap<>();

    /**
     * 发送短信验证码
     *
     * @param mobile 手机号
     */
    public void sendCode(String mobile) {
        checkRateLimit(mobile);

        String code = generateCode();
        log.info("生成验证码: mobile={}, code={}", mobile, code);

        // 存入Redis，5分钟过期
        String redisKey = SMS_CODE_PREFIX + mobile;
        stringRedisTemplate.opsForValue().set(redisKey, code, Duration.ofMinutes(5));

        // 调用短信提供商发送
        if (smsProvider == null) {
            log.warn("未配置短信服务商(SmsProvider)，跳过短信发送: mobile={}, code={}", mobile, code);
        } else {
            try {
                smsProvider.send(mobile, code);
            } catch (Exception e) {
                log.error("短信发送失败: mobile={}", mobile, e);
                throw new RuntimeException("短信发送失败，请稍后重试");
            }
        }

        incrementCounters(mobile);
    }

    /**
     * 验证短信验证码
     *
     * @param mobile 手机号
     * @param code   验证码
     * @return 是否校验通过
     */
    public boolean verifyCode(String mobile, String code) {
        String redisKey = SMS_CODE_PREFIX + mobile;
        String storedCode = stringRedisTemplate.opsForValue().get(redisKey);
        if (storedCode == null) {
            return false;
        }
        boolean matched = storedCode.equals(code);
        if (matched) {
            // 验证通过后删除验证码，防止重复使用
            stringRedisTemplate.delete(redisKey);
        }
        return matched;
    }

    /**
     * 生成6位随机数字验证码
     */
    private String generateCode() {
        int code = RANDOM.nextInt(900000) + 100000;
        return String.valueOf(code);
    }

    /**
     * 频率控制检查
     */
    private void checkRateLimit(String mobile) {
        SmsConfig.RateLimit rateLimit = smsConfig.getRateLimit();
        long now = Instant.now().toEpochMilli();

        int maxPerMinute = rateLimit.getPerMinute() > 0 ? rateLimit.getPerMinute() : 1;
        int maxPerHour = rateLimit.getPerHour() > 0 ? rateLimit.getPerHour() : 5;
        int maxPerDay = rateLimit.getPerDay() > 0 ? rateLimit.getPerDay() : 10;

        // 检查每分钟限制
        long[] minuteCounter = perMinuteCounters.computeIfAbsent(mobile, k -> new long[]{now, 0});
        synchronized (minuteCounter) {
            if (now - minuteCounter[0] > 60_000) {
                minuteCounter[0] = now;
                minuteCounter[1] = 0;
            }
            if (minuteCounter[1] >= maxPerMinute) {
                throw new RuntimeException("短信发送过于频繁，请60秒后再试");
            }
        }

        // 检查每小时限制
        long[] hourCounter = perHourCounters.computeIfAbsent(mobile, k -> new long[]{now, 0});
        synchronized (hourCounter) {
            if (now - hourCounter[0] > 3_600_000) {
                hourCounter[0] = now;
                hourCounter[1] = 0;
            }
            if (hourCounter[1] >= maxPerHour) {
                throw new RuntimeException("短信发送次数已达每小时上限，请稍后再试");
            }
        }

        // 检查每日限制
        long[] dayCounter = perDayCounters.computeIfAbsent(mobile, k -> new long[]{now, 0});
        synchronized (dayCounter) {
            if (now - dayCounter[0] > 86_400_000) {
                dayCounter[0] = now;
                dayCounter[1] = 0;
            }
            if (dayCounter[1] >= maxPerDay) {
                throw new RuntimeException("短信发送次数已达每日上限，请明日再试");
            }
        }
    }

    /**
     * 更新所有计数器
     */
    private void incrementCounters(String mobile) {
        long now = Instant.now().toEpochMilli();

        long[] minuteCounter = perMinuteCounters.get(mobile);
        if (minuteCounter != null) {
            synchronized (minuteCounter) {
                minuteCounter[1]++;
            }
        }

        long[] hourCounter = perHourCounters.get(mobile);
        if (hourCounter != null) {
            synchronized (hourCounter) {
                hourCounter[1]++;
            }
        }

        long[] dayCounter = perDayCounters.get(mobile);
        if (dayCounter != null) {
            synchronized (dayCounter) {
                dayCounter[1]++;
            }
        }
    }
}

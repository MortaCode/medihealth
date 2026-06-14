package com.myy.medihealth.common.utils;

import com.myy.medihealth.common.constants.Constants;

public class RedisUtil {

    public static String thumbKey(String userId) {
        return Constants.THUMB_KEY_PREFIX + userId;
    }

    public static String tempThumbKey(String timeSlice) {
        return Constants.TEMP_THUMB_KEY_PREFIX + timeSlice;
    }

    public static String chatMemoryKey(String sessionId) {
        return Constants.CHAT_MEMORY_KEY_PREFIX + sessionId;
    }

    public static String quotaKey(String quotaId) {
        return Constants.STOCK_KEY_PREFIX + quotaId;
    }

    public static String quotaLockKey(String quotaId) {
        return Constants.LOCK_KEY_PREFIX + quotaId;
    }

    private RedisUtil() {
    }
}

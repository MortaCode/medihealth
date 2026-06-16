package com.myy.medihealth.thumb.vo;

import lombok.Getter;

@Getter
public enum LikeEnum {

    INCR(1),
    DECR(-1),
    NON(0);

    private final long value;

    LikeEnum(long value) {
        this.value = value;
    }
}

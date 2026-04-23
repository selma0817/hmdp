package com.hmdp.utils;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RedisData {
    // logic expiration time
    private LocalDateTime expireTime;
    private Object data;
}

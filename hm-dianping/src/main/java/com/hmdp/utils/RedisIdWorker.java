package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisIdWorker {
    private static final Long BEGIN_TIMESTAMP = 1640995200L;
    private static final Integer COUNT_BITS = 32;
    private StringRedisTemplate stringRedisTemplate;

    public RedisIdWorker(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public long nextId(String keyPrefix){
        // 1. create timestamp
        LocalDateTime now = LocalDateTime.now();
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp = nowSecond - BEGIN_TIMESTAMP;


        // 2. create serial number
        // 2.1 get current date
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        // 2.2. self increment
        Long count = stringRedisTemplate.opsForValue().increment("icr"+keyPrefix+":"+date);
        // 3. concatenate and return
        return timestamp<<COUNT_BITS | count;

    }
}

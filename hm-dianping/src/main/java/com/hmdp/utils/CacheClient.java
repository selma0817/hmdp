package com.hmdp.utils;

import cn.hutool.core.lang.func.Func;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Slf4j
@Component
public class CacheClient {
    private StringRedisTemplate stringRedisTemplate;
    public CacheClient(StringRedisTemplate stringRedisTemplate){
        this.stringRedisTemplate = stringRedisTemplate;
    }
    public void set(String key, Object value, Long time, TimeUnit unit){
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit){
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }
    public <ID, R> R queryWithPassThrough(
            String keyPrefix, ID id, Class<R> type, Function<ID, R>dbFallback, Long time, TimeUnit unit) {
        // 1. get shop cache from redis
        String key = keyPrefix+id;
        String json = stringRedisTemplate.opsForValue().get(key);
        // 2. decide if exist
        if (StrUtil.isNotBlank(json)){
            return JSONUtil.toBean(json, type);
        }
        // 3. if hit empty "", return
        if(json !=null){
            return null;
        }

        // 4. if not exist, search in db
        R r = dbFallback.apply(id);
        // 5. if not exist, set null value to redis
        if (r == null){
            stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        // 6. if exist, write in redis
        this.set(key, r, time, unit);

        // 7. return
        return r;
    }
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    public <R, ID> R queryWithLogicalExpire(
            String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        // 1. get shop cache from redis
        String key = keyPrefix+id;
        String json = stringRedisTemplate.opsForValue().get(key);
        // 2. decide if exist
        if (StrUtil.isBlank(json)){
            // 3. not exist, return
            return null;
        }

        // 4. exist, deserialize json to object
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        LocalDateTime expireTime = redisData.getExpireTime();
        // 5. decide if expire
        if(expireTime.isAfter(LocalDateTime.now())){
            // 5.1 if not expire, return
            return r;
        }
        // 5.2. expired, rebuild cache
        // 6. rebuild cache
        // 6.1 get mutex lock
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);
        // 6.2 decide if getting lock is successful
        if(isLock){
            // TODO 6.3 success, start new thread to rebuild cache
            CACHE_REBUILD_EXECUTOR.submit(()->{
                try {
                    // rebuild
                    R r1 = dbFallback.apply(id);
                    this.setWithLogicalExpire(key, r1, time, unit);
                    // release lock
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    unlock(lockKey);
                }

            });

        }
        // if success, open a thread to rebuild cache
        return r;
    }
    private boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", RedisConstants.LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }
    private void unlock(String key){
        stringRedisTemplate.delete(key);
    }



}

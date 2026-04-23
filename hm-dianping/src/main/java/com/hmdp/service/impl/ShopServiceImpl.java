package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private CacheClient cacheClient;

    @Override
    public Result queryById(Long id) {
        // cache penetration
        //Shop shop = queryWithPassThrough(id);
        // use mutex to solve cache breakdown
        //Shop shop = queryWithMutex(id);
        // use logical expire to solve cache breakdown
        //Shop shop = queryWithLogicalExpire(id);
//        Shop shop = cacheClient
//                .queryWithPassThrough(RedisConstants.CACHE_SHOP_KEY, id, Shop.class, this::getById,  RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
//
//
        Shop shop = cacheClient
                .queryWithLogicalExpire(RedisConstants.CACHE_SHOP_KEY, id, Shop.class, this::getById, RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        if (shop == null){
            return Result.fail("shop does not exist");
        }
        return Result.ok(shop);
    }


//    public Shop queryWithMutex(Long id) {
//        // 1. get shop cache from redis
//        String key = RedisConstants.CACHE_SHOP_KEY+id;
//        String shopJson = stringRedisTemplate.opsForValue().get(key);
//        // 2. decide if exist
//        if (StrUtil.isNotBlank(shopJson)){
//            return JSONUtil.toBean(shopJson, Shop.class);
//        }
//        // 3. if hit empty "", return
//        if(shopJson !=null){
//            return null;
//        }
//
//        // 4. if not exist, rebuild cache
//        // 4.1 get mutex
//        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
//        Shop shop = null;
//        try {
//            boolean isLock = tryLock(lockKey);
//            // 4.2 decide if get lock success
//            if (!isLock){
//                // 4.3 if fail, sleep and retry
//                Thread.sleep(50);
//                return queryWithMutex(id);
//            }
//
//            // 4.4 if success, search by id
//            shop = getById(id);
//            Thread.sleep(200); // simulate delay in reconstructing data in cache
//            // 5. if not exist, set null value to redis
//            if (shop == null){
//                stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
//                return null;
//            }
//            // 6. if exist, write in redis
//            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
//            // 7. return
//            //release mutex, and return
//
//        } catch (InterruptedException e) {
//            throw new RuntimeException(e);
//        } finally {
//            unlock(lockKey);
//        }
//        return shop;
//    }




    @Override
    @Transactional
    public Result update(Shop shop) {
        // TODO 1. update database, first make sure id is valid, then use updateById to write to database
        Long id = shop.getId();
        if (id==null){
            return Result.fail("shop id cannot be null");
        }
        updateById(shop);
        // TODO 2. delete from redis cache
        stringRedisTemplate.delete(RedisConstants.CACHE_SHOP_KEY+id);
        return Result.ok();
    }
}

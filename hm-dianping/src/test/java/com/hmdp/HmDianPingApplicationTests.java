package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisIdWorker;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoLocation;
import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@SpringBootTest
class HmDianPingApplicationTests {

    @Resource
    private ShopServiceImpl shopService;
    @Resource
    private CacheClient cacheClient;
    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private StringRedisTemplate stringRedisTemplate;



    @Test
    void testIdWorker() throws InterruptedException {
        ExecutorService executorService = Executors.newFixedThreadPool(500);
        CountDownLatch countDownLatch = new CountDownLatch(300);
        Runnable task = ()->{
            for (int i=0; i<100; i++){
                long id = redisIdWorker.nextId("order");
                System.out.println("id=" + id);
            }
            countDownLatch.countDown();
        };
        long begin = System.currentTimeMillis();
        for(int i=0; i<300; i++){
            executorService.submit(task);
        }
        countDownLatch.await();
        long end = System.currentTimeMillis();
        System.out.println("t=" + (end-begin));
    }
    @Test
    void testSaveShop() throws InterruptedException {
        Shop shop = shopService.getById(1L);
        cacheClient.setWithLogicalExpire(RedisConstants.CACHE_SHOP_KEY+1L, shop, 10L, TimeUnit.SECONDS);
    }

    @Test
    void testLoadShopData(){
        // 1. search for shop info
        List<Shop> list = shopService.list();
        // 2. sort shop by group by their typeId, same shop id in same sorted set
        Map<Long, List<Shop>> map = list.stream().collect(Collectors.groupingBy(Shop::getTypeId));
        for(Map.Entry<Long, List<Shop>> entry: map.entrySet()){
            // 3.1 get type id
            Long typeId = entry.getKey();
            String key = RedisConstants.SHOP_GEO_KEY + typeId;
            // 3.2 get list of shop of same type id
            List<Shop> value = entry.getValue();
            List<RedisGeoCommands.GeoLocation<String>> locations=new ArrayList<>(value.size());
            for (Shop shop: value){
                //stringRedisTemplate.opsForGeo().add(key, new Point(shop.getX(), shop.getY()), shop.getId().toString());
                locations.add(new RedisGeoCommands.GeoLocation<>(
                        shop.getId().toString(),
                        new Point(shop.getX(), shop.getY()))
                );
            }
            stringRedisTemplate.opsForGeo().add(key, locations);
        }
        // 3. save the shops in sorted set
    }
    @Test
    void testHyperLogLog(){
        String[] values = new String[1000];
        int j = 0;
        for (int i=0; i<1000000; i++){
            j = i % 1000;
            values[j] = "user_" + i;
            if(j==999){
                // send to redis
                stringRedisTemplate.opsForHyperLogLog().add("hl2", values);
            }
        }
        // statistic
        Long count= stringRedisTemplate.opsForHyperLogLog().size("hl2");
        System.out.println("count="+count);

    }

}

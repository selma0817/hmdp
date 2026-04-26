package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IVoucherService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.Collections;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private RedissonClient redissonClient;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024 * 1024);
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    @PostConstruct
    private void init() {
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }

    private class VoucherOrderHandler implements Runnable {
        @Override
        public void run() {
            while (true) {
                // 1. get voucher order in queue
                try {
                    VoucherOrder voucherOrder = orderTasks.take();
                    handleVoucherOrder(voucherOrder);
                } catch (InterruptedException e) {
                    log.error("dealing with order error", e);
                }
            }

        }
    }

    private void handleVoucherOrder(VoucherOrder voucherOrder) {
        // 1. get user
        Long userId = voucherOrder.getUserId();
        // 2. create lock object
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        // 3. get lock
        boolean isLock = lock.tryLock();
        // 4. if fail at getting lock
        if (!isLock) {
            log.error("no repeat ordering");
            return;
        }
        try {
            proxy.createVoucherOrder(voucherOrder);
        } finally {
            lock.unlock();
        }
    }

    private IVoucherOrderService proxy;

    @Override
    public Result seckillVoucher(Long voucherId) {
        // get user
        Long userId = UserHolder.getUser().getId();
        // 1. execute the lua script
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(),
                userId.toString());
        int r = result.intValue();
        // 2. decide the lua return if it is 0
        if (r != 0) {
            return Result.fail(r == 1 ? "insufficient stock" : "cannot buy again");
        }

        // 2.1. if result is not 0, cannot buy
        // 2.2 if result is 0, can buy, save to blocking queue
        VoucherOrder voucherOrder = new VoucherOrder();
        // 2.3 order id
        Long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        // 2.4 voucher id
        voucherOrder.setUserId(userId);
        // 2.5 return order id
        voucherOrder.setVoucherId(voucherId);
        // 2.6 create blocking queue
        orderTasks.add(voucherOrder);
        // 3. get proxy object
        proxy = (IVoucherOrderService) AopContext.currentProxy();
        return Result.ok(orderId);
    }

    //    @Override
//    public Result seckillVoucher(Long voucherId) {
//        // 1. search for voucher
//        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
//        // 2. decide seckill start
//        if (voucher.getBeginTime().isAfter(LocalDateTime.now())){
//            // not yet start
//            return Result.fail("seckill not started yet");
//        }
//        // 3. decide seckill end
//        if (voucher.getEndTime().isBefore(LocalDateTime.now())){
//            return Result.fail("seckill has ended");
//        }
//        // 4. decide if enough voucher
//        if (voucher.getStock() < 1){
//            return Result.fail("not enough voucher left in stock");
//        }
//        return createVoucherOrder(voucherId);
//
//    }
    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder) {

        // 5. one order per person
        Long userId = voucherOrder.getUserId();

        // 5.1. search for order
        long count = query().eq("user_id", userId).eq("voucher_id", voucherOrder.getVoucherId()).count();
        if (count > 0) {
            // already bought voucher
            log.error("user has already purchased");
            return;
        }
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock-1") // set stock = stock - 1
                .eq("voucher_id", voucherOrder.getVoucherId()).gt("stock", 0)
                .update();

        if (!success) {
            log.error("insufficient stock");
            return;
        }
        save(voucherOrder);
    }
}
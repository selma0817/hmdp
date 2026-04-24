package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */

@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private RedisIdWorker redisIdWorker;
    @Override
    public Result seckillVoucher(Long voucherId) {
        // 1. search for voucher
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        // 2. decide seckill start
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())){
            // not yet start
            return Result.fail("seckill not started yet");
        }
        // 3. decide seckill end
        if (voucher.getEndTime().isBefore(LocalDateTime.now())){
            return Result.fail("seckill has ended");
        }
        // 4. decide if enough voucher
        if (voucher.getStock() < 1){
            return Result.fail("not enough voucher left in stock");
        }

        Long userId = UserHolder.getUser().getId();
        synchronized (userId.toString().intern()) {
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        }
    }
    @Transactional
    public Result createVoucherOrder(Long voucherId) {

        // 5. one order per person
            Long userId = UserHolder.getUser().getId();
            // 5.1. search for order
            int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
            if (count > 0) {
                // already bought voucher
                return Result.fail("user has already purchased the secvoucher before");
            }

            // 6. decrement voucher
            boolean success = seckillVoucherService.update()
                    .setSql("stock = stock-1") // set stock = stock - 1
                    .eq("voucher_id", voucherId).gt("stock", 0)
                    .update();

            if (!success) {
                return Result.fail("not enough in stock");
            }


            // 7. create order
            VoucherOrder voucherOrder = new VoucherOrder();
            // 7.1 order id
            Long orderId = redisIdWorker.nextId("order");
            voucherOrder.setId(orderId);
            // 7.2 voucher id
            voucherOrder.setUserId(userId);
            // 7.3 return order id
            voucherOrder.setVoucherId(voucherId);
            save(voucherOrder);
            return Result.ok(voucherOrder);
        }

}

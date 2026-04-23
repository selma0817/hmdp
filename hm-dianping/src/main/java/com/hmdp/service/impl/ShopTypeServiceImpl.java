package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result getTypeList() {
        // get redis key
        String typeKey = RedisConstants.CACHE_SHOP_TYPE_KEY;
        Long typeListSize = stringRedisTemplate.opsForList().size(typeKey);
        // if list in redis cache, return the list
        if(typeListSize!=null && typeListSize!=0){
            List<String> typeJsonList = stringRedisTemplate.opsForList().range(typeKey, 0, -1);
            // stored as list of string, but need to convert to object
            List<ShopType> typeList = new ArrayList<>();
             for(String typeJson : typeJsonList){
                typeList.add(JSONUtil.toBean(typeJson, ShopType.class));
            }
            return Result.ok(typeList);
        }
        // if list not in redis cache, search in database
        List<ShopType> typeList = query().orderByAsc("sort").list();

        // if list not in database, return error
        if (typeList == null){
            return Result.fail("Shop Type List not in Database");
        }
        List<String> typeJsonList = new ArrayList<>();
        for (ShopType shopType: typeList){
            typeJsonList.add(JSONUtil.toJsonStr(shopType));
        }

        // if list in database, put into red is
        stringRedisTemplate.opsForList().rightPushAll(typeKey, typeJsonList);
        // return
        return Result.ok(typeList);
    }
}

package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result sendCode(String phone, HttpSession session) {
        // 1. authenticate phone number
        if (RegexUtils.isPhoneInvalid(phone)){
            // if not match, return wrong phone number
            return Result.fail("Wrong Phone Number");
        }
        // 2. if match, create code
        String code = RandomUtil.randomNumbers(6);


        // 3. store the code in redis
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);

        // 4. send code to user
        log.debug("successfully send code:{}", code);
        // send ok
        return Result.ok(code);
    }
    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session){
        // 1. authenticate phone number
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)){
            return Result.fail("Wrong Phone Number");
        }
        // 2. authenticate code
        //Object cacheCode = session.getAttribute("code");
        // get code from redis
        Object cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY+phone);
        String code = loginForm.getCode();
        if (cacheCode == null || !cacheCode.toString().equals(code)){
            // 3. if not the same, error
            return Result.fail("Wrong authentication code");
        }
        // 4. find user with phone
        User user = query().eq("phone", phone).one();
        // 5. decide whether user exist
        if (user == null) {

            // 6. if user not exist, create new user and save
            user = createUserWithPhone(phone);
        }
        // TODO save user info to redis
        // generate token,as login
        String token = UUID.randomUUID().toString(true);
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        // convert userDTO to hashMap
        ///Map<String, Object> userMap = BeanUtil.beanToMap(userDTO);
        Map<String, Object> userMap = BeanUtil.beanToMap(
                userDTO,
                new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue == null ? null : fieldValue.toString())
        );
        // save user as hash in redis
        String tokenKey = LOGIN_USER_KEY +token;
        // store token
        stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
        // set expiration for the token
        stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES);
        // use interceptor to check if user is still active, so we can
        // use it to refresh expiration for token

        // return token
        // 7. write user info to session

        //session.setAttribute("user", userDTO);
        //session.setAttribute("user", user);

        return Result.ok(token);
    }

    private User createUserWithPhone(String phone) {
        // 1. create user
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        // 2. save the user
        save(user);

        return user;
    }
}

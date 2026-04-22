package com.hmdp.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;

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

    @Override
    public Result sendCode(String phone, HttpSession session) {
        // authenticate phone number
        if (RegexUtils.isPhoneInvalid(phone)){
            return Result.fail("Wrong Phone Number");
        }
        // if not match, return wrong phone number
        String code = RandomUtil.randomNumbers(6);
        // if match, create code

        // store the code in session
        session.setAttribute("code", code);
        // send code to user
        log.debug("successfully send code:{}", code);
        // send ok
        return Result.ok();
    }
}

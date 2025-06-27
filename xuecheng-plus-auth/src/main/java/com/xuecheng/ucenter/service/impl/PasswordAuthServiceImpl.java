package com.xuecheng.ucenter.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xuecheng.ucenter.mapper.XcUserMapper;
import com.xuecheng.ucenter.model.dto.AuthParamsDto;
import com.xuecheng.ucenter.model.dto.XcUserExt;
import com.xuecheng.ucenter.model.po.XcUser;
import com.xuecheng.ucenter.service.AuthService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service("password_authservice")
public class PasswordAuthServiceImpl implements AuthService {

    @Autowired
    private XcUserMapper xcUserMapper;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public XcUserExt execute(AuthParamsDto authParamsDto) {

        // 账号
        String username = authParamsDto.getUsername();

        // todo: 校验验证码

        // 账号是否存在
        // 根据username账号查询数据库
        XcUser xcUser = xcUserMapper.selectOne(new LambdaQueryWrapper<XcUser>().eq(XcUser::getUsername, username));


        // 查询到用户不存在，要返回null即可，spring security会抛出异常用户不存在
        if (xcUser == null) {
            throw new RuntimeException("用户不存在");
        }

        // 验证密码是否正确
        // 如果查到了用户，用户拿到正确的密码
        String password = xcUser.getPassword();
        // 拿到用户输入的密码
        String inputPassword = authParamsDto.getPassword();
        // 校验密码
        boolean matches = passwordEncoder.matches(inputPassword, password);
        if (!matches) {
            throw new RuntimeException("密码错误");
        }
        XcUserExt xcUserExt = new XcUserExt();
        BeanUtils.copyProperties(xcUser, xcUserExt);

        return xcUserExt;
    }
}

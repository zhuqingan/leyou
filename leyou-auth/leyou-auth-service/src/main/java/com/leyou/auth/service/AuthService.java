package com.leyou.auth.service;

import com.leyou.auth.config.JwtProperties;
import com.leyou.auth.pojo.UserInfo;
import com.leyou.auth.utils.JwtUtils;
import com.leyou.client.UserClient;
import com.leyou.user.pojo.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    @Autowired
    private UserClient userClient;

    @Autowired
    private JwtProperties properties;


    public String accredit(User user) {
        //调用远程接口，校验用户名和密码
        User u = this.userClient.queryUser(user.getUsername(), user.getPassword());
        //判断用户是否存在
        if (u == null){
            return null;
        }
        //生成jwt
        try {
            UserInfo userInfo = new UserInfo();
            userInfo.setId(u.getId());
            userInfo.setUsername(u.getUsername());
            return JwtUtils.generateToken(userInfo, this.properties.getPrivateKey(), this.properties.getExpire());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}

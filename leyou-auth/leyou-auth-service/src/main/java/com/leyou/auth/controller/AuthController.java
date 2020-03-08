package com.leyou.auth.controller;

import com.leyou.auth.config.JwtProperties;
import com.leyou.auth.pojo.UserInfo;
import com.leyou.auth.service.AuthService;
import com.leyou.auth.utils.JwtUtils;
import com.leyou.common.enums.ExceptionEnum;
import com.leyou.common.exception.LyException;
import com.leyou.common.utils.CookieUtils;
import com.leyou.user.pojo.User;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Controller
@EnableConfigurationProperties(JwtProperties.class)
public class AuthController {

    @Autowired
    private AuthService authService;

    @Autowired
    private JwtProperties properties;

    @PostMapping("accredit")
    public ResponseEntity<Void> accredit(User user, HttpServletRequest request, HttpServletResponse response){
        //调用service方法，校验用户信息，获取jwt
        String token = this.authService.accredit(user);
        if (StringUtils.isBlank(token)){
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        //把token放入cookie中
        CookieUtils.setCookie(request,response,this.properties.getCookieName(),token,this.properties.getExpire());

        return ResponseEntity.noContent().build();
    }

    /**
     * 校验用户登录状态
     */
    @GetMapping("verify")
    public ResponseEntity<UserInfo> verify(@CookieValue("LY_TOKEN")String token,HttpServletRequest request,HttpServletResponse response){
        try {
            //解析token
            UserInfo info = JwtUtils.getInfoFromToken(token,properties.getPublicKey());
            //刷新token，重新生成token
            String newToken = JwtUtils.generateToken(info,properties.getPrivateKey(),properties.getExpire());
            //写入cookie
            CookieUtils.setCookie(request,response,this.properties.getCookieName(),newToken,this.properties.getExpire());
            //已登录，返回用户信息
            return ResponseEntity.ok(info);
        }catch (Exception e){
            throw new LyException(ExceptionEnum.UNAUTHORIZED);
        }
    }
}

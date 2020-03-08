package com.leyou.user.service;

import com.leyou.common.enums.ExceptionEnum;
import com.leyou.common.exception.LyException;
import com.leyou.common.utils.NumberUtils;
import com.leyou.user.mapper.UserMapper;
import com.leyou.user.pojo.User;
import com.leyou.user.utils.CodecUtils;
import com.netflix.discovery.converters.Auto;
import org.apache.commons.lang3.StringUtils;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class UserService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private AmqpTemplate amqpTemplate;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private static final String KEY_PREFIX = "user:verify:phone:";

    public Boolean checkData(String data, Integer type) {
        User record = new User();
        switch (type) {
            case 1:
                record.setUsername(data);
                break;
            case 2:
                record.setPhone(data);
                break;
            default:
                throw new LyException(ExceptionEnum.INVALID_USER_DATA_TYPE);
        }
        return userMapper.selectCount(record) == 0;
    }

    public void sendCode(String phone) {
        String key = KEY_PREFIX + phone;
        String code = NumberUtils.generateCode(6);
        Map<String,String> msg = new HashMap<>();
        msg.put("phone",phone);
        msg.put("code",code);

        //发送验证码
        amqpTemplate.convertAndSend("ly.sms.exchange","sms.verify.code",msg);
        //保存验证码
        stringRedisTemplate.opsForValue().set(key,code,5, TimeUnit.MINUTES);
    }

    public Boolean register(User user, String code) {
        //查询redis中的验证码
        String cacheCode = this.stringRedisTemplate.opsForValue().get(KEY_PREFIX + user.getPhone());
        //校验验证码
        if (!StringUtils.equals(code,cacheCode)){
            return false;
        }
        //生成盐
        String salt = CodecUtils.generateSalt();
        user.setSalt(salt);
        //对密码加密
        String password = CodecUtils.md5Hex(user.getPassword(), salt);
        user.setPassword(password);

        //新增用户
        user.setId(null);
        user.setCreated(new Date());
        Boolean b = this.userMapper.insertSelective(user) == 1;

        //删除redis中的验证码
        if (b) {
            this.stringRedisTemplate.delete(KEY_PREFIX + user.getPhone());
        }
        return true;
    }

    public User queryUser(String username, String password) {
        //执行查询
        User record = new User();
        record.setUsername(username);
        User user = this.userMapper.selectOne(record);

        //判断用户是否存在
        if (user == null) {
            return  null;
        }
        //比较密码
        password = CodecUtils.md5Hex(password, user.getSalt());
        if (StringUtils.equals(password,user.getPassword())) {
            return null;
        }
        return user;
    }
}
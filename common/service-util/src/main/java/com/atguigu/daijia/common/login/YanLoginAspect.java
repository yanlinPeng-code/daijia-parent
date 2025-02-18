package com.atguigu.daijia.common.login;

import com.atguigu.daijia.common.constant.RedisConstant;
import com.atguigu.daijia.common.execption.GuiguException;
import com.atguigu.daijia.common.result.ResultCodeEnum;
import com.atguigu.daijia.common.util.AuthContextHolder;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component//交给Spring容器管理
@Aspect//切面类
public class YanLoginAspect {
    @Autowired
    private RedisTemplate redisTemplate;
    //用环绕通知，进行登录判断
    @Around("execution(* com.atguigu.daijia.*.controller.*.*(..)) && @annotation(yanLogin)")//
    public Object yanLogin(ProceedingJoinPoint proceedingJoinPoint, YanLogin yanLogin) throws Throwable {
        // 使用 @Around 注解定义一个环绕通知，匹配所有带有 @YanLogin 注解的方法，并且这些方法必须位于 com.atguigu.daijia.*.controller 包下的任意类中。
        // 方法参数 ProceedingJoinPoint proceedingJoinPoint 表示当前被拦截的方法的连接点。
        // 方法参数 YanLogin yanLogin 表示当前方法上的 @YanLogin 注解实例。
        //在该方法中，直接调用 proceedingJoinPoint.proceed() 来继续执行目标方法。
        //1.获取到request对象，从请求头里获取到token
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        ServletRequestAttributes servletRequestAttributes = (ServletRequestAttributes) requestAttributes;
        HttpServletRequest request = servletRequestAttributes.getRequest();

        String token = request.getHeader("token");

        //2.判断token是否为空,是空，返回提示信息。
        if(!StringUtils.hasText(token)){
            throw new GuiguException(ResultCodeEnum.LOGIN_AUTH);
        }
        //3.如果不为空，从redis里查看是否有customerid
        String customerId = (String) redisTemplate.opsForValue().get(RedisConstant.USER_LOGIN_KEY_PREFIX+token);
        if(!StringUtils.hasText(customerId)){
            throw new GuiguException(ResultCodeEnum.LOGIN_AUTH);
        }else {
            AuthContextHolder.setUserId(Long.parseLong(customerId));
        }
        //4.查询完后，如果有，把用户id放到ThreadLocal里，并放行。
        //如果没有，返回提示信息。
        //5.执行业务方法
      return  proceedingJoinPoint.proceed();

    }
}

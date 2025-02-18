package com.atguigu.daijia.common.service;


import com.alibaba.fastjson2.JSON;
import com.atguigu.daijia.common.entity.GuiguCorrelationData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class RabbitService {


    @Autowired
    private RabbitTemplate rabbitTemplate;

    //实现思路：借助redis来实现重发机制
    @Autowired
    RedisTemplate redisTemplate;



    /**
     *  发送消息
     * @param exchange 交换机
     * @param routingkey 路由键
     * @param message 消息
     */
    //发送消息
    public  boolean sendMessage(String exchange,
                                String routingkey,
                                Object message) {


        //1.创建自定义相关消息对象-包含业务数据本身，交换器名称，路由键，队列类型，延迟时间,重试次数
        GuiguCorrelationData guiguCorrelationData=new GuiguCorrelationData();
        String uuid="mq:"+ UUID.randomUUID().toString().replaceAll("-","");
        guiguCorrelationData.setId(uuid);
        guiguCorrelationData.setMessage(message);
        guiguCorrelationData.setExchange(exchange);
        guiguCorrelationData.setRoutingKey(routingkey);
        //2.将相关消息封装到发送消息方法中
        rabbitTemplate.convertAndSend(exchange,routingkey,message,guiguCorrelationData);
        //3.将相关消息存入Redis  Key：UUID  相关消息对象  10 分钟
        redisTemplate.opsForValue().set(uuid, JSON.toJSONString(guiguCorrelationData),10, TimeUnit.MINUTES);

        log.info("生产者发送消息成功：{}，{}，{}", exchange, routingkey, message);
        return true;
    }


    // * 发送延迟消息方法
    // * @param exchange 交换机
    // * @param routingKey 路由键
    // * @param message 消息数据
    // * @param delayTime 延迟时间，单位为：秒


    public Boolean sendDelayMessage(String exchangeDelay, String routingDelay, Object message, int delayTime) {
        //1.创建自定义相关消息对象-包含业务数据本身，交换器名称，路由键，队列类型，延迟时间,重试次数
        GuiguCorrelationData guiguCorrelationData=new GuiguCorrelationData();
        guiguCorrelationData.setId("mq:"+UUID.randomUUID().toString().replaceAll("-",""));
        guiguCorrelationData.setMessage(message);
        guiguCorrelationData.setExchange(exchangeDelay);
        guiguCorrelationData.setRoutingKey(routingDelay);
        guiguCorrelationData.setDelayTime(delayTime);
        guiguCorrelationData.setDelay(true);
        //2.将相关消息封装到发送消息方法中
        rabbitTemplate.convertAndSend(exchangeDelay,routingDelay,message,message1 -> {
            message1.getMessageProperties().setDelay(delayTime*1000);
            return message1;
        },guiguCorrelationData);
//3.将相关消息存入Redis  Key：UUID  相关消息对象  10 分钟
        redisTemplate.opsForValue().set("mq:"+UUID.randomUUID().toString().replaceAll("-",""), JSON.toJSONString(guiguCorrelationData), 10, TimeUnit.MINUTES);
        return true;
    }

}

package com.atguigu.daijia.mq.controller;

import com.atguigu.daijia.common.result.Result;
import com.atguigu.daijia.common.service.RabbitService;
import com.atguigu.daijia.mq.config.DeadLetterMqConfig;
import com.atguigu.daijia.mq.config.DelayedMqConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/mq")
public class MqController {


    @Autowired
    RabbitService rabbitService;

    /**
     * 消息发送
     */
    //http://localhost:8282/mq/sendConfirm
    @GetMapping("sendConfirm")
    public Result sendConfirm() {
        rabbitService.sendMessage("exchange.conf", "routing.conf", "来人了，开始接客吧！");
        return Result.ok();
    }

    /**
     * 消息发送延迟消息：基于死信实现
     */
    @GetMapping("/sendDeadLetterMsg")
    public Result sendDeadLetterMsg() {
        rabbitService.sendMessage(DeadLetterMqConfig.exchange_dead, DeadLetterMqConfig.routing_dead_1, "我是延迟消息");
        log.info("基于死信发送延迟消息成功");
        return Result.ok();
    }

    /**
     * 消息发送延迟消息：基于延迟插件使用,使用插件后交换机会暂存消息固交换器无法即时路由消息到队列
     */
//@GetMapping("/sendDelayMsg")
//public Result sendDelayMsg() {
//    rabbitTemplate.convertAndSend(DelayedMqConfig.exchange_delay,
//            DelayedMqConfig.routing_delay,
//            "基于延迟插件-我是延迟消息",
//            (message -> {
//                //设置消息ttl
//                message.getMessageProperties().setDelay(10000);
//                return message;
//            })
//    );
//    log.info("基于延迟插件-发送延迟消息成功");
//    return Result.ok();
//}

    /**
     * 消息发送延迟消息：基于延迟插件使用
     */
    @GetMapping("/sendDelayMsg")
    public Result sendDelayMsg() {
        //调用工具方法发送延迟消息
        int delayTime = 10;
        rabbitService.sendDelayMessage(DelayedMqConfig.exchange_delay, DelayedMqConfig.routing_delay, "我是延迟消息", delayTime);
        log.info("基于延迟插件-发送延迟消息成功");
        return Result.ok();
    }
}
package com.atguigu.daijia.order.monitor;

import com.atguigu.daijia.order.service.OrderInfoService;
import jakarta.annotation.PostConstruct;
import org.redisson.api.RBlockingDeque;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
//监听延迟队列
public class RedisDelayHandle {
    @Autowired
    RedissonClient redissonClient;

    @Autowired
    OrderInfoService orderInfoService;


    @PostConstruct
    public void listener(){

        new Thread(()->{
            while (true){
                ////获取延迟队列里面阻塞队列
                RBlockingDeque<String> queueCancel = redissonClient.getBlockingDeque("queue_cancel");

                //从队列获取消息
                try {
                    String id=queueCancel.take();

                    //取消订单
                    if(StringUtils.hasText(id)){
                        orderInfoService.cancelOrder(Long.parseLong(id));
                    }

                }catch (InterruptedException e){
                    throw new RuntimeException(e);
                }

            }
        }).start();
    }
}

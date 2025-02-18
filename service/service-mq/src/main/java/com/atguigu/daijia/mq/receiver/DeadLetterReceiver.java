package com.atguigu.daijia.mq.receiver;


import com.alibaba.cloud.commons.lang.StringUtils;
import com.atguigu.daijia.mq.config.DeadLetterMqConfig;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;


import java.io.IOException;

@Slf4j
@Component
public class DeadLetterReceiver {
    /**
     * 监听延迟消息
     * @param msg
     * @param message
     * @param channel
     */
    @RabbitListener(queues = {DeadLetterMqConfig.queue_dead_2})
    public void getDeadLetterMsg(String msg, Message message, Channel channel){
        try {
            if (StringUtils.isNotBlank(msg)) {
                log.info("死信消费者：{}", msg);
                System.out.println("RabbitListener:"+new String(message.getBody()));
            }
            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
        } catch (IOException e) {
            e.printStackTrace();
            log.error("[xx服务]监听xxx业务异常：{}", e);
        }
    }

    }


package com.atguigu.daijia.mq.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class DeadLetterMqConfig {
//    public static final String exchange_delay = "exchange.delay";
//    public static final String routing_delay = "routing.delay";
//    public static final String queue_delay_1 = "queue.delay.1";
//
//
//    @Bean
//    public Queue delayQueue1(){
//        // 第一个参数是创建的queue的名字，第二个参数是是否支持持久化
//        return new Queue(queue_delay_1,true);
//    }
//
//    @Bean
//    public CustomExchange delayExchange(){
//        Map<String,Object> args=new HashMap<>();
//        args.put("x-delayed-type", "direct");
//        return new CustomExchange(exchange_delay,"x-delayed-message",true,false,args);
//    }
//
//    @Bean
//    public Binding delayBbinding1() {
//        return BindingBuilder.bind(delayQueue1()).to(delayExchange()).with(routing_delay).noargs();
//    }
//    // 声明一些变量
//

    //死信交换机
    public static final String exchange_dead = "exchange.dead";
    //死信key
    public static final String routing_dead_1 = "routing.dead.1";
    public static final String routing_dead_2 = "routing.dead.2";
    //死信对列
    public static final String queue_dead_1 = "queue.dead.1";
    public static final String queue_dead_2 = "queue.dead.2";


    @Bean
    public DirectExchange exchange(){
        //设置持久化（durable = true）,重启之后消息不丢失.并且不自动删除(autoDelete = false)
        return new DirectExchange(exchange_dead,true,false,null);



    }
    //死信对列
    @Bean
    public Queue queue1(){
// 设置如果队列一 出现问题，则通过参数转到exchange_dead，routing_dead_2 上！
        HashMap<String,Object> map=new HashMap<>();
        // 参数绑定 此处的key 固定值，不能随意写
        map.put("x-dead-letter-exchange", exchange_dead);
        map.put("x-dead-letter-routing-key", routing_dead_2);
        // 设置延迟时间
        map.put("x-message-ttl", 10 * 1000);
        // 队列名称，是否持久化，是否独享、排外的【true:只可以在本次连接中访问】，是否自动删除，队列的其他属性参数
    return new Queue(queue_dead_1, true, false, false, map);
    }
    @Bean
    public Binding binding(){
        // 将队列一 通过routing_dead_1 key 绑定到exchange_dead 交换机上
        return BindingBuilder.bind(queue1()).to(exchange()).with(routing_dead_1);    }

    // 这个队列二就是一个普通队列
    @Bean
    public Queue queue2() {
        return new Queue(queue_dead_2, true, false, false, null);
    }
    // 设置队列二的绑定规则
    @Bean
    public Binding binding2() {
        // 将队列二通过routing_dead_2 key 绑定到exchange_dead交换机上！
        return BindingBuilder.bind(queue2()).to(exchange()).with(routing_dead_2);
    }
}



package com.atguigu.daijia.order.testLock;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class TestServiceImpl implements TestService{

    @Autowired
    StringRedisTemplate redisTemplate;



    @Autowired
    private RedissonClient redissonClient;



    /**
     * 使用Redison实现分布式锁
     * 开发步骤：
     * 1.使用RedissonClient客户端对象 创建锁对象
     * 2.调用获取锁方法
     * 3.执行业务逻辑
     * 4.将锁释放
     *
     */
    @Override
    public  void testLock() {

         //一、通过redisson来创建一个锁对象
        RLock lock1 = redissonClient.getLock("lock1");



        //二。尝试获取到锁
        //0.1.1 lock() 阻塞等待一直到获取锁,默认锁有效期30s
        lock1.lock();


//        //2.获取到锁，过期时间10s
//        lock1.lock(10,TimeUnit.SECONDS);
//
//
//        //3.第一个参数是获取到锁的等待时间，第二个参数是获取到锁后，过期时间
//        try {
//            lock1.tryLock(30,10,TimeUnit.SECONDS);
//        } catch (InterruptedException e) {
//            throw new RuntimeException(e);
//        }


        //三.业务代码


        //获取锁成功
        //1.先从redis里面通过key“num” 获取到值，key提前设置为num初始值为0、

        String s =redisTemplate.opsForValue().get("num");


        //2.如果值为空则返回空

        if(!StringUtils.hasText(s)){

            return;
        }
        //3.对num值进行自增加一

        int num=Integer.parseInt(s);
        redisTemplate.opsForValue().set("num",String.valueOf(++num));

        //四。释放锁

        lock1.unlock();



    }

    /**
     * 使用redis里面的setnx来实现分布式锁,uuid防止误删,LUA脚本来保证原子性
     */

//    @Override
//    public synchronized void testLock() {
//
//
//        //生成uuid
//
//
//        String uuid = UUID.randomUUID().toString();
//
//        //1获取到当前的锁
//
//
//        Boolean ifAbsent = redisTemplate.opsForValue().setIfAbsent("lock", uuid, 10, TimeUnit.SECONDS);
//
//
//        //2.如果获取到当前锁，从redis里面获取到数据，数据加1，放回到redis里面
//
//        if(ifAbsent){
//
//            //获取锁成功
//            //1.先从redis里面通过key“num” 获取到值，key提前设置为num初始值为0、
//
//            String s = stringRedisTemplate.opsForValue().get("num");
//
//
//            //2.如果值为空则返回空
//
//            if(!StringUtils.hasText(s)){
//
//                return;
//            }
//            //3.对num值进行自增加一
//
//            int num=Integer.parseInt(s);
//            stringRedisTemplate.opsForValue().set("num",String.valueOf(++num));
//
//
//            //4.将锁释放 判断uuid
//            //问题：删除操作缺乏原子性。
//            //if(uuid.equals(stringRedisTemplate.opsForValue().get("lock"))){ //线程一：判断是满足是当前线程锁的值
//            //    //条件满足，此时锁正好到期，redis锁自动释放了线程2获取锁成功，线程1将线程2的锁删除
//            //    stringRedisTemplate.delete("lock");
//            //}
//            //解决：redis执行lua脚本保证原子，lua脚本执行会作为一个整体执行
//
//            //执行脚本参数 参数1：脚本对象封装lua脚本，参数二：lua脚本中需要key参数（KEYS[i]）  参数三：lua脚本中需要参数值 ARGV[i]
//
//
//
//            //4.1 先创建脚本对象 DefaultRedisScript泛型脚本语言返回值类型 Long 0：失败 1：成功
//
//            DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>();
//
//            //4.2设置LUA脚本文本
//            String script = "if redis.call(\"get\",KEYS[1]) == ARGV[1]\n" +
//                    "then\n" +
//                    "    return redis.call(\"del\",KEYS[1])\n" +
//                    "else\n" +
//                    "    return 0\n" +
//                    "end";
//
//            redisScript.setScriptText(script);
//
//            //4.3设置响应脚本
//
//            redisScript.setResultType(Long.class);
//
//            stringRedisTemplate.execute(redisScript, Arrays.asList("lock"),uuid);
//
//
//        }else {
//
//
//            try{
//                Thread.sleep(100);
//                this.testLock();
//            }catch (InterruptedException e){
//                e.printStackTrace();
//            }
//        }






//    /**
//          * 使用redis里面的setnx来实现分布式锁,uuid防止误删
//         */
//
//    @Override
//    public synchronized void testLock() {
//
//
//        //生成uuid
//
//
//        String uuid = UUID.randomUUID().toString();
//
//        //1获取到当前的锁
//
//
//        Boolean ifAbsent = redisTemplate.opsForValue().setIfAbsent("lock", uuid, 10, TimeUnit.SECONDS);
//
//
//        //2.如果获取到当前锁，从redis里面获取到数据，数据加1，放回到redis里面
//
//        if(ifAbsent){
//
//            //获取锁成功
//            //1.先从redis里面通过key“num” 获取到值，key提前设置为num初始值为0、
//
//            String s = stringRedisTemplate.opsForValue().get("num");
//
//
//            //2.如果值为空则返回空
//
//            if(!StringUtils.hasText(s)){
//
//                return;
//            }
//            //3.对num值进行自增加一
//
//            int num=Integer.parseInt(s);
//            stringRedisTemplate.opsForValue().set("num",String.valueOf(++num));
//
//
//            //3.释放锁
//            String redisUUid = redisTemplate.opsForValue().get("lock");
//
//            if(uuid.equals(redisUUid)){
//
//
//                redisTemplate.delete("lock");
//
//            }
//
//        }else {
//
//
//            try{
//                Thread.sleep(100);
//                this.testLock();
//            }catch (InterruptedException e){
//                e.printStackTrace();
//            }
//        }








//
//    /**
//     * 使用redis里面的setnx来实现分布式锁
//     */
//
//
//    @Override
//    public synchronized void testLock() {
//
//
//
//        //1获取到当前的锁
//
//       //Boolean ifAbsent = redisTemplate.opsForValue().setIfAbsent("lock", "lock");
//
//        Boolean ifAbsent = redisTemplate.opsForValue().setIfAbsent("lock", "lock", 10, TimeUnit.SECONDS);
//
//
//        //2.如果获取到当前锁，从redis里面获取到数据，数据加1，放回到redis里面
//
//        if(ifAbsent){
//
//            //获取锁成功
//            //1.先从redis里面通过key“num” 获取到值，key提前设置为num初始值为0、
//
//            String s = stringRedisTemplate.opsForValue().get("num");
//
//
//            //2.如果值为空则返回空
//
//            if(!StringUtils.hasText(s)){
//
//                return;
//            }
//            //3.对num值进行自增加一
//
//            int num=Integer.parseInt(s);
//            stringRedisTemplate.opsForValue().set("num",String.valueOf(++num));
//
//
//            //3.释放锁
//            redisTemplate.delete("lock");
//        }else {
//
//
//            try{
//                Thread.sleep(100);
//                this.testLock();
//            }catch (InterruptedException e){
//                e.printStackTrace();
//            }
//        }
//
//



    /**
     * 本地锁的演示
     */

    public synchronized void testLock1() {


        //从redis里面获取数据
        String value = redisTemplate.opsForValue().get("num");

        //把redis里的数据加一

        if(!StringUtils.hasText(value)){
            return;
        }
        int num=Integer.parseInt(value);
        //数据加一后再放回redis里面去
        redisTemplate.opsForValue().set("num",String.valueOf(++num));
    }
}

package com.atguigu.daijia.order.service.impl;

import com.atguigu.daijia.common.constant.MqConst;
import com.atguigu.daijia.common.constant.RedisConstant;
import com.atguigu.daijia.common.constant.SystemConstant;
import com.atguigu.daijia.common.execption.GuiguException;
import com.atguigu.daijia.common.result.ResultCodeEnum;
import com.atguigu.daijia.common.service.RabbitService;
import com.atguigu.daijia.model.entity.order.*;
import com.atguigu.daijia.model.enums.OrderStatus;
import com.atguigu.daijia.model.form.order.OrderInfoForm;
import com.atguigu.daijia.model.form.order.StartDriveForm;
import com.atguigu.daijia.model.form.order.UpdateOrderBillForm;
import com.atguigu.daijia.model.form.order.UpdateOrderCartForm;
import com.atguigu.daijia.model.vo.base.PageVo;
import com.atguigu.daijia.model.vo.order.*;
import com.atguigu.daijia.order.mapper.OrderBillMapper;
import com.atguigu.daijia.order.mapper.OrderInfoMapper;
import com.atguigu.daijia.order.mapper.OrderProfitsharingMapper;
import com.atguigu.daijia.order.mapper.OrderStatusLogMapper;
import com.atguigu.daijia.order.service.OrderInfoService;
import com.atguigu.daijia.order.service.OrderMonitorService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.redisson.api.RBlockingDeque;
import org.redisson.api.RDelayedQueue;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@SuppressWarnings({"unchecked", "rawtypes"})
public class OrderInfoServiceImpl extends ServiceImpl<OrderInfoMapper, OrderInfo> implements OrderInfoService {

    @Autowired
    private OrderInfoMapper orderInfoMapper;
    @Autowired
    OrderStatusLogMapper orderStatusLogMapper;

    @Autowired
    RedisTemplate redisTemplate;
@Autowired
    RedissonClient redissonClient;

@Autowired
   RabbitService rabbitService;

@Autowired
    OrderMonitorService orderMonitorService;


@Autowired
    OrderBillMapper orderBillMapper;


@Autowired
    OrderProfitsharingMapper orderProfitsharingMapper;



    //乘客下单
    @Override
    public Long saveOrderInfo(OrderInfoForm orderInfoForm) {

        //添加订单信息到Order_info
        OrderInfo orderInfo = new OrderInfo();
        BeanUtils.copyProperties(orderInfoForm, orderInfo);
        //订单号
        String orderNo = UUID.randomUUID().toString().replaceAll("-","");
        orderInfo.setStatus(OrderStatus.WAITING_ACCEPT.getStatus());
        orderInfo.setOrderNo(orderNo);
        orderInfoMapper.insert(orderInfo);

        //生成订单之后，发送延迟队列消息
        this.sendDrlayMessage(orderInfo.getId());

        //记录日志
        this.log(orderInfo.getId(), orderInfo.getStatus());

        //发送延迟消息取消订单
        rabbitService.sendDelayMessage(MqConst.EXCHANGE_CANCEL_ORDER,MqConst.ROUTING_CANCEL_ORDER,String.valueOf(orderInfo.getId()),SystemConstant.CANCEL_ORDER_DELAY_TIME);






        //接单标识，标识不存在了说明不在等待接单状态了
        redisTemplate.opsForValue().set(RedisConstant.ORDER_ACCEPT_MARK, "0", RedisConstant.ORDER_ACCEPT_MARK_EXPIRES_TIME, TimeUnit.MINUTES);
        return orderInfo.getId();

    }

    //送延迟队列消息的方法
    private void sendDrlayMessage(Long id) {

        try {

            //创建队列

            RBlockingDeque<Object> queueCancel = redissonClient.getBlockingDeque("queue_cancel");

            //把创建队列放到延迟队列里面
            RDelayedQueue<Object> delayedQueue = redissonClient.getDelayedQueue(queueCancel);

            //发送消息到延迟队列里面
            //设置过期时间
            delayedQueue.offer(id.toString(),15,TimeUnit.MINUTES);
        }catch (Exception e){

            e.printStackTrace();
        }

    }

    @Override
    public Integer getOrderStatus(Long orderId) {
        //SELECT status FROM order_info WHERE id = #{orderId};
        LambdaQueryWrapper<OrderInfo> queryWrapper=new LambdaQueryWrapper<>();
        queryWrapper.eq(OrderInfo::getId,orderId);
        queryWrapper.select(OrderInfo::getStatus);
//        OrderInfo orderInfo = orderInfoMapper.selectById(orderId);
        OrderInfo orderInfo = orderInfoMapper.selectOne(queryWrapper);
        if(orderInfo==null){
            return OrderStatus.NULL_ORDER.getStatus();
        }
        return orderInfo.getStatus();
    }

    //Redisson分布式锁，解决司机抢单
    //司机抢单
    @Override
    public Boolean robNewOrder(Long driverId, Long orderId) {

        //1.判断订单是否存在,通过redis，减少数据库压力
        if(!redisTemplate.hasKey(RedisConstant.ORDER_ACCEPT_MARK)){
            //抢单失败
            throw new GuiguException(ResultCodeEnum.COB_NEW_ORDER_FAIL);
        }


        //创建锁

        RLock lock = redissonClient.getLock(RedisConstant.ROB_NEW_ORDER_LOCK + orderId);


        try {
            //获取到锁
            /**
             * TryLock是一种非阻塞式的分布式锁，实现原理：Redis的SETNX命令
             * 参数：
             *     waitTime：等待获取锁的时间
             *     leaseTime：加锁的时间
             */
            boolean flag = lock.tryLock(RedisConstant.ROB_NEW_ORDER_LOCK_WAIT_TIME,
                    RedisConstant.ROB_NEW_ORDER_LOCK_LEASE_TIME,
                    TimeUnit.SECONDS);

            if(flag){
                if(!redisTemplate.hasKey(RedisConstant.ORDER_ACCEPT_MARK)){
                    //抢单失败
                    throw new GuiguException(ResultCodeEnum.COB_NEW_ORDER_FAIL);
                }

                //司机抢单
                //2.修改订单状态和司机的id
                //update order_info set status = 2, driver_id = #{driverId}, accept_time = now() where id = #{id}
                //修改字段
//                LambdaQueryWrapper<OrderInfo> wrapper=new LambdaQueryWrapper<>();
//                wrapper.eq(OrderInfo::getId,orderId);
//
//                OrderInfo orderInfo = orderInfoMapper.selectOne(wrapper);
//                System.out.println("orderinfo的查询信息:::::;: "+orderInfo );

                OrderInfo orderInfo = new OrderInfo();
                orderInfo.setId(orderId);
                orderInfo.setStatus(OrderStatus.ACCEPTED.getStatus());
                orderInfo.setAcceptTime(new Date());
                orderInfo.setDriverId(driverId);

                int i = orderInfoMapper.updateById(orderInfo);

                if(i !=1){
                    //抢单失败
                    throw new GuiguException(ResultCodeEnum.COB_NEW_ORDER_FAIL);
                }
                //记录日志
                this.log(orderId, orderInfo.getStatus());

                //删除redis订单标识
                redisTemplate.delete(RedisConstant.ORDER_ACCEPT_MARK);
            }

        }catch (Exception e){
            //抢单失败
            throw new GuiguException(ResultCodeEnum.COB_NEW_ORDER_FAIL);
        }finally {

            //释放锁
            if(lock.isLocked()){
                lock.unlock();
            }
        }

        return true;
    }

    @Override
    public CurrentOrderInfoVo searchCustomerCurrentOrder(Long customerId) {
        //根据乘客id查找当前订单信息

        LambdaQueryWrapper<OrderInfo> wrapper=new LambdaQueryWrapper<>();
        wrapper.eq(OrderInfo::getCustomerId,customerId);
        //乘客端支付完订单，乘客端主要流程就走完（当前这些节点，乘客端会调整到相应的页面处理逻辑）

        Integer[] statusArray={
                OrderStatus.ACCEPTED.getStatus(),
                OrderStatus.DRIVER_ARRIVED.getStatus(),
                OrderStatus.UPDATE_CART_INFO.getStatus(),
                OrderStatus.START_SERVICE.getStatus(),
                OrderStatus.END_SERVICE.getStatus(),
                OrderStatus.UNPAID.getStatus()
        };
        wrapper.in(OrderInfo::getStatus,statusArray);
        wrapper.orderByDesc(OrderInfo::getId);
        wrapper.last("limit 1");
        OrderInfo orderInfo=orderInfoMapper.selectOne(wrapper);
        CurrentOrderInfoVo currentOrderInfoVo=new CurrentOrderInfoVo();
        if(orderInfo!=null){
            currentOrderInfoVo.setStatus(orderInfo.getStatus());
            currentOrderInfoVo.setOrderId(orderInfo.getId());
            currentOrderInfoVo.setIsHasCurrentOrder(true);
        }else {
            currentOrderInfoVo.setIsHasCurrentOrder(false);
        }
        return currentOrderInfoVo;
    }

    //司机端查找当前订单
    @Override
    public CurrentOrderInfoVo searchDriverCurrentOrder(Long driverId) {
        LambdaQueryWrapper<OrderInfo> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(OrderInfo::getDriverId, driverId);
        //司机发送完账单，司机端主要流程就走完（当前这些节点，司机端会调整到相应的页面处理逻辑）
        Integer[] statusArray = {
                OrderStatus.ACCEPTED.getStatus(),
                OrderStatus.DRIVER_ARRIVED.getStatus(),
                OrderStatus.UPDATE_CART_INFO.getStatus(),
                OrderStatus.START_SERVICE.getStatus(),
                OrderStatus.END_SERVICE.getStatus()
        };
        queryWrapper.in(OrderInfo::getStatus, statusArray);
        queryWrapper.orderByDesc(OrderInfo::getId);
        queryWrapper.last("limit 1");
        OrderInfo orderInfo = orderInfoMapper.selectOne(queryWrapper);
        CurrentOrderInfoVo currentOrderInfoVo = new CurrentOrderInfoVo();
        if(null != orderInfo) {
            currentOrderInfoVo.setStatus(orderInfo.getStatus());
            currentOrderInfoVo.setOrderId(orderInfo.getId());
            currentOrderInfoVo.setIsHasCurrentOrder(true);
        } else {
            currentOrderInfoVo.setIsHasCurrentOrder(false);
        }
        return currentOrderInfoVo;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public Boolean driverArriveStartLocation(Long orderId, Long driverId) {
        LambdaQueryWrapper<OrderInfo> lambdaQueryWrapper=new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(OrderInfo::getId,orderId);
        lambdaQueryWrapper.eq(OrderInfo::getDriverId,driverId);

        OrderInfo orderInfo=new OrderInfo();
        orderInfo.setStatus(OrderStatus.DRIVER_ARRIVED.getStatus());
        orderInfo.setArriveTime(new Date());
        int update = orderInfoMapper.update(orderInfo, lambdaQueryWrapper);
        if(update==1){
            this.log(orderId,OrderStatus.DRIVER_ARRIVED.getStatus());
        }else {
            throw new GuiguException(ResultCodeEnum.UPDATE_ERROR);
        }
        return true;
    }

    //更新代驾车辆信息
    @Override
    public Boolean updateOrderCart(UpdateOrderCartForm updateOrderCartForm) {

        LambdaQueryWrapper<OrderInfo> wrapper=new LambdaQueryWrapper<>();
        wrapper.eq(OrderInfo::getDriverId,updateOrderCartForm.getDriverId());
        wrapper.eq(OrderInfo::getId,updateOrderCartForm.getOrderId());
        OrderInfo orderInfo=new OrderInfo();
        BeanUtils.copyProperties(updateOrderCartForm,orderInfo);
        orderInfo.setStatus(OrderStatus.UPDATE_CART_INFO.getStatus());

        int update = orderInfoMapper.update(orderInfo, wrapper);
        if(update==1){
            this.log(updateOrderCartForm.getOrderId(), OrderStatus.UPDATE_CART_INFO.getStatus());
        }else {
            throw new GuiguException(ResultCodeEnum.UPDATE_ERROR);
        }
return true;

    }


    //开始代驾服务
    @Override
    public Boolean startDrive(StartDriveForm driveForm) {

        LambdaQueryWrapper<OrderInfo> lambdaQueryWrapper=new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(OrderInfo::getId,driveForm.getOrderId());
        lambdaQueryWrapper.eq(OrderInfo::getDriverId,driveForm.getDriverId());

        OrderInfo orderInfo=new OrderInfo();
        orderInfo.setStatus(OrderStatus.START_SERVICE.getStatus());
        orderInfo.setStartServiceTime(new Date());
        int update = orderInfoMapper.update(orderInfo, lambdaQueryWrapper);
        if(update==1){
            this.log(driveForm.getOrderId(), OrderStatus.START_SERVICE.getStatus());
        }else {
            throw new GuiguException(ResultCodeEnum.UPDATE_ERROR);
        }


        //初始化订单监控统计数据
        OrderMonitor orderMonitor = new OrderMonitor();
        orderMonitor.setOrderId(driveForm.getOrderId());
        orderMonitorService.saveOrderMonitor(orderMonitor);
        return true;
    }


    //根据时间段获取订单数
    @Override
    public Long getOrderNumByTime(String startTime, String endTime) {

        //这两行代码的效果是构建一个查询条件，查询满足以下条件的 OrderInfo 表的记录：
        //
        //startServiceTime >= startTime（startServiceTime 大于等于 startTime）
        //endServiceTime < endTime（endServiceTime 小于 endTime）

        //09<=time<10<=time1<11

        // 转换字符串到 LocalDateTime
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        if (startTime != null && startTime.endsWith("24:00:00")) {
            // 将 startTime 转换为 LocalDateTime
            LocalDateTime startDateTime = LocalDateTime.parse(startTime, formatter);
            startDateTime = startDateTime.plusDays(1).toLocalDate().atStartOfDay(); // 增加一天，设置为00:00:00
            startTime = startDateTime.format(formatter); // 转换回字符串
        }

        if (endTime != null && endTime.endsWith("24:00:00")) {
            // 将 endTime 转换为 LocalDateTime
            LocalDateTime endDateTime = LocalDateTime.parse(endTime, formatter);
            endDateTime = endDateTime.plusDays(1).toLocalDate().atStartOfDay(); // 增加一天，设置为00:00:00
            endTime = endDateTime.format(formatter); // 转换回字符串
        }
        LambdaQueryWrapper<OrderInfo> lambdaQueryWrapper=new LambdaQueryWrapper<>();
        lambdaQueryWrapper.ge(OrderInfo::getStartServiceTime,startTime);
        lambdaQueryWrapper.lt(OrderInfo::getStartServiceTime,endTime);
        Long l = orderInfoMapper.selectCount(lambdaQueryWrapper);

        return l;
    }

    //"结束代驾服务更新订单账单
    @Override
    public Boolean endDrive(UpdateOrderBillForm updateOrderBillForm) {
        //更新订单信息
        LambdaQueryWrapper<OrderInfo> wrapper=new LambdaQueryWrapper<>();
        wrapper.eq(OrderInfo::getId,updateOrderBillForm.getOrderId());
        wrapper.eq(OrderInfo::getDriverId,updateOrderBillForm.getDriverId());
        //更新字段
        OrderInfo orderInfo=new OrderInfo();
        orderInfo.setStatus(OrderStatus.END_SERVICE.getStatus());
        orderInfo.setRealAmount(updateOrderBillForm.getTotalAmount());
        orderInfo.setFavourFee(updateOrderBillForm.getFavourFee());
        orderInfo.setEndServiceTime(new Date());
        orderInfo.setRealDistance(updateOrderBillForm.getRealDistance());

        //只能更新自己的订单
        int update = orderInfoMapper.update(orderInfo, wrapper);
        if(update==1){
            //记录日志
            this.log(updateOrderBillForm.getOrderId(), OrderStatus.END_SERVICE.getStatus());



            //插入实际账单信息
            OrderBill orderBill=new OrderBill();
            BeanUtils.copyProperties(updateOrderBillForm,orderBill);
            orderBill.setOrderId(updateOrderBillForm.getOrderId());
            orderBill.setPayAmount(updateOrderBillForm.getTotalAmount());

            orderBillMapper.insert(orderBill);
            //插入分账信息数据
            OrderProfitsharing orderProfitsharing=new OrderProfitsharing();
            orderProfitsharing.setOrderId(updateOrderBillForm.getOrderId());
            BeanUtils.copyProperties(updateOrderBillForm,orderProfitsharing);
            orderProfitsharing.setRuleId(updateOrderBillForm.getProfitsharingRuleId());
            System.out.println(updateOrderBillForm.getProfitsharingRuleId());
            orderProfitsharing.setStatus(1);

            orderProfitsharingMapper.insert(orderProfitsharing);
        }else {
            throw new GuiguException(ResultCodeEnum.UPDATE_ERROR);
        }


        return true;

    }

    //获取乘客订单分页列表
    @Override
    public PageVo findCustomerOrderPage(Page<OrderInfo> pageParm, Long customerId) {
        IPage<OrderListVo> pageInfo =   orderInfoMapper.selectCustomerOrderPage(pageParm,customerId);
        return new PageVo<>(pageInfo.getRecords(),pageInfo.getPages(),pageInfo.getTotal());
    }

    @Override
    public PageVo findDriverOrderPage(Page<OrderInfo> pageParam, Long driverId) {

        IPage<OrderListVo> pageInfo=orderInfoMapper.selectDriverOrderPage(pageParam,driverId);
        return new PageVo<>(pageInfo.getRecords(),pageInfo.getPages(),pageInfo.getTotal());
    }


    //根据订单id获取实际分账信息
    @Override
    public OrderProfitsharingVo getOrderProfitsharing(Long orderId) {
        LambdaQueryWrapper<OrderProfitsharing> lambdaQueryWrapper=new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(OrderProfitsharing::getOrderId,orderId);
        OrderProfitsharing orderProfitsharing = orderProfitsharingMapper.selectOne(lambdaQueryWrapper);
        OrderProfitsharingVo orderProfitsharingVo=new OrderProfitsharingVo();
        BeanUtils.copyProperties(orderProfitsharing,orderProfitsharingVo);
        orderProfitsharingVo.setStatus(orderProfitsharing.getStatus());
        return orderProfitsharingVo;
    }

    //根据订单id获取实际账单信息
    @Override
    public OrderBillVo getOrderBillInfo(Long orderId) {
        LambdaQueryWrapper<OrderBill> wrapper=new LambdaQueryWrapper<>();
        wrapper.eq(OrderBill::getOrderId,orderId);
        OrderBill orderBill = orderBillMapper.selectOne(wrapper);
        OrderBillVo orderBillVo=new OrderBillVo();
        BeanUtils.copyProperties(orderBill,orderBillVo);
        return orderBillVo;
    }

    //发送账单信息
    @Override
    public Boolean sendOrderBillInfo(Long orderId, Long driverId) {
        LambdaQueryWrapper<OrderInfo> wrapper=new LambdaQueryWrapper<>();
        wrapper.eq(OrderInfo::getId,orderId);
        wrapper.eq(OrderInfo::getDriverId,driverId);
        //更新字段
        OrderInfo updateOrderInfo=new OrderInfo();
        updateOrderInfo.setStatus(OrderStatus.UNPAID.getStatus());
        //只能更新自己的订单
        int update = orderInfoMapper.update(updateOrderInfo, wrapper);
        if(update==1){
            //记录日志
            this.log(orderId, OrderStatus.UNPAID.getStatus());
            return true;
        } else {
            throw new GuiguException(ResultCodeEnum.UPDATE_ERROR);
        }


    }

    //获取订单支付信息
    @Override
    public OrderPayVo getOrderPayVo(String orderNo, Long customerId) {
    OrderPayVo orderPayVo=   orderInfoMapper.selectOrderPayVo(orderNo,customerId);
    if(orderPayVo!=null){
        String content = orderPayVo.getStartLocation() + " 到 " + orderPayVo.getEndLocation();
        orderPayVo.setContent(content);

    }
    return orderPayVo;
    }


    @Override
    public Boolean updateOrderPayStatus(String orderNo) {
        //1 根据订单编号查询，判断订单状态
        LambdaQueryWrapper<OrderInfo> wrapper=new LambdaQueryWrapper<>();
        wrapper.eq(OrderInfo::getOrderNo,orderNo);
        OrderInfo orderInfo = orderInfoMapper.selectOne(wrapper);
        if(orderInfo==null||orderInfo.getStatus().intValue()==OrderStatus.PAID.getStatus().intValue()){
            return true;
        }

        //更新状态
        LambdaQueryWrapper<OrderInfo> updateWrapper = new LambdaQueryWrapper<>();
        updateWrapper.eq(OrderInfo::getOrderNo,orderNo);

        OrderInfo updateOrderInfo = new OrderInfo();
        updateOrderInfo.setStatus(OrderStatus.PAID.getStatus());
        updateOrderInfo.setPayTime(new Date());


        int update = orderInfoMapper.update(updateOrderInfo, updateWrapper);
        if(update == 1) {
            return true;
        } else {


            throw new GuiguException(ResultCodeEnum.UPDATE_ERROR);
        }


        //
    }


    //获取订单的系统奖励
    @Override
    public OrderRewardVo getOrderRewardFee(String orderNo) {

        //  //根据订单编号查询订单表
        LambdaQueryWrapper<OrderInfo> wrapper=new LambdaQueryWrapper<>();
        wrapper.eq(OrderInfo::getOrderNo,orderNo);
        OrderInfo orderInfo = orderInfoMapper.selectOne(wrapper);


        //账单   //根据订单id查询系统奖励表
        LambdaQueryWrapper<OrderBill> lambdaQueryWrapper=new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(OrderBill::getOrderId,orderInfo.getId());
        OrderBill orderBill = orderBillMapper.selectOne(lambdaQueryWrapper);
        OrderRewardVo orderRewardVo=new OrderRewardVo();
        orderRewardVo.setOrderId(orderInfo.getId());
        orderRewardVo.setDriverId(orderInfo.getDriverId());
        orderRewardVo.setRewardFee(orderBill.getRewardFee());

        return orderRewardVo;

    }

    @Override
    public void cancelOrder(long l) {
        //orderId 查询订单信息
        OrderInfo orderInfo = orderInfoMapper.selectById(l);
        //判断
        if(orderInfo.getStatus().intValue()==OrderStatus.WAITING_ACCEPT.getStatus()){
            orderInfo.setStatus(OrderStatus.CANCEL_ORDER.getStatus());
            int i = orderInfoMapper.updateById(orderInfo);
            if(i==1){
                //删除接单标识

                redisTemplate.delete(RedisConstant.ORDER_ACCEPT_MARK);
            }
        }
    }

    //乘客取消订单
    @Transactional
    @Override
    public void systemOrderCancel(Long orderId) {
        Integer orderStatus = this.getOrderStatus(orderId);
        if(null != orderStatus && orderStatus.intValue() == OrderStatus.WAITING_ACCEPT.getStatus().intValue()) {
            //取消订单
            OrderInfo orderInfo = new OrderInfo();
            orderInfo.setId(orderId);
            orderInfo.setStatus(OrderStatus.CANCEL_ORDER.getStatus());
            int row = orderInfoMapper.updateById(orderInfo);
            if(row == 1) {
                //记录日志
                this.log(orderInfo.getId(), orderInfo.getStatus());

                //删除redis订单标识
                redisTemplate.delete(RedisConstant.ORDER_ACCEPT_MARK);
            } else {
                throw new GuiguException(ResultCodeEnum.UPDATE_ERROR);
            }
        }



    }

    //更新订单优惠券金额
    @Transactional(rollbackFor = Exception.class)
    @Override
    public Boolean updateCouponAmount(Long orderId, BigDecimal couponAmount) {
      int rows= orderBillMapper.updateCouponAmount(orderId,couponAmount);
      if(rows!=1){
          throw new GuiguException(ResultCodeEnum.UPDATE_ERROR);
      }
      return true;
    }

    /**
    """用乐观锁方式解决司机过度抢单问题，"""


    @Transactional(rollbackFor = Exception.class)
    public Boolean robNewOrder1(Long driverId, Long orderId) {
        //抢单成功或取消订单，都会删除该key，redis判断，减少数据库压力
        if(!redisTemplate.hasKey(RedisConstant.ORDER_ACCEPT_MARK)) {
            //抢单失败
            throw new GuiguException(ResultCodeEnum.COB_NEW_ORDER_FAIL);
        }

        //修改订单状态及司机id
        //update order_info set status = 2, driver_id = #{driverId}, accept_time = now() where id = #{id} and status =1
        //条件
        LambdaQueryWrapper<OrderInfo> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(OrderInfo::getId, orderId);
        queryWrapper.eq(OrderInfo::getStatus, OrderStatus.WAITING_ACCEPT.getStatus());

        //修改字段
        OrderInfo orderInfo = new OrderInfo();
        orderInfo.setStatus(OrderStatus.ACCEPTED.getStatus());
        orderInfo.setAcceptTime(new Date());
        orderInfo.setDriverId(driverId);
        int rows = orderInfoMapper.update(orderInfo, queryWrapper);
        if(rows != 1) {
            //抢单失败
            throw new GuiguException(ResultCodeEnum.COB_NEW_ORDER_FAIL);
        }

        //记录日志
        this.log(orderId, orderInfo.getStatus());

        //删除redis订单标识
        redisTemplate.delete(RedisConstant.ORDER_ACCEPT_MARK);
        return true;
    }
 """"
     */

    public  void  log(Long orderId,Integer status){
        OrderStatusLog orderStatusLog=new OrderStatusLog();
        orderStatusLog.setOrderId(orderId);
        orderStatusLog.setOrderStatus(status);
        orderStatusLog.setOperateTime(new Date());
        orderStatusLogMapper.insert(orderStatusLog);
    }
}

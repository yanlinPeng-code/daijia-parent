package com.atguigu.daijia.customer.service.impl;

import com.atguigu.daijia.common.execption.GuiguException;
import com.atguigu.daijia.common.result.Result;
import com.atguigu.daijia.common.result.ResultCodeEnum;
import com.atguigu.daijia.coupon.client.CouponFeignClient;
import com.atguigu.daijia.customer.client.CustomerInfoFeignClient;
import com.atguigu.daijia.customer.service.OrderService;
import com.atguigu.daijia.dispatch.client.NewOrderFeignClient;
import com.atguigu.daijia.driver.client.DriverInfoFeignClient;
import com.atguigu.daijia.map.client.LocationFeignClient;
import com.atguigu.daijia.map.client.MapFeignClient;
import com.atguigu.daijia.map.client.WxPayFeignClient;
import com.atguigu.daijia.model.entity.order.OrderInfo;
import com.atguigu.daijia.model.entity.payment.PaymentInfo;
import com.atguigu.daijia.model.enums.OrderStatus;
import com.atguigu.daijia.model.form.coupon.UseCouponForm;
import com.atguigu.daijia.model.form.customer.ExpectOrderForm;
import com.atguigu.daijia.model.form.customer.SubmitOrderForm;
import com.atguigu.daijia.model.form.map.CalculateDrivingLineForm;
import com.atguigu.daijia.model.form.order.OrderInfoForm;
import com.atguigu.daijia.model.form.payment.CreateWxPaymentForm;
import com.atguigu.daijia.model.form.payment.PaymentInfoForm;
import com.atguigu.daijia.model.form.rules.FeeRuleRequest;
import com.atguigu.daijia.model.form.rules.FeeRuleRequestForm;
import com.atguigu.daijia.model.vo.base.PageVo;
import com.atguigu.daijia.model.vo.customer.ExpectOrderVo;
import com.atguigu.daijia.model.vo.dispatch.NewOrderTaskVo;
import com.atguigu.daijia.model.vo.driver.DriverInfoVo;
import com.atguigu.daijia.model.vo.map.DrivingLineVo;
import com.atguigu.daijia.model.vo.map.OrderLocationVo;
import com.atguigu.daijia.model.vo.map.OrderServiceLastLocationVo;
import com.atguigu.daijia.model.vo.order.CurrentOrderInfoVo;
import com.atguigu.daijia.model.vo.order.OrderBillVo;
import com.atguigu.daijia.model.vo.order.OrderInfoVo;
import com.atguigu.daijia.model.vo.order.OrderPayVo;
import com.atguigu.daijia.model.vo.payment.WxPrepayVo;
import com.atguigu.daijia.model.vo.rules.FeeRuleResponseVo;
import com.atguigu.daijia.order.client.OrderInfoFeignClient;
import com.atguigu.daijia.rules.client.FeeRuleFeignClient;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import io.seata.spring.annotation.GlobalTransactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Date;

@Slf4j
@Service
@SuppressWarnings({"unchecked", "rawtypes"})
public class OrderServiceImpl implements OrderService {

    @Autowired
    FeeRuleFeignClient feeRuleFeignClient;

    @Autowired
    MapFeignClient mapFeignClient;

    @Autowired
    OrderInfoFeignClient orderInfoFeignClient;

    @Autowired
    NewOrderFeignClient newOrderFeignClient;
    @Autowired
    DriverInfoFeignClient driverInfoFeignClient;
    @Autowired
    private LocationFeignClient locationFeignClient;

    @Autowired
    WxPayFeignClient wxPayFeignClient;


    @Autowired
    CustomerInfoFeignClient customerInfoFeignClient;


    @Autowired
    CouponFeignClient couponFeignClient;

    //预估订单数据路线规划+订单估计
    @Override
    public ExpectOrderVo expectOrderResult(ExpectOrderForm expectOrderForm) {


        //获取驾驶路线

        CalculateDrivingLineForm calculateDrivingLineForm=new CalculateDrivingLineForm();
        BeanUtils.copyProperties(expectOrderForm,calculateDrivingLineForm);
        Result<DrivingLineVo> drivingLineVoResult = mapFeignClient.calculateDrivingLine(calculateDrivingLineForm);
        DrivingLineVo drivingLineVo = drivingLineVoResult.getData();


        //获取订单预估金额
        FeeRuleRequestForm feeRuleRequestForm=new FeeRuleRequestForm();
        feeRuleRequestForm.setDistance(drivingLineVo.getDistance());
        feeRuleRequestForm.setStartTime(new Date());
        feeRuleRequestForm.setWaitMinute(0);

        Result<FeeRuleResponseVo> feeRuleResponseVoResult = feeRuleFeignClient.calculateOrderFee(feeRuleRequestForm);
        FeeRuleResponseVo data = feeRuleResponseVoResult.getData();



        //封装到ExpectOrderVo当中并返回到前端
        ExpectOrderVo expectOrderVo=new ExpectOrderVo();
        expectOrderVo.setDrivingLineVo(drivingLineVo);
        expectOrderVo.setFeeRuleResponseVo(data);


        return expectOrderVo;
    }

    //乘客下单,返回订单id
    @Override
    public Long submitOrder(SubmitOrderForm submitOrderForm) {
        //1.重新计算驾驶线路
        CalculateDrivingLineForm calculateDrivingLineForm = new CalculateDrivingLineForm();
        BeanUtils.copyProperties(submitOrderForm, calculateDrivingLineForm);
        DrivingLineVo drivingLineVo = mapFeignClient.calculateDrivingLine(calculateDrivingLineForm).getData();

        //2.重新计算订单费用
        FeeRuleRequestForm calculateOrderFeeForm = new FeeRuleRequestForm();
        calculateOrderFeeForm.setDistance(drivingLineVo.getDistance());
        calculateOrderFeeForm.setStartTime(new Date());
        calculateOrderFeeForm.setWaitMinute(0);
        FeeRuleResponseVo feeRuleResponseVo = feeRuleFeignClient.calculateOrderFee(calculateOrderFeeForm).getData();

        //3.封装订单信息对象
        OrderInfoForm orderInfoForm = new OrderInfoForm();
        //订单位置信息
        BeanUtils.copyProperties(submitOrderForm, orderInfoForm);
        //预估里程
        orderInfoForm.setExpectDistance(drivingLineVo.getDistance());
        orderInfoForm.setExpectAmount(feeRuleResponseVo.getTotalAmount());

        //4.保存订单信息
        Long orderId = orderInfoFeignClient.saveOrderInfo(orderInfoForm).getData();

        //5.添加并执行任务调度，每分钟执行一次，搜索附近司机
        //5.1.封装调度参数对象
        NewOrderTaskVo newOrderDispatchVo = new NewOrderTaskVo();
        newOrderDispatchVo.setOrderId(orderId);
        newOrderDispatchVo.setStartLocation(orderInfoForm.getStartLocation());
        newOrderDispatchVo.setStartPointLongitude(orderInfoForm.getStartPointLongitude());
        newOrderDispatchVo.setStartPointLatitude(orderInfoForm.getStartPointLatitude());
        newOrderDispatchVo.setEndLocation(orderInfoForm.getEndLocation());
        newOrderDispatchVo.setEndPointLongitude(orderInfoForm.getEndPointLongitude());
        newOrderDispatchVo.setEndPointLatitude(orderInfoForm.getEndPointLatitude());
        newOrderDispatchVo.setExpectAmount(orderInfoForm.getExpectAmount());
        newOrderDispatchVo.setExpectDistance(orderInfoForm.getExpectDistance());
        newOrderDispatchVo.setExpectTime(drivingLineVo.getDuration());
        newOrderDispatchVo.setFavourFee(orderInfoForm.getFavourFee());
        newOrderDispatchVo.setCreateTime(new Date());
        //5.2.添加并执行任务调度
        Long jobId = newOrderFeignClient.addAndStartTask(newOrderDispatchVo).getData();
        log.info("订单id为： {}，绑定任务id为：{}", orderId, jobId);
        return orderId;
    }

    //查询订单状态
    @Override
    public Integer getOrderStatus(Long orderId) {
        return orderInfoFeignClient.getOrderStatus(orderId).getData();
    }

    //乘客端查找当前订单
    @Override
    public CurrentOrderInfoVo searchCustomerCurrentOrder(Long customerId) {
        return orderInfoFeignClient.searchCustomerCurrentOrder(customerId).getData();
    }

    //获取订单信息
    @Override
    public OrderInfoVo getOrderInfo(Long orderId, Long customerId) {
        //订单信息
        OrderInfo orderInfo = orderInfoFeignClient.getOrderInfo(orderId).getData();
        if(orderInfo.getCustomerId().longValue()!=customerId.longValue()){
            throw new GuiguException(ResultCodeEnum.ILLEGAL_REQUEST);
        }

        //获取司机信息
        DriverInfoVo driverInfoVo=null;
        if(orderInfo.getDriverId()!=null){
           driverInfoVo= driverInfoFeignClient.getDriverBaseInfo(orderInfo.getDriverId()).getData();
        }
        //账单信息
        OrderBillVo orderBillVo=null;
        if(orderInfo.getStatus().intValue()>= OrderStatus.UNPAID.getStatus().intValue()){
            orderBillVo = orderInfoFeignClient.getOrderBillInfo(orderId).getData();
        }

        //封装订单信息
        OrderInfoVo orderInfoVo=new OrderInfoVo();
        orderInfoVo.setOrderId(orderId);
        BeanUtils.copyProperties(orderInfo,orderInfoVo);
        orderInfoVo.setOrderBillVo(orderBillVo);
        orderInfoVo.setDriverInfoVo(driverInfoVo);


        return orderInfoVo;


    }

    //根据订单id获取司机基本信息
    @Override
    public DriverInfoVo getDriverBaseInfo(Long orderId, Long customerId) {
        //根据订单id获取订单信息
        OrderInfo orderInfo=orderInfoFeignClient.getOrderInfo(orderId).getData();
        if(orderInfo.getCustomerId().longValue()!=customerId.longValue()){
            throw new GuiguException(ResultCodeEnum.ILLEGAL_REQUEST);
        }
        return driverInfoFeignClient.getDriverBaseInfo(orderInfo.getDriverId()).getData();
    }

    //司机赶往代驾起始点：获取订单经纬度位置
    @Override
    public OrderLocationVo getCacheOrderLocation(Long orderId) {
       return locationFeignClient.getCacheOrderLocation(orderId).getData();
    }

    @Override
    public DrivingLineVo calculateDrivingLine(CalculateDrivingLineForm calculateDrivingLineForm) {
        return mapFeignClient.calculateDrivingLine(calculateDrivingLineForm).getData();
    }


    //代驾服务：获取订单服务最后一个位置信息
    @Override
    public OrderServiceLastLocationVo getOrderServiceLastLocation(Long orderId) {
        return locationFeignClient.getOrderServiceLastLocation(orderId).getData();
    }

    @Override
    public PageVo findCustomerOrderPage(Long customerId, Long page, Long limit) {
        return orderInfoFeignClient.findCustomerOrderPage(customerId,page,limit).getData();
    }


    //创建微信支付
  @GlobalTransactional
    @Override
    public WxPrepayVo createWxPayment(CreateWxPaymentForm createWxPaymentForm) {
      //获取订单支付相关的信息
        OrderPayVo orderPayVo = orderInfoFeignClient.getOrderPayVo(createWxPaymentForm.getOrderNo(), createWxPaymentForm.getCustomerId()).getData();

        //判断是否在未支付状态
        if(orderPayVo.getStatus().intValue()!=OrderStatus.UNPAID.getStatus().intValue()){
            throw new GuiguException(ResultCodeEnum.ILLEGAL_REQUEST);
        }


        //获取客户的openid
        String customerOpenId = customerInfoFeignClient.getCustomerOpenId(orderPayVo.getCustomerId()).getData();




        //获取司机的Openid

        String driverOpenId = driverInfoFeignClient.getDriverOpenId(orderPayVo.getDriverId()).getData();

        //4.处理优惠券
        BigDecimal couponAmount=null;
        //支付时选择过一次优惠券，如果支付失败或未支付，下次支付时不能再次选择，只能使用第一次选中的优惠券（前端已控制，后端再次校验）
        if(orderPayVo.getCouponAmount()==null&&createWxPaymentForm.getCustomerCouponId()!=null&&createWxPaymentForm.getCustomerCouponId()!=0){
            UseCouponForm useCouponForm=new UseCouponForm();
            useCouponForm.setOrderId(orderPayVo.getOrderId());
            useCouponForm.setCustomerCouponId(createWxPaymentForm.getCustomerCouponId());
            useCouponForm.setOrderAmount(orderPayVo.getPayAmount());
            useCouponForm.setCustomerId(createWxPaymentForm.getCustomerId());
            couponAmount = couponFeignClient.useCoupon(useCouponForm).getData();
        }

        //5.更新账单优惠券金额
        //支付金额
        BigDecimal payAmount=orderPayVo.getPayAmount();
        if(couponAmount!=null){
            Boolean data = orderInfoFeignClient.updateCouponAmount(orderPayVo.getOrderId(), couponAmount).getData();
            if(!data){
                throw new GuiguException(ResultCodeEnum.DATA_ERROR);
            }
            //当前支付金额 = 支付金额 - 优惠券金额
            payAmount = payAmount.subtract(couponAmount);
        }






        //6.封装微信下单对象，微信支付只关注以下订单属性
        PaymentInfoForm paymentInfoForm = new PaymentInfoForm();
        paymentInfoForm.setCustomerOpenId(customerOpenId);
        paymentInfoForm.setDriverOpenId(driverOpenId);
        paymentInfoForm.setOrderNo(orderPayVo.getOrderNo());
        paymentInfoForm.setAmount(payAmount);
        paymentInfoForm.setContent(orderPayVo.getContent());
        paymentInfoForm.setPayWay(1);
        WxPrepayVo wxPrepayVo = wxPayFeignClient.createWxPayment(paymentInfoForm).getData();
        return wxPrepayVo;

        //封装WxprepayVo对象返回


        //
    }

    //支付状态查询
    @Override
    public Boolean queryPayStatus(String orderNo) {
        return wxPayFeignClient.queryPayStatus(orderNo).getData();
    }
}

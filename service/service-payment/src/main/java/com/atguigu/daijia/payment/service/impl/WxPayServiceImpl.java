package com.atguigu.daijia.payment.service.impl;


import com.alibaba.fastjson.JSON;
import com.atguigu.daijia.common.constant.MqConst;
import com.atguigu.daijia.common.execption.GuiguException;
import com.atguigu.daijia.common.result.ResultCodeEnum;
import com.atguigu.daijia.common.service.RabbitService;
import com.atguigu.daijia.common.util.RequestUtils;
import com.atguigu.daijia.driver.client.DriverAccountFeignClient;
import com.atguigu.daijia.model.entity.payment.PaymentInfo;
import com.atguigu.daijia.model.enums.TradeType;
import com.atguigu.daijia.model.form.driver.TransferForm;
import com.atguigu.daijia.model.form.payment.PaymentInfoForm;
import com.atguigu.daijia.model.vo.order.OrderRewardVo;
import com.atguigu.daijia.model.vo.payment.WxPrepayVo;
import com.atguigu.daijia.order.client.OrderInfoFeignClient;
import com.atguigu.daijia.payment.config.WxPayV3Properties;
import com.atguigu.daijia.payment.mapper.PaymentInfoMapper;
import com.atguigu.daijia.payment.service.WxPayService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wechat.pay.java.core.RSAAutoCertificateConfig;
import com.wechat.pay.java.core.exception.ServiceException;
import com.wechat.pay.java.core.notification.NotificationParser;
import com.wechat.pay.java.core.notification.RequestParam;
import com.wechat.pay.java.service.payments.jsapi.JsapiServiceExtension;
import com.wechat.pay.java.service.payments.jsapi.model.*;
import com.wechat.pay.java.service.payments.model.Transaction;
//import io.seata.spring.annotation.GlobalTransactional;
import io.seata.spring.annotation.GlobalTransactional;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.invoke.LambdaConversionException;
import java.math.BigDecimal;
import java.util.Date;


@Service
@Slf4j
public class WxPayServiceImpl implements WxPayService {

    @Autowired
    PaymentInfoMapper paymentInfoMapper;
    @Autowired
    RSAAutoCertificateConfig rsaAutoCertificateConfig;


    @Autowired
    WxPayV3Properties wxPayV3Properties;

    @Autowired
    RabbitService rabbitService;

    @Autowired
    OrderInfoFeignClient orderInfoFeignClient;


    @Autowired
    DriverAccountFeignClient driverAccountFeignClient;



    @Override
    public WxPrepayVo createWxPayment(PaymentInfoForm paymentInfoForm) {

        try {
            //1 添加支付记录到支付表里面

            //根据订单的支付信息，如果已经存在，就不需要添加
            LambdaQueryWrapper<PaymentInfo> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(PaymentInfo::getOrderNo, paymentInfoForm.getOrderNo());
            PaymentInfo paymentInfo = paymentInfoMapper.selectOne(wrapper);
            if (paymentInfo == null) {
                paymentInfo = new PaymentInfo();
                BeanUtils.copyProperties(paymentInfoForm, paymentInfo);
                paymentInfo.setPaymentStatus(0);
                paymentInfoMapper.insert(paymentInfo);
            }
            //2.创建微信支付对象
            JsapiServiceExtension service =
                    new JsapiServiceExtension.Builder().config(rsaAutoCertificateConfig).build();



            //3.创建request对象，封装微信支付需要参数
            PrepayRequest request = new PrepayRequest();
            Amount amount = new Amount();
//            amount.setTotal(paymentInfoForm.getAmount().multiply(new BigDecimal(100)).intValue());

            amount.setTotal(1);//TODO为了测试 ，支付一分钱
            request.setAmount(amount);
            request.setAppid(wxPayV3Properties.getAppid());
            request.setMchid(wxPayV3Properties.getMerchantId());
            ////string[1,127]
            String content = paymentInfo.getContent();
            if (content.length() > 127) {
                content = content.substring(0, 127);
            }
            request.setDescription(content);
            request.setNotifyUrl(wxPayV3Properties.getNotifyUrl());

            request.setOutTradeNo(paymentInfo.getOrderNo());

            //获取用户信息
            Payer payer = new Payer();
            payer.setOpenid(paymentInfoForm.getCustomerOpenId());
            request.setPayer(payer);

            //是否指定分账，不指定不能分账
            SettleInfo settleInfo = new SettleInfo();
            settleInfo.setProfitSharing(true);
            request.setSettleInfo(settleInfo);


            //
            //4 调用微信支付使用对象里面方法实现微信支付调用
            PrepayWithRequestPaymentResponse prepayWithRequestPaymentResponse = service.prepayWithRequestPayment(request);

            //5 根据返回结果，封装到WxPrepayVo里面
            WxPrepayVo wxPrepayVo = new WxPrepayVo();
            BeanUtils.copyProperties(prepayWithRequestPaymentResponse, wxPrepayVo);
            wxPrepayVo.setTimeStamp(prepayWithRequestPaymentResponse.getTimeStamp());
            return wxPrepayVo;


        } catch (Exception e) {
            throw new GuiguException(ResultCodeEnum.DATA_ERROR);
        }

    }


    //支付状态查询
    @Override
    public Boolean queryPayStatus(String orderNo) {
        // 构建service
        JsapiServiceExtension service = new JsapiServiceExtension.Builder().config(rsaAutoCertificateConfig).build();

        QueryOrderByOutTradeNoRequest queryRequest = new QueryOrderByOutTradeNoRequest();
        queryRequest.setMchid(wxPayV3Properties.getMerchantId());
        queryRequest.setOutTradeNo(orderNo);

        try {
            Transaction transaction = service.queryOrderByOutTradeNo(queryRequest);
            log.info(JSON.toJSONString(transaction));
            if(null != transaction && transaction.getTradeState() == Transaction.TradeStateEnum.SUCCESS) {
                //更改订单状态
                this.handlePayment(transaction);
                return true;
            }
        } catch (ServiceException e) {
            // API返回失败, 例如ORDER_NOT_EXISTS
            System.out.printf("code=[%s], message=[%s]\n", e.getErrorCode(), e.getErrorMessage());
        }
        return false;
    }

    private void handlePayment(Transaction transaction) {

        //1.更新支付状态，状态修改为已支付
        //订单编号
        String outTradeNo = transaction.getOutTradeNo();
        LambdaQueryWrapper<PaymentInfo> wrapper=new LambdaQueryWrapper<>();
        wrapper.eq(PaymentInfo::getOrderNo,outTradeNo);
        PaymentInfo paymentInfo=paymentInfoMapper.selectOne(wrapper);
        //如果已经支付，不需要更新
        if(paymentInfo.getPaymentStatus()==1){
            return;
        }
        paymentInfo.setPaymentStatus(1);
        paymentInfo.setOrderNo(transaction.getOutTradeNo());
        paymentInfo.setTransactionId(transaction.getTransactionId());
        paymentInfo.setCallbackContent(JSON.toJSONString(transaction));
        paymentInfo.setCallbackTime(new Date());
        paymentInfoMapper.updateById(paymentInfo);


        //2 发送端：发送mq消息，传递 订单编号
        //  接收端：获取订单编号，完成后续处理
        rabbitService.sendMessage(MqConst.EXCHANGE_ORDER,MqConst.ROUTING_PAY_SUCCESS,outTradeNo);
    }

    //微信支付异步通知接口
    @Transactional
    @Override
    public void wxnotify(HttpServletRequest request) {
        //1.回调通知的验签与解密
        //从request头信息获取参数
        //HTTP 头 Wechatpay-Signature
        // HTTP 头 Wechatpay-Nonce
        //HTTP 头 Wechatpay-Timestamp
        //HTTP 头 Wechatpay-Serial
        //HTTP 头 Wechatpay-Signature-Type
        //HTTP 请求体 body。切记使用原始报文，不要用 JSON 对象序列化后的字符串，避免验签的 body 和原文不一致。

        String wechatPaySerial = request.getHeader("Wechatpay-Serial");
        String nonce = request.getHeader("Wechatpay-Nonce");
        String timestamp = request.getHeader("Wechatpay-Timestamp");
        String signature = request.getHeader("Wechatpay-Signature");
        String requestBody = RequestUtils.readData(request);
        //2.构造 RequestParam
        RequestParam requestParam = new RequestParam.Builder()
                .serialNumber(wechatPaySerial)
                .nonce(nonce)
                .signature(signature)
                .timestamp(timestamp)
                .body(requestBody)
                .build();

        //3.初始化 NotificationParser
        NotificationParser notificationParser = new NotificationParser(rsaAutoCertificateConfig);
        //4.以支付通知回调为例，验签、解密并转换成 Transaction
        Transaction transaction = notificationParser.parse(requestParam, Transaction.class);

        if(transaction!=null&&transaction.getTradeState()==Transaction.TradeStateEnum.SUCCESS){
            //5.处理支付业务
            this.handlePayment(transaction);
        }
    }

    //支付成功后，后续处理
    @GlobalTransactional
    @Override
    public void handleOrder(String orderNo) {

        //1.订单状态改变，改为已支付
        orderInfoFeignClient.updateOrderPayStatus(orderNo);


        //2.获取系统的奖励,转账
        OrderRewardVo orderRewardVo = orderInfoFeignClient.getOrderRewardFee(orderNo).getData();
        if(orderRewardVo!=null&&orderRewardVo.getRewardFee().doubleValue()>0){
            TransferForm transferForm=new TransferForm();
            transferForm.setAmount(orderRewardVo.getRewardFee());
            transferForm.setDriverId(orderRewardVo.getDriverId());
            transferForm.setContent(TradeType.REWARD.getContent());
            transferForm.setTradeNo(orderNo);
            transferForm.setTradeType(TradeType.REWARD.getType());
            driverAccountFeignClient.transfer(transferForm);

        }


        //TODO 分账其他



    }
}

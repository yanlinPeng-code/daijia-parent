package com.atguigu.daijia.customer.service.impl;

import cn.binarywang.wx.miniapp.api.WxMaService;
import cn.binarywang.wx.miniapp.bean.WxMaJscode2SessionResult;

import cn.binarywang.wx.miniapp.bean.WxMaPhoneNumberInfo;
import com.atguigu.daijia.common.execption.GuiguException;
import com.atguigu.daijia.common.result.ResultCodeEnum;
import com.atguigu.daijia.customer.mapper.CustomerInfoMapper;
import com.atguigu.daijia.customer.mapper.CustomerLoginLogMapper;
import com.atguigu.daijia.customer.service.CustomerInfoService;
import com.atguigu.daijia.model.entity.customer.CustomerInfo;
import com.atguigu.daijia.model.entity.customer.CustomerLoginLog;
import com.atguigu.daijia.model.form.customer.UpdateWxPhoneForm;
import com.atguigu.daijia.model.vo.customer.CustomerLoginVo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import me.chanjar.weixin.common.error.WxErrorException;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@SuppressWarnings({"unchecked", "rawtypes"})
public class CustomerInfoServiceImpl extends ServiceImpl<CustomerInfoMapper, CustomerInfo> implements CustomerInfoService {
    @Autowired
    private WxMaService wxMaService;


    @Autowired
    private CustomerInfoMapper customerInfoMapper;

    @Autowired
    private CustomerLoginLogMapper customerLoginLogMapper;


    // 微信小程序登录接口

    @Override
    public Long login(String code) {
        String openid=null;
        // 1.获取code值，使用微信工具包对象，获取微信的唯一标志符openid
        try {
            WxMaJscode2SessionResult sessionInfo = wxMaService.getUserService().getSessionInfo(code);
             openid=sessionInfo.getOpenid();
        } catch (WxErrorException e) {
            throw new RuntimeException(e);
        }

        //2.根据openid查询数据库表，判断是否是第一次登录。
          //如果openid不存在，返回空，如果openid存在，返回一条用户信息
        LambdaQueryWrapper<CustomerInfo> wrapper=new LambdaQueryWrapper<>();
        wrapper.eq(CustomerInfo::getWxOpenId,openid);
        CustomerInfo customerInfo = customerInfoMapper.selectOne(wrapper);
        //3.如果是第一次登录，添加信息到用户表。
        if(customerInfo==null){
            customerInfo=new CustomerInfo();
            customerInfo.setNickname(String.valueOf(System.currentTimeMillis()));
            customerInfo.setAvatarUrl("https://big-event-yanlin.oss-cn-beijing.aliyuncs.com/0199e7d6-6c2d-4a96-9290-98f553ed72b8.png");
            customerInfo.setWxOpenId(openid);
            customerInfoMapper.insert(customerInfo);

        }

        //4.记录用户的登录信息

        CustomerLoginLog customerLoginLog = new CustomerLoginLog();
        customerLoginLog.setCustomerId(customerInfo.getId());
        customerLoginLog.setMsg("小程序登录");
        customerLoginLogMapper.insert(customerLoginLog);
        //5.返回用户id
        return customerInfo.getId();

    }
   //获取客户登录信息
    @Override
    public CustomerLoginVo getCustomerInfo(Long customerId) {
        //1.根据用户id查询用户信息

        CustomerInfo customerInfo = customerInfoMapper.selectById(customerId);

        //2.把数据封装到CustomerLoginVo对象里

        CustomerLoginVo customerLoginVo = new CustomerLoginVo();
        BeanUtils.copyProperties(customerInfo,customerLoginVo);//属性拷贝

        //查看是否绑定手机号


        //@Schema(description = "是否绑定手机号码")
        //    private Boolean isBindPhone;
        String phone=customerInfo.getPhone();

        boolean hasPhone= StringUtils.hasText(phone);//判断手机号是否为空,返回的是true或者false
        customerLoginVo.setIsBindPhone(hasPhone);



        //3.返回CustomerLoginVo
        return customerLoginVo;
    }


    //更新客户微信手机号
    @Override
    public Boolean updateWxPhoneNumber(UpdateWxPhoneForm updateWxPhoneForm) {
       //根据code获取微信绑定的手机号

        try {
            WxMaPhoneNumberInfo phoneNoInfo = wxMaService.getUserService().getPhoneNoInfo(updateWxPhoneForm.getCode());
            String phoneNumber = phoneNoInfo.getPhoneNumber();

            //更新客户信息

            Long customerId = updateWxPhoneForm.getCustomerId();
            CustomerInfo customerInfo = customerInfoMapper.selectById(customerId);
            customerInfo.setPhone(phoneNumber);
            customerInfoMapper.updateById(customerInfo);
            return true;
        } catch (WxErrorException e) {
            throw  new  GuiguException(ResultCodeEnum.DATA_ERROR);
        }
    }


    //获取客户OpenId
    @Override
    public String getCustomerOpenId(Long customerId) {
        LambdaQueryWrapper<CustomerInfo> wrapper=new LambdaQueryWrapper<>();
        wrapper.eq(CustomerInfo::getId,customerId);
        CustomerInfo customerInfo = customerInfoMapper.selectOne(wrapper);
        return customerInfo.getWxOpenId();

    }
}

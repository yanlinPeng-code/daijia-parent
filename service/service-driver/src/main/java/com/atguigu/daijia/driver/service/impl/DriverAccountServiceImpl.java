package com.atguigu.daijia.driver.service.impl;

import com.atguigu.daijia.driver.mapper.DriverAccountDetailMapper;
import com.atguigu.daijia.driver.mapper.DriverAccountMapper;
import com.atguigu.daijia.driver.service.DriverAccountService;
import com.atguigu.daijia.model.entity.driver.DriverAccount;
import com.atguigu.daijia.model.entity.driver.DriverAccountDetail;
import com.atguigu.daijia.model.form.driver.TransferForm;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@SuppressWarnings({"unchecked", "rawtypes"})
public class DriverAccountServiceImpl extends ServiceImpl<DriverAccountMapper, DriverAccount> implements DriverAccountService {

    @Autowired
    private DriverAccountMapper driverAccountMapper;

    @Autowired
    private DriverAccountDetailMapper driverAccountDetailMapper;
    //转账
    @Override
    public Boolean transfer(TransferForm transferForm) {

        //去重
        LambdaQueryWrapper<DriverAccountDetail> wrapper=new LambdaQueryWrapper<>();


        wrapper.eq(DriverAccountDetail::getTradeNo,transferForm.getTradeNo());
        Long l = driverAccountDetailMapper.selectCount(wrapper);
        if(l>0){
            return true;
        }

        //2 添加奖励到司机账户表
        driverAccountMapper.add(transferForm.getDriverId(),transferForm.getAmount());

        //3 添加交易记录
        DriverAccountDetail d=new DriverAccountDetail();
        BeanUtils.copyProperties(transferForm,d);
        driverAccountDetailMapper.insert(d);

        return true;

    }
}

package com.atguigu.daijia.driver.service;

import com.atguigu.daijia.common.result.Result;
import com.atguigu.daijia.model.entity.driver.DriverInfo;
import com.atguigu.daijia.model.entity.driver.DriverSet;
import com.atguigu.daijia.model.form.driver.DriverFaceModelForm;
import com.atguigu.daijia.model.form.driver.UpdateDriverAuthInfoForm;
import com.atguigu.daijia.model.vo.driver.DriverAuthInfoVo;
import com.atguigu.daijia.model.vo.driver.DriverInfoVo;
import com.atguigu.daijia.model.vo.driver.DriverLoginVo;
import com.baomidou.mybatisplus.extension.service.IService;

public interface DriverInfoService extends IService<DriverInfo> {

    Long login(String code);

    DriverLoginVo getDriverLoginInfo(Long driverId);

    DriverAuthInfoVo getDriverAuthInfo(Long driverId);

    //更新司机认证信息操作
    Boolean updateDriverAuthInfo(UpdateDriverAuthInfoForm updateDriverAuthInfoForm);

    //创建司机人脸模型
    Boolean createDriverFaceModel(DriverFaceModelForm driverFaceModelForm);

    //获取司机设置信息
    DriverSet getDriverSet(Long driverId);

    //判断司机当日是否进行过人脸识别
    Boolean isFaceRecognition(Long driverId);

    //验证司机人脸
    Boolean verifyDriverFace(DriverFaceModelForm driverFaceModelForm);

    //更新接单状态
    Boolean updateServiceStatus(Long driverId, Integer status);

    //获取司机基本信息
    DriverInfoVo getDriverBaseInfo(Long driverId);

    //获取司机OpenId
    String getDriverOpenId(Long driverId);
}

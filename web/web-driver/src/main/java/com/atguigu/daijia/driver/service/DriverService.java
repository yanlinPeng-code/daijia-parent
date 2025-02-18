package com.atguigu.daijia.driver.service;

import com.atguigu.daijia.model.form.driver.DriverFaceModelForm;
import com.atguigu.daijia.model.form.driver.UpdateDriverAuthInfoForm;
import com.atguigu.daijia.model.vo.driver.DriverAuthInfoVo;
import com.atguigu.daijia.model.vo.driver.DriverLoginVo;

public interface DriverService {


    String login(String code);

    DriverLoginVo getDriverLoginInfo(Long driverId);

    DriverAuthInfoVo getDriverAuthInfo(Long driverId);

    Boolean updateDriverAuthInfo(UpdateDriverAuthInfoForm updateDriverAuthInfoForm);

    Boolean creatDriverFaceModel(DriverFaceModelForm driverFaceModelForm);

    //"判断司机当日是否进行过人脸识别"
    Boolean isFaceRecognition(Long driverId);

    //验证司机人脸
    Boolean verifyDriverFace(DriverFaceModelForm driverFaceModelForm);

    //开始接单服务
    Boolean startService(Long driverId);

    //停止接单服务
    Boolean stopService(Long driverId);
}

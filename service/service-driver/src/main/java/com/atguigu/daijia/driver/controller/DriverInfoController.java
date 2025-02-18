package com.atguigu.daijia.driver.controller;

import com.atguigu.daijia.common.result.Result;
import com.atguigu.daijia.driver.service.DriverInfoService;
import com.atguigu.daijia.model.entity.driver.DriverInfo;
import com.atguigu.daijia.model.entity.driver.DriverSet;
import com.atguigu.daijia.model.form.driver.DriverFaceModelForm;
import com.atguigu.daijia.model.form.driver.UpdateDriverAuthInfoForm;
import com.atguigu.daijia.model.vo.driver.DriverAuthInfoVo;
import com.atguigu.daijia.model.vo.driver.DriverInfoVo;
import com.atguigu.daijia.model.vo.driver.DriverLoginVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Slf4j
@Tag(name = "司机API接口管理")
@RestController
@RequestMapping(value="/driver/info")
@SuppressWarnings({"unchecked", "rawtypes"})
public class DriverInfoController {
    @Autowired
   private DriverInfoService driverInfoService;

    @Operation(summary = "小程序授权登录")
    @GetMapping("/login/{code}")
    public Result<Long> login(@PathVariable String code) {
        return Result.ok(driverInfoService.login(code));
    }



    @Operation(summary = "获取司机登录信息")
    @GetMapping("/getDriverLoginInfo/{driverId}")
    public Result<DriverLoginVo> getDriverInfo(@PathVariable Long driverId){
        return Result.ok(driverInfoService.getDriverLoginInfo(driverId));
    }
    @Operation(summary = "获取司机的认证信息")
    @GetMapping("/getDriverAuthInfo/{driverId}")
    public Result<DriverAuthInfoVo> driverAuthInfoVoR(@PathVariable Long driverId){
       DriverAuthInfoVo driverAuthInfoVo=driverInfoService.getDriverAuthInfo(driverId);
        return Result.ok(driverAuthInfoVo);
    }


    //更新司机认证信息操作
    @Operation(summary = "更新司机认证信息")
    @PostMapping("/updateDriverAuthInfo")
    public Result<Boolean> UpdateDriverAuthInfo(@RequestBody UpdateDriverAuthInfoForm updateDriverAuthInfoForm){

        return Result.ok(driverInfoService.updateDriverAuthInfo(updateDriverAuthInfoForm));
    }

    //创建司机的人脸识别

    @Operation(summary = "创建司机人脸模型")
    @PostMapping("/creatDriverFaceModel")
    Result<Boolean> createDriverFaceModel(@RequestBody DriverFaceModelForm driverFaceModelForm){

        return Result.ok(driverInfoService.createDriverFaceModel(driverFaceModelForm));
    }

    //获取司机设置信息
    @Operation(summary = "获取司机设置信息")
    @GetMapping("/getDriverSet/{driverId}")
    public Result<DriverSet> getDriverSet(@PathVariable Long driverId) {
        return Result.ok(driverInfoService.getDriverSet(driverId));
    }


    //判断司机当日是否进行人脸识别
    @Operation(summary = "判断司机当日是否进行人脸识别")
    @GetMapping("/isFaceRecognition/{driverId}")
    Result<Boolean> isFaceRecognition(@PathVariable("driverId") Long driverId){
        return Result.ok(driverInfoService.isFaceRecognition(driverId));
    }
    //验证司机人脸
    @Operation(summary = "验证司机人脸")
    @PostMapping("/verifyDriverFace")
    public Result<Boolean> verifyDriverFace(@RequestBody DriverFaceModelForm driverFaceModelForm){
        return Result.ok(driverInfoService.verifyDriverFace(driverFaceModelForm));
    }
    //更新司机的接单状态
    @Operation(summary = "更新接单状态")
    @GetMapping("/updateServiceStatus/{driverId}/{status}")
    Result <Boolean> updateServiceStatus(@PathVariable Long driverId,@PathVariable Integer status){
        return Result.ok(driverInfoService.updateServiceStatus(driverId,status));
    }

    @Operation(summary = "获取司机基本信息")
    @GetMapping("/getDriverInfo/{driverId}")
    public Result<DriverInfoVo> getDriverBaseInfo(@PathVariable Long driverId){
        return Result.ok(driverInfoService.getDriverBaseInfo(driverId));
    }

    @Operation(summary = "获取司机OpenId")
    @GetMapping("/getDriverOpenId/{driverId}")
    public Result<String> getDriverOpenId(@PathVariable Long driverId){
        return Result.ok(driverInfoService.getDriverOpenId(driverId));
    }

}


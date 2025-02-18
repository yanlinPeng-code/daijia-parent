package com.atguigu.daijia.driver.controller;

import com.atguigu.daijia.common.login.YanLogin;
import com.atguigu.daijia.common.result.Result;
import com.atguigu.daijia.common.util.AuthContextHolder;
import com.atguigu.daijia.driver.service.LocationService;
import com.atguigu.daijia.model.form.map.OrderServiceLocationForm;
import com.atguigu.daijia.model.form.map.UpdateDriverLocationForm;
import com.atguigu.daijia.model.form.map.UpdateOrderLocationForm;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@Tag(name = "位置API接口管理")
@RestController
@RequestMapping(value="/location")
@SuppressWarnings({"unchecked", "rawtypes"})
public class LocationController {

    @Autowired
    LocationService locationService;

    @Operation(summary = "开启接单服务：更新司机经纬度位置")
    @YanLogin
    @PostMapping("/updateDriverLocation")
    public Result<Boolean> updateDriverLocation(@RequestBody UpdateDriverLocationForm updateDriverLocationForm) {
        Long driverId = AuthContextHolder.getUserId();
        updateDriverLocationForm.setDriverId(driverId);
        return Result.ok(locationService.updateDriverLocation(updateDriverLocationForm));
    }

    @Operation(summary = "司机赶往代驾起始点：更新订单位置到Redis缓存")
    @YanLogin
    @PostMapping("/updateOrderLocationToCache")
    public Result<Boolean> updateOrderLocationToCache(@RequestBody UpdateOrderLocationForm updateOrderLocationForm){
        return Result.ok(locationService.updateOrderLocationToCache(updateOrderLocationForm));
    }
    //可以注释，老师没写

//    @Operation(summary = "关闭接单服务后，删除司机经纬度信息")
////    @YanLogin
//    @DeleteMapping("/removeDriverLocation/{driverId}")
//    public Result<Boolean>removeDriverLocation(@PathVariable("driverId") Long driverId){
//        return Result.ok(locationService.removeDriverLocation(driverId));
//    }

    @Operation(summary = "开始代驾服务：保存代驾服务订单位置")
    @PostMapping("/saveOrderServiceLocation")
    public Result<Boolean> saveOrderServiceLocation(@RequestBody List<OrderServiceLocationForm> orderServiceLocationFormList){

        return Result.ok(locationService.saveOrderServiceLocation(orderServiceLocationFormList));
    }

}


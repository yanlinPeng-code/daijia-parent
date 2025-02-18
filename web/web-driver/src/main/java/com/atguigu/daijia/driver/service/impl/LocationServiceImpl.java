package com.atguigu.daijia.driver.service.impl;

import com.atguigu.daijia.common.execption.GuiguException;
import com.atguigu.daijia.common.result.ResultCodeEnum;
import com.atguigu.daijia.driver.client.DriverInfoFeignClient;
import com.atguigu.daijia.driver.service.LocationService;
import com.atguigu.daijia.map.client.LocationFeignClient;
import com.atguigu.daijia.model.entity.driver.DriverSet;
import com.atguigu.daijia.model.form.map.OrderServiceLocationForm;
import com.atguigu.daijia.model.form.map.UpdateDriverLocationForm;
import com.atguigu.daijia.model.form.map.UpdateOrderLocationForm;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@SuppressWarnings({"unchecked", "rawtypes"})
public class LocationServiceImpl implements LocationService {
@Autowired
private LocationFeignClient locationFeignClient;

@Autowired
 private    DriverInfoFeignClient driverInfoFeignClient;

//开启接单服务：更新司机经纬度位置
    @Override
    public Boolean updateDriverLocation(UpdateDriverLocationForm updateDriverLocationForm) {
        //根据司机id获取司机个性化信息
        DriverSet driverSet = driverInfoFeignClient.getDriverSet(updateDriverLocationForm.getDriverId()).getData();
        //如果司机getServiceStatus()的值是1表示可以接单，否者不可以接单。可以接单了在更新司机的位置信息。
        if (driverSet.getServiceStatus().intValue() == 1) {
            return locationFeignClient.updateDriverLocation(updateDriverLocationForm).getData();
        }else {
            throw new GuiguException(ResultCodeEnum.NO_START_SERVICE);
        }
    }

    //司机赶往代驾起始点：更新订单位置到Redis缓存
    @Override
    public Boolean updateOrderLocationToCache(UpdateOrderLocationForm updateOrderLocationForm) {
        return locationFeignClient.updateOrderLocationToCache(updateOrderLocationForm).getData();
    }

//  //  关闭接单服务后，删除司机经纬度信息(老师没写，可以注释掉)
//    @Override
//    public Boolean removeDriverLocation(Long driverId) {
//        return locationFeignClient.removeDriverLocation(driverId).getData();
//    }

    @Override
    public Boolean saveOrderServiceLocation(List<OrderServiceLocationForm> orderLocationServiceFormList) {
        return locationFeignClient.saveOrderServiceLocation(orderLocationServiceFormList).getData();
    }

}

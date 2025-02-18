package com.atguigu.daijia.map.service.impl;

import com.atguigu.daijia.common.constant.RedisConstant;
import com.atguigu.daijia.common.constant.SystemConstant;
import com.atguigu.daijia.common.execption.GuiguException;
import com.atguigu.daijia.common.result.Result;
import com.atguigu.daijia.common.result.ResultCodeEnum;
import com.atguigu.daijia.common.util.LocationUtil;
import com.atguigu.daijia.driver.client.DriverInfoFeignClient;
import com.atguigu.daijia.map.repository.OrderServiceLocationRepository;
import com.atguigu.daijia.map.service.LocationService;
import com.atguigu.daijia.model.entity.driver.DriverSet;
import com.atguigu.daijia.model.entity.map.OrderServiceLocation;
import com.atguigu.daijia.model.form.map.OrderServiceLocationForm;
import com.atguigu.daijia.model.form.map.SearchNearByDriverForm;
import com.atguigu.daijia.model.form.map.UpdateDriverLocationForm;
import com.atguigu.daijia.model.form.map.UpdateOrderLocationForm;
import com.atguigu.daijia.model.vo.map.NearByDriverVo;
import com.atguigu.daijia.model.vo.map.OrderLocationVo;
import com.atguigu.daijia.model.vo.map.OrderServiceLastLocationVo;
import com.atguigu.daijia.order.client.OrderInfoFeignClient;
import io.micrometer.common.util.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.data.web.SpringDataWebProperties;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Sort;
import org.springframework.data.geo.*;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Slf4j
@Service
@SuppressWarnings({"unchecked", "rawtypes"})
public class LocationServiceImpl implements LocationService {

    @Autowired
    RedisTemplate redisTemplate;

    @Autowired
    DriverInfoFeignClient driverInfoFeignClient;

    @Autowired
    OrderServiceLocationRepository orderServiceLocationRepository;

    @Autowired
    MongoTemplate mongoTemplate;

    @Autowired
    OrderInfoFeignClient orderInfoFeignClient;

    /**
     *  Redis GEO 主要用于存储地理位置信息，并对存储的信息进行相关操作，该功能在 Redis 3.2 版本新增。
     *  后续用在，乘客下单后寻找5公里范围内开启接单服务的司机，通过Redis GEO进行计算
     */
    //开启接单服务后，更新司机经纬度信息
    @Override
    public Boolean updateDriverLocation(UpdateDriverLocationForm updateDriverLocationForm) {


        Point point = new Point(updateDriverLocationForm.getLongitude().doubleValue(), updateDriverLocationForm.getLatitude().doubleValue());
        redisTemplate.opsForGeo().add(RedisConstant.DRIVER_GEO_LOCATION, point, updateDriverLocationForm.getDriverId().toString());
        return true;

    }

    //移除司机经纬度信息
    @Override
    public Boolean removeDriverLocation(Long driverId) {
        redisTemplate.opsForGeo().remove(RedisConstant.DRIVER_GEO_LOCATION, driverId.toString());
        return true;
    }

    //搜索附近满足条件的司机
    @Override
    public List<NearByDriverVo> searchNearByDriver(SearchNearByDriverForm searchNearByDriverForm) {
        // 搜索经纬度位置5公里以内的司机
        //定义经纬度点
        Point point = new Point(searchNearByDriverForm.getLongitude().doubleValue(), searchNearByDriverForm.getLatitude().doubleValue());
        //定义距离：5公里(系统配置)
        Distance distance = new Distance(SystemConstant.NEARBY_DRIVER_RADIUS, RedisGeoCommands.DistanceUnit.KILOMETERS);
        //定义以point点为中心，distance为距离这么一个范围
        Circle circle = new Circle(point, distance);

        //定义GEO参数
        RedisGeoCommands.GeoRadiusCommandArgs args = RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs()
                .includeDistance() //包含距离
                .includeCoordinates() //包含坐标
                .sortAscending(); //排序：升序

        // 1.GEORADIUS获取附近范围内的信息
        GeoResults<RedisGeoCommands.GeoLocation<String>> result = this.redisTemplate.opsForGeo().radius(RedisConstant.DRIVER_GEO_LOCATION, circle, args);

        //2.收集信息，存入list
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> content = result.getContent();

        //3.返回计算后的信息
        List<NearByDriverVo> list = new ArrayList();
        if(!CollectionUtils.isEmpty(content)) {
            Iterator<GeoResult<RedisGeoCommands.GeoLocation<String>>> iterator = content.iterator();
            while (iterator.hasNext()) {
                GeoResult<RedisGeoCommands.GeoLocation<String>> item = iterator.next();

                //司机id
                Long driverId = Long.parseLong(item.getContent().getName());
                //当前距离
                BigDecimal currentDistance = new BigDecimal(item.getDistance().getValue()).setScale(2, RoundingMode.HALF_UP);
                log.info("司机：{}，距离：{}",driverId, item.getDistance().getValue());

                //获取司机接单设置参数
                DriverSet driverSet = driverInfoFeignClient.getDriverSet(driverId).getData();
                //接单里程判断，acceptDistance==0：不限制，
                if(driverSet.getAcceptDistance().doubleValue() != 0 && driverSet.getAcceptDistance().subtract(currentDistance).doubleValue() < 0) {
                    continue;
                }
                //订单里程判断，orderDistance==0：不限制
                if(driverSet.getOrderDistance().doubleValue() != 0 && driverSet.getOrderDistance().subtract(searchNearByDriverForm.getMileageDistance()).doubleValue() < 0) {
                    continue;
                }

                //满足条件的附近司机信息
                NearByDriverVo nearByDriverVo = new NearByDriverVo();
                nearByDriverVo.setDriverId(driverId);
                nearByDriverVo.setDistance(currentDistance);
                list.add(nearByDriverVo);
            }
        }
        return list;
    }

    //司机赶往代驾起始点：更新订单地址到缓存
    @Override
    public Boolean updateOrderLocationToCache(UpdateOrderLocationForm updateOrderLocationForm) {

        OrderLocationVo orderLocationVo = new OrderLocationVo();
        orderLocationVo.setLongitude(updateOrderLocationForm.getLongitude());
        orderLocationVo.setLatitude(updateOrderLocationForm.getLatitude());

        redisTemplate.opsForValue().set(RedisConstant.UPDATE_ORDER_LOCATION + updateOrderLocationForm.getOrderId(), orderLocationVo);
        return true;
    }

    @Override
    public OrderLocationVo getCacheOrderLocation(Long orderId) {
        OrderLocationVo orderLocationVo = (OrderLocationVo)redisTemplate.opsForValue().get(RedisConstant.UPDATE_ORDER_LOCATION + orderId);
        return orderLocationVo;
    }


    //开始代驾服务：保存代驾服务订单位置

//    @Override
//    public Boolean saveOrderServiceLocation(List<OrderServiceLocationForm> orderServiceLocationFormList) {
//
//        List<OrderServiceLocation> list=new ArrayList<>();
//        orderServiceLocationFormList.forEach(item ->{
//            OrderServiceLocation orderServiceLocation=new OrderServiceLocation();
//            BeanUtils.copyProperties(item,orderServiceLocation);
//            orderServiceLocation.setId(ObjectId.get().toString());
//            orderServiceLocation.setCreateTime(new Date());
//            list.add(orderServiceLocation);
//            //
//           // mongoTemplate.insert(orderServiceLocation);
//            //orderServiceLocationRepository.save(orderServiceLocation);
//        });
//
//
//        //批量添加到mongdb
//        //saveAll：可以 插入或更新 文档，具体取决于文档是否已有 ID。如果文档已存在，它会执行更新操作；如果没有，则执行插入操作。
//        //insertAll：仅执行 插入操作，不会更新已有文档。如果已有文档的 ID 与插入文档冲突，它会抛出 DuplicateKeyException 异常。
//        List<OrderServiceLocation> orderServiceLocations = orderServiceLocationRepository.saveAll(list);
//
//        int size = orderServiceLocations.size();
//        if(size<=0){
//            throw new GuiguException(ResultCodeEnum.SERVICE_ERROR);
//        }
//        // mongoTemplate.insertAll(list);
//
//        return true;
//    }

    @Override
    public Boolean saveOrderServiceLocation(List<OrderServiceLocationForm> orderLocationServiceFormList) {
        List<OrderServiceLocation> list = new ArrayList<>();
        orderLocationServiceFormList.forEach(item -> {
            OrderServiceLocation orderServiceLocation = new OrderServiceLocation();
            BeanUtils.copyProperties(item, orderServiceLocation);
            orderServiceLocation.setId(ObjectId.get().toString());
            orderServiceLocation.setCreateTime(new Date());
            list.add(orderServiceLocation);
            System.out.println("list是"+list);
        });
        orderServiceLocationRepository.saveAll(list);
        return true;
    }


    //"代驾服务：获取订单服务最后一个位置信息
    @Override
    public OrderServiceLastLocationVo getOrderServiceLastLocation(Long orderId) {
        System.out.println("orderId是"+orderId);

        //查询MongoDB
        //查询条件 ：orderId
        //根据创建时间降序排列
        //最新一条数据
        Query query = new Query();
        query.addCriteria(Criteria.where("orderId").is(orderId));
        query.with(Sort.by(Sort.Order.desc("createTime")));
        query.limit(1);

        OrderServiceLocation orderServiceLocation =
                mongoTemplate.findOne(query, OrderServiceLocation.class);
        System.out.println("888888************************* orderServerlation"+orderServiceLocation);
        OrderServiceLastLocationVo orderServiceLastLocationVo = new OrderServiceLastLocationVo();
        BeanUtils.copyProperties(orderServiceLocation,orderServiceLastLocationVo);
        return orderServiceLastLocationVo;
    }


    //代驾服务：计算订单实际里程
    @Override
    public BigDecimal calculateOrderRealDistance(Long orderId) {
        //根据订单id获取代驾订单位置信息，根据创建时间排序（升序），查询MongoDB

        //第一种方式
//
//        OrderServiceLocation orderServiceLocation = new OrderServiceLocation();
//        orderServiceLocation.setOrderId(orderId);
//        Example<OrderServiceLocation> orderServiceLocationExample = Example.of(orderServiceLocation);
//
//        Sort createTime = Sort.by(Sort.Direction.ASC, "createTime");
//        orderServiceLocationRepository.findAll(orderServiceLocationExample,createTime);


        //第二种方式
        //MongoRepository只需要 按照规则 在MongoRepository把查询方法创建出来就可以了
        // 总体规则：
        //1 查询方法名称 以 get  |  find  | read开头
        //2 后面查询字段名称，满足驼峰式命名，比如OrderId
        //3 字段查询条件添加关键字，比如Like  OrderBy   Asc
        // 具体编写 ： 根据订单id获取代驾订单位置信息，根据创建时间排序（升序）

        List<OrderServiceLocation> orderServiceLocationList=orderServiceLocationRepository.findByOrderIdOrderByCreateTimeAsc(orderId);

        //2.的到orderlcation的返回订单位置信息的list集合
        //把list集合进行遍历，得到每个位置信息，计算两个位置信息的距离
        //把所有计算的距离进行相加操作

        double realDistance=0;
        if(!CollectionUtils.isEmpty(orderServiceLocationList)){
            for (int i=0,size=orderServiceLocationList.size()-1;i<size;i++){
                OrderServiceLocation location1 = orderServiceLocationList.get(i);
                OrderServiceLocation location2 = orderServiceLocationList.get(i + 1);
                double distance = LocationUtil.getDistance(location1.getLatitude().doubleValue(), location1.getLongitude().doubleValue(), location2.getLatitude().doubleValue(), location2.getLongitude().doubleValue());

                realDistance=distance+realDistance;

            }
        }
        //测试过程中，没有真正代驾，实际代驾GPS位置没有变化，模拟：实际代驾里程 = 预期里程 + 5
        if(realDistance==0){
            return orderInfoFeignClient.getOrderInfo(orderId).getData().getExpectDistance().add(new BigDecimal("5"));
        }
        return new BigDecimal(realDistance);
    }
}

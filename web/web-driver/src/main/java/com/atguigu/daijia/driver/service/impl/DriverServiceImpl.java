package com.atguigu.daijia.driver.service.impl;

import com.atguigu.daijia.common.constant.RedisConstant;
import com.atguigu.daijia.common.execption.GuiguException;
import com.atguigu.daijia.common.result.Result;
import com.atguigu.daijia.common.result.ResultCodeEnum;
import com.atguigu.daijia.dispatch.client.NewOrderFeignClient;
import com.atguigu.daijia.driver.client.DriverInfoFeignClient;
import com.atguigu.daijia.driver.service.DriverService;
import com.atguigu.daijia.map.client.LocationFeignClient;
import com.atguigu.daijia.model.form.driver.DriverFaceModelForm;
import com.atguigu.daijia.model.form.driver.UpdateDriverAuthInfoForm;
import com.atguigu.daijia.model.vo.customer.CustomerLoginVo;
import com.atguigu.daijia.model.vo.driver.DriverAuthInfoVo;
import com.atguigu.daijia.model.vo.driver.DriverLoginVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@SuppressWarnings({"unchecked", "rawtypes"})
public class DriverServiceImpl implements DriverService {

    @Autowired
   private DriverInfoFeignClient driverInfoFeignClient;

    @Autowired
    private RedisTemplate redisTemplate;


    @Autowired
    private LocationFeignClient locationFeignClient;

    @Autowired
    private NewOrderFeignClient newOrderFeignClient;
    @Override
    public String login(String code) {
        //1.拿着code进行远程调用，返回用户id
        Result<Long> loginResult =driverInfoFeignClient.login(code);

        //2 判断，如果返回失败了，返回错误提示
        Integer codeResult = loginResult.getCode();
        if(codeResult!=200){
            throw new GuiguException(ResultCodeEnum.DATA_ERROR);
        }
        //3.获取远程调用返回用户id
        Long driverId = loginResult.getData();
        //4 判断返回用户id是否为空，如果为空，返回错误提示
        if(driverId==null){
            throw new GuiguException(ResultCodeEnum.DATA_ERROR);
        }

        //5.生成token，返回
        String token= UUID.randomUUID().toString().replaceAll("-", "");

        //6.把用户id和token存入redis,设置过期时间
        // key:token value:driverId

        //redisTemplate.opsForValue().set(token,customerId.toString(),30, TimeUnit.MINUTES);
        redisTemplate.opsForValue().set(RedisConstant.USER_LOGIN_KEY_PREFIX+token,
                driverId.toString(),
                RedisConstant.USER_LOGIN_KEY_TIMEOUT,
                TimeUnit.SECONDS);
        //7.返回token

        return token;

    }

    @Override
    public DriverLoginVo getDriverLoginInfo(Long driverId) {
        Result<DriverLoginVo> driverLoginVoResult = driverInfoFeignClient.getDriverInfo(driverId);
        Integer code = driverLoginVoResult.getCode();
        if(code!=200){
            throw new GuiguException(ResultCodeEnum.DATA_ERROR);
        }
        DriverLoginVo driverLoginVo=driverLoginVoResult.getData();
//        CustomerLoginVo customerLoginVo = customerLoginVoResult.getData();
        if(driverLoginVo==null){
            throw new GuiguException(ResultCodeEnum.DATA_ERROR);
        }
        //5.返回用户信息
        return driverLoginVo;
    }

    //司机认证信息
    @Override
    public DriverAuthInfoVo getDriverAuthInfo(Long driverId) {
        Result<DriverAuthInfoVo> driverAuthInfo = driverInfoFeignClient.getDriverAuthInfo(driverId);
        Integer code=driverAuthInfo.getCode();
        if(code!=200){
            throw new GuiguException(ResultCodeEnum.DATA_ERROR);
        }
        DriverAuthInfoVo driverAuthInfoVo=driverAuthInfo.getData();
        if(driverAuthInfoVo==null){
            throw new GuiguException(ResultCodeEnum.DATA_ERROR);
        }
        return driverAuthInfoVo;
    }

    //更新认证信息
    @Override
    public Boolean updateDriverAuthInfo(UpdateDriverAuthInfoForm updateDriverAuthInfoForm) {
        Result<Boolean> booleanResult = driverInfoFeignClient.UpdateDriverAuthInfo(updateDriverAuthInfoForm);

        Integer code = booleanResult.getCode();
        if (code != 200) {
            throw new GuiguException(ResultCodeEnum.DATA_ERROR);
        }

        return booleanResult.getData();
    }

    //创建司机人脸识别模型
    @Override
    public Boolean creatDriverFaceModel(DriverFaceModelForm driverFaceModelForm) {
        Result<Boolean> booleanResult = driverInfoFeignClient.creatDriverFaceModel(driverFaceModelForm);
        Integer code = booleanResult.getCode();
        if (code != 200) {
            throw new GuiguException(ResultCodeEnum.DATA_ERROR);
        }

        return booleanResult.getData();
    }

    //判断司机当日是否进行过人脸识别
    @Override
    public Boolean isFaceRecognition(Long driverId) {
        return driverInfoFeignClient.isFaceRecognition(driverId).getData();
    }

    @Override
    public Boolean verifyDriverFace(DriverFaceModelForm driverFaceModelForm) {
        return driverInfoFeignClient.verifyDriverFace(driverFaceModelForm).getData();
    }

    @Override
    public Boolean startService(Long driverId) {
        //判断认证状态
        DriverLoginVo driverLoginVo=driverInfoFeignClient.getDriverInfo(driverId).getData();
        if(driverLoginVo.getAuthStatus().intValue()!=2){
            throw new GuiguException(ResultCodeEnum.AUTH_ERROR);
        }
    //判断当日是否人脸识别
        Boolean isFaceRecognition = driverInfoFeignClient.isFaceRecognition(driverId).getData();
        if(!isFaceRecognition){
            throw new GuiguException(ResultCodeEnum.FACE_ERROR);
        }

        //更新司机接单状态,1开始接单
        driverInfoFeignClient.updateServiceStatus(driverId,1);


        //删除司机位置信息redis
        locationFeignClient.removeDriverLocation(driverId);


        //清空司机新订单队列
        newOrderFeignClient.clearNewOrderQueueData(driverId);
        return true;

    }

    //停止接单服务
    @Override
    public Boolean stopService(Long driverId) {
        //更新司机接单信息0
        driverInfoFeignClient.updateServiceStatus(driverId,0);

        //删除司机位置信息
        locationFeignClient.removeDriverLocation(driverId);


        //清空司机新订单队列
        newOrderFeignClient.clearNewOrderQueueData(driverId);
        return true;
    }

}

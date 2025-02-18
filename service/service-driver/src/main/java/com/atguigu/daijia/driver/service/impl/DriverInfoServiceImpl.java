package com.atguigu.daijia.driver.service.impl;

import cn.binarywang.wx.miniapp.api.WxMaService;
import cn.binarywang.wx.miniapp.bean.WxMaJscode2SessionResult;
import com.atguigu.daijia.common.constant.SystemConstant;
import com.atguigu.daijia.common.execption.GuiguException;
import com.atguigu.daijia.common.result.ResultCodeEnum;
import com.atguigu.daijia.driver.config.TencentCloudProperties;
import com.atguigu.daijia.driver.mapper.*;
import com.atguigu.daijia.driver.service.CosService;
import com.atguigu.daijia.driver.service.DriverInfoService;
import com.atguigu.daijia.model.entity.driver.*;
import com.atguigu.daijia.model.form.driver.DriverFaceModelForm;
import com.atguigu.daijia.model.form.driver.UpdateDriverAuthInfoForm;
import com.atguigu.daijia.model.vo.driver.DriverAuthInfoVo;
import com.atguigu.daijia.model.vo.driver.DriverInfoVo;
import com.atguigu.daijia.model.vo.driver.DriverLoginVo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tencentcloudapi.common.AbstractModel;
import com.tencentcloudapi.iai.v20180301.IaiClient;
import com.tencentcloudapi.iai.v20180301.models.*;
import lombok.extern.slf4j.Slf4j;
import me.chanjar.weixin.common.error.WxErrorException;
import org.joda.time.DateTime;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.exception.TencentCloudSDKException;
import com.tencentcloudapi.common.profile.ClientProfile;
import com.tencentcloudapi.common.profile.HttpProfile;
import java.math.BigDecimal;
import java.util.Date;

@Slf4j
@Service
@SuppressWarnings({"unchecked", "rawtypes"})
public class DriverInfoServiceImpl extends ServiceImpl<DriverInfoMapper, DriverInfo> implements DriverInfoService {

    @Autowired
    private WxMaService wxMaService;

    @Autowired
    private DriverInfoMapper driverInfoMapper;
    @Autowired
    private DriverSetMapper driverSetMapper;
    @Autowired
    private DriverAccountMapper driverAccountMapper;

    @Autowired
    private DriverLoginLogMapper driverLoginLogMapper;

    @Autowired
    private CosService cosService;

    @Autowired
    private TencentCloudProperties tencentCloudProperties;
    @Autowired
    private DriverFaceRecognitionMapper driverFaceRecognitionMapper;


    @Override
    public Long login(String code) {


        String openid = null;

        try {
            WxMaJscode2SessionResult sessionInfo = wxMaService.getUserService().getSessionInfo(code);
            openid = sessionInfo.getOpenid();


            //2.根据openid查询数据库表，判断是否是第一次登录。
            //如果openid不存在，返回空，如果openid存在，返回一条用户信息

            LambdaQueryWrapper<DriverInfo> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(DriverInfo::getWxOpenId, openid);
            DriverInfo driverInfo = driverInfoMapper.selectOne(wrapper);
            if (driverInfo == null) {

                //添加司机基本信息
                driverInfo = new DriverInfo();
                driverInfo.setNickname(String.valueOf(System.currentTimeMillis()));
                driverInfo.setWxOpenId(openid);
                driverInfo.setAvatarUrl("https://big-event-yanlin.oss-cn-beijing.aliyuncs.com/04b9421e-ccdd-45b8-8e90-b009f1089387.jpg");
                driverInfoMapper.insert(driverInfo);
                //初始化司机设置
                DriverSet driverSet = new DriverSet();
                driverSet.setDriverId(driverInfo.getId());
                driverSet.setOrderDistance(new BigDecimal(0));//无限制
                driverSet.setAcceptDistance(new BigDecimal(SystemConstant.ACCEPT_DISTANCE));//默认接单范围：5公里

                driverSet.setIsAutoAccept(0);//0:否，1：是
                driverSetMapper.insert(driverSet);

                //初始化司机账户信息
                DriverAccount driverAccount = new DriverAccount();
                driverAccount.setDriverId(driverInfo.getId());
                driverAccountMapper.insert(driverAccount);


            }
            DriverLoginLog driverLoginLog = new DriverLoginLog();
            driverLoginLog.setDriverId(driverInfo.getId());
            driverLoginLog.setMsg("小程序登录");
            driverLoginLogMapper.insert(driverLoginLog);


            //返回司机id;

            return driverInfo.getId();

        } catch (WxErrorException e) {
            throw new GuiguException(ResultCodeEnum.DATA_ERROR);
//        if(!StringUtils.hasText(openid)){
//            throw new GuiguException(ResultCodeEnum.DATA_ERROR);
//
//
//        }


        }


    }

    @Override
    public DriverLoginVo getDriverLoginInfo(Long driverId) {
        //1.根据用户id获取用户信息
        DriverInfo driverInfo = driverInfoMapper.selectById(driverId);
        //2.对象封装到DriverLoginVo
        DriverLoginVo driverLoginVo = new DriverLoginVo();
        BeanUtils.copyProperties(driverInfo, driverLoginVo);


        //判断是否建档人脸识别

        String faceModelId = driverInfo.getFaceModelId();
        boolean hasText = StringUtils.hasText(faceModelId);
        driverLoginVo.setIsArchiveFace(hasText);
        return driverLoginVo;
    }


    //获取司机认证信息
    @Override
    public DriverAuthInfoVo getDriverAuthInfo(Long driverId) {
        DriverInfo driverInfo = driverInfoMapper.selectById(driverId);
        DriverAuthInfoVo driverAuthInfoVo = new DriverAuthInfoVo();
        BeanUtils.copyProperties(driverInfo, driverAuthInfoVo);
        //身份证反面
        driverAuthInfoVo.setIdcardBackShowUrl(cosService.getImageUrl(driverAuthInfoVo.getIdcardBackUrl()));
        //身份证正面
        driverAuthInfoVo.setIdcardFrontShowUrl(cosService.getImageUrl(driverAuthInfoVo.getIdcardFrontUrl()));
        //手持身份证
        driverAuthInfoVo.setIdcardHandShowUrl(cosService.getImageUrl(driverAuthInfoVo.getIdcardHandUrl()));
        //驾照正面
        driverAuthInfoVo.setDriverLicenseFrontShowUrl(cosService.getImageUrl(driverAuthInfoVo.getDriverLicenseFrontUrl()));
        //驾照反面
        driverAuthInfoVo.setDriverLicenseBackShowUrl(cosService.getImageUrl(driverAuthInfoVo.getDriverLicenseBackUrl()));
        //手持驾照
        driverAuthInfoVo.setDriverLicenseHandShowUrl(cosService.getImageUrl(driverAuthInfoVo.getDriverLicenseHandUrl()));
        return driverAuthInfoVo;
    }

    //更新司机认证信息操作
    @Override
    public Boolean updateDriverAuthInfo(UpdateDriverAuthInfoForm updateDriverAuthInfoForm) {
        //获取到司机id
        Long driverId = updateDriverAuthInfoForm.getDriverId();


        //根据id查询进行数据更新

        DriverInfo driverInfo = new DriverInfo();
        driverInfo.setId(driverId);
        BeanUtils.copyProperties(updateDriverAuthInfoForm, driverInfo);


        int i = driverInfoMapper.updateById(driverInfo);
        if (i > 0) {
            return true;
        } else {
            return false;
        }


    }

    ////创建司机人脸模型
    @Override
    public Boolean createDriverFaceModel(DriverFaceModelForm driverFaceModelForm) {
        //根据司机id获取司机基本信息
        DriverInfo driverInfo = driverInfoMapper.selectById(driverFaceModelForm.getDriverId());
        try {


            // 实例化一个认证对象，入参需要传入腾讯云账户 SecretId 和 SecretKey，此处还需注意密钥对的保密
            Credential cred = new Credential(tencentCloudProperties.getSecretId(), tencentCloudProperties.getSecretKey());
            // 实例化一个http选项，可选的，没有特殊需求可以跳过
            HttpProfile httpProfile = new HttpProfile();
            httpProfile.setEndpoint("iai.tencentcloudapi.com");
            // 实例化一个client选项，可选的，没有特殊需求可以跳过
            ClientProfile clientProfile = new ClientProfile();
            clientProfile.setHttpProfile(httpProfile);
            // 实例化要请求产品的client对象,clientProfile是可选的
            IaiClient client = new IaiClient(cred, tencentCloudProperties.getRegion(), clientProfile);
            // 实例化一个请求对象,每个接口都会对应一个request对象
            CreatePersonRequest req = new CreatePersonRequest();
            req.setGroupId(tencentCloudProperties.getPersionGroupId());
            //设置相关的值
            req.setPersonId(String.valueOf(driverInfo.getId()));
            req.setGender(Long.parseLong(driverInfo.getGender()));
            req.setQualityControl(4L);
            req.setUniquePersonControl(4L);
            req.setPersonName(driverInfo.getName());
            req.setImage(driverFaceModelForm.getImageBase64());


            // 返回的resp是一个CreatePersonResponse的实例，与请求对象对应
            CreatePersonResponse resp = client.CreatePerson(req);
            // 输出json格式的字符串回包
            System.out.println(AbstractModel.toJsonString(resp));
            if (StringUtils.hasText(resp.getFaceId())) {
                driverInfo.setFaceModelId(resp.getFaceId());
                driverInfoMapper.updateById(driverInfo);
            }
        } catch (TencentCloudSDKException e) {
            System.out.println(e.toString());
            return false;
        }

        return true;
    }

    //获取司机设置信息
    @Override
    public DriverSet getDriverSet(Long driverId) {
        LambdaQueryWrapper<DriverSet> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(DriverSet::getDriverId, driverId);
        return driverSetMapper.selectOne(lambdaQueryWrapper);
    }

    @Override
    public Boolean isFaceRecognition(Long driverId) {
        LambdaQueryWrapper<DriverFaceRecognition> lambdaQueryWrapper = new LambdaQueryWrapper();
        lambdaQueryWrapper.eq(DriverFaceRecognition::getDriverId, driverId);
        lambdaQueryWrapper.eq(DriverFaceRecognition::getFaceDate, new DateTime().toString("yyyy-MM-dd"));
        Long count = driverFaceRecognitionMapper.selectCount(lambdaQueryWrapper);
        return count != 0;
    }

    /**
     * 人脸验证
     * 文档地址：
     * https://cloud.tencent.com/document/api/867/44983
     * https://console.cloud.tencent.com/api/explorer?Product=iai&Version=2020-03-03&Action=VerifyFace
     *
     * @param driverFaceModelForm
     * @return
     */
    @Override
    public Boolean verifyDriverFace(DriverFaceModelForm driverFaceModelForm) {

        try {


            // 实例化一个认证对象，入参需要传入腾讯云账户 SecretId 和 SecretKey，此处还需注意密钥对的保密
            Credential cred = new Credential(tencentCloudProperties.getSecretId(), tencentCloudProperties.getSecretKey());
            // 实例化一个http选项，可选的，没有特殊需求可以跳过
            HttpProfile httpProfile = new HttpProfile();
            httpProfile.setEndpoint("iai.tencentcloudapi.com");
            // 实例化一个client选项，可选的，没有特殊需求可以跳过
            ClientProfile clientProfile = new ClientProfile();
            clientProfile.setHttpProfile(httpProfile);
            // 实例化要请求产品的client对象,clientProfile是可选的
            IaiClient client = new IaiClient(cred, tencentCloudProperties.getRegion(), clientProfile);
            // 实例化一个请求对象,每个接口都会对应一个request对象
            VerifyFaceRequest req = new VerifyFaceRequest();
            req.setImage(driverFaceModelForm.getImageBase64());
            req.setPersonId(String.valueOf(driverFaceModelForm.getDriverId()));
            //返回的resp是一个VerifyFaceResponse的实例，与请求对象对应

            VerifyFaceResponse resp = client.VerifyFace(req);
            // 输出json格式的字符串回包
            System.out.println(VerifyFaceResponse.toJsonString(resp));
            if (resp.getIsMatch()) {
                //活体检查

                if (this.detectLiveFace(driverFaceModelForm.getImageBase64())) {
                    DriverFaceRecognition driverFaceRecognition = new DriverFaceRecognition();
                    driverFaceRecognition.setDriverId(driverFaceModelForm.getDriverId());
                    driverFaceRecognition.setFaceDate(new Date());


                    driverFaceRecognitionMapper.insert(driverFaceRecognition);
                    return true;
                }
                ;
            }

        } catch (TencentCloudSDKException e) {
            System.out.println(e.toString());
        }
        throw new GuiguException(ResultCodeEnum.FEIGN_FAIL);


    }

    //更新接单状态
    //update driver_set set status? where driver_id=?

    @Override
    public Boolean updateServiceStatus(Long driverId, Integer status) {

        LambdaQueryWrapper<DriverSet> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DriverSet::getDriverId, driverId);
        DriverSet driverSet = new DriverSet();
        driverSet.setServiceStatus(status);

        driverSetMapper.update(driverSet, wrapper);
         return true;

    }

    //获取司机基本信息
    @Override
    public DriverInfoVo getDriverBaseInfo(Long driverId) {

        //根据司机id获取基本信息
        DriverInfo driverInfo=this.getById(driverId);

        DriverInfoVo driverInfoVo=new DriverInfoVo();
        BeanUtils.copyProperties(driverInfo,driverInfoVo);

        //驾龄

        Integer driverLicenseAge = new DateTime().getYear() - new DateTime(driverInfo.getDriverLicenseIssueDate()).getYear() + 1;

        System.out.println("司机驾龄："+driverInfo.getDriverLicenseIssueDate());
        driverInfoVo.setDriverLicenseAge(driverLicenseAge);

        return driverInfoVo;

    }

    //获取司机OpenId
    @Override
    public String getDriverOpenId(Long driverId) {
        DriverInfo driverInfo = this.getOne(new LambdaQueryWrapper<DriverInfo>().eq(DriverInfo::getId, driverId).select(DriverInfo::getWxOpenId));
        return driverInfo.getWxOpenId();
    }


    //人脸活体检查
    private Boolean detectLiveFace(String imageBase64) {

        try {


            Credential cred = new Credential(tencentCloudProperties.getSecretId(), tencentCloudProperties.getSecretKey());
            // 实例化一个http选项，可选的，没有特殊需求可以跳过
            HttpProfile httpProfile = new HttpProfile();
            httpProfile.setEndpoint("iai.tencentcloudapi.com");
            // 实例化一个client选项，可选的，没有特殊需求可以跳过
            ClientProfile clientProfile = new ClientProfile();
            clientProfile.setHttpProfile(httpProfile);
            // 实例化要请求产品的client对象,clientProfile是可选的
            IaiClient client = new IaiClient(cred, tencentCloudProperties.getRegion(), clientProfile);

            DetectLiveFaceRequest req = new DetectLiveFaceRequest();
            req.setImage(imageBase64);

            DetectLiveFaceResponse resp = client.DetectLiveFace(req);

            System.out.println(DetectLiveFaceResponse.toJsonString(resp));
            if (resp.getIsLiveness()) {
                return true;
            }


        } catch (TencentCloudSDKException e) {
            System.out.println(e.toString());
        }
        return false;
    }
}


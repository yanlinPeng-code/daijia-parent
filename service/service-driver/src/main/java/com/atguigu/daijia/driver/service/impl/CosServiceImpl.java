package com.atguigu.daijia.driver.service.impl;

import com.atguigu.daijia.common.execption.GuiguException;
import com.atguigu.daijia.common.result.ResultCodeEnum;
import com.atguigu.daijia.driver.config.TencentCloudProperties;
import com.atguigu.daijia.driver.service.CiService;
import com.atguigu.daijia.driver.service.CosService;
import com.atguigu.daijia.model.vo.driver.CosUploadVo;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.http.HttpMethodName;
import com.qcloud.cos.http.HttpProtocol;
import com.qcloud.cos.model.*;
import com.qcloud.cos.region.Region;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.units.qual.C;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URL;
import java.util.Date;
import java.util.UUID;

@Slf4j
@Service
@SuppressWarnings({"unchecked", "rawtypes"})
public class CosServiceImpl implements CosService {

    @Autowired
    private TencentCloudProperties properties;

    @Autowired
    CiService ciService;

    public COSClient getCosClient(){
        // 1 初始化用户身份信息（secretId, secretKey）。
// SECRETID 和 SECRETKEY 请登录访问管理控制台 https://console.cloud.tencent.com/cam/capi 进行查看和管理
        String secretId = properties.getSecretId();//用户的 SecretId，建议使用子账号密钥，授权遵循最小权限指引，降低使用风险。子账号密钥获取可参见 https://cloud.tencent.com/document/product/598/37140
        String secretKey = properties.getSecretKey();//用户的 SecretKey，建议使用子账号密钥，授权遵循最小权限指引，降低使用风险。子账号密钥获取可参见 https://cloud.tencent.com/document/product/598/37140
        COSCredentials cred = new BasicCOSCredentials(secretId, secretKey);
// 2 设置 bucket 的地域, COS 地域的简称请参见 https://cloud.tencent.com/document/product/436/6224
// clientConfig 中包含了设置 region, https(默认 http), 超时, 代理等 set 方法, 使用可参见源码或者常见问题 Java SDK 部分。
        Region region = new Region(properties.getRegion());
        ClientConfig clientConfig = new ClientConfig(region);
// 这里建议设置使用 https 协议
// 从 5.6.54 版本开始，默认使用了 https
        clientConfig.setHttpProtocol(HttpProtocol.https);
// 3 生成 cos 客户端。
        COSClient cosClient = new COSClient(cred, clientConfig);
        return cosClient;
    }


    @Override
    public String getImageUrl(String path) {
        //获取到Cosclient对象

        COSClient cosClient=this.getCosClient();
        if(!StringUtils.hasText(path)){
            return " ";
        }
        //GeneratePresignedUrlRequest
        GeneratePresignedUrlRequest request=new GeneratePresignedUrlRequest(properties.getBucketPrivate(),path, HttpMethodName.GET);
        //设置临时URL有效期为15分钟
        Date date = new DateTime().plusMinutes(15).toDate();
        request.setExpiration(date);
        URL url = cosClient.generatePresignedUrl(request);
        cosClient.shutdown();
        return url.toString();
    }



    @Override
    public CosUploadVo upload(MultipartFile file, String path) {



        COSClient cosClient=this.getCosClient();

        //文件上传
        ObjectMetadata metadata=new ObjectMetadata();
        metadata.setContentLength(file.getSize());
        metadata.setContentEncoding("UTF-8");
        metadata.setContentType(file.getContentType());


        //向储存桶里保存文件

        String fileType=file.getOriginalFilename().substring(file.getOriginalFilename().lastIndexOf("."));//文件后缀名
        String UploadPath="/driver/"+path+"/"+ UUID.randomUUID().toString().replaceAll("-","")+fileType;
        // // 01.jpg
        // /driver/auth/0o98754.jpg
        PutObjectRequest putObjectRequest=null;

        try {
            //1.Buket名称
            //2.上传路径

            putObjectRequest=new PutObjectRequest(properties.getBucketPrivate(),UploadPath,file.getInputStream(),metadata);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        putObjectRequest.setStorageClass(StorageClass.Standard);
        PutObjectResult putObjectResult=cosClient.putObject(putObjectRequest);//上传文件

        cosClient.shutdown();
        //万象图片审核
        Boolean b = ciService.imageAuditing(UploadPath);
        if(!b){
            //删除违规的图片
            cosClient.deleteObject(properties.getBucketPrivate(),UploadPath);
            throw new GuiguException(ResultCodeEnum.IMAGE_AUDITION_FAIL);
        }

        //返回vo对象

        CosUploadVo cosUploadVo=new CosUploadVo();
        cosUploadVo.setUrl(UploadPath);
        //TODO 图片临时访问url，回显使用
       String imageUrl=this.getImageUrl(UploadPath);
       cosUploadVo.setShowUrl(imageUrl);
        return cosUploadVo;
    }


}

package com.atguigu.daijia.driver.service.impl;

import com.atguigu.daijia.common.execption.GuiguException;
import com.atguigu.daijia.common.result.Result;
import com.atguigu.daijia.common.result.ResultCodeEnum;
import com.atguigu.daijia.driver.client.CosFeignClient;
import com.atguigu.daijia.driver.config.MinioProperties;
import com.atguigu.daijia.driver.service.FileService;
import com.atguigu.daijia.model.vo.driver.CosUploadVo;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

@Slf4j
@Service
@SuppressWarnings({"unchecked", "rawtypes"})
public class FileServiceImpl implements FileService {
    @Autowired
    private CosFeignClient cosFeignClient;

    @Autowired
    MinioProperties minioProperties;
    //文件上传
    @Override
    public CosUploadVo uploadFile(MultipartFile file, String path) {
        //远程调用
        Result<CosUploadVo> uploadVoResult = cosFeignClient.upload(file, path);
        CosUploadVo cosUploadVo = uploadVoResult.getData();
        return cosUploadVo;
    }

    //Minio文件上传
    @Override
    public String upload(MultipartFile file) {
        try {


            //创建一个Minio客户端对象
            MinioClient minioClient = MinioClient.builder()
                    .endpoint(minioProperties.getEndpointUrl()
                    ).credentials(minioProperties.getAccessKey(), minioProperties.getSecreKey()).build();


            // 判断桶是否存在
            boolean b = minioClient.bucketExists(BucketExistsArgs.builder().bucket(minioProperties.getBucketName()).build());
            if (!b) {
                //创建一个buket桶
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(minioProperties.getBucketName()).build());
            } else {
                System.out.println("Bucket 'asiatrip' already exists.");
            }
            //设置存储对象
            String substring = file.getOriginalFilename().substring(file.getOriginalFilename().lastIndexOf("."));
            String filename = new SimpleDateFormat("yyyyMMdd").format(new Date()) + "/" + UUID.randomUUID().toString().replace("-", "") + "." + substring;

            PutObjectArgs putObjectArgs = PutObjectArgs.builder()
                    .bucket(minioProperties.getBucketName())
                    .stream(file.getInputStream(), file.getSize(), -1)
                    .object(filename)
                    .build();
            minioClient.putObject(putObjectArgs);

            return minioProperties.getEndpointUrl() + "/" + minioProperties.getBucketName() + "/" + filename;

        } catch (Exception e) {
            throw new GuiguException(ResultCodeEnum.FILE_ERROR);
        }
    }

}

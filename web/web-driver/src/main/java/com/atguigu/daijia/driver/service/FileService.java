package com.atguigu.daijia.driver.service;

import com.atguigu.daijia.model.vo.driver.CosUploadVo;
import org.springframework.web.multipart.MultipartFile;

public interface FileService {
    CosUploadVo uploadFile(MultipartFile file, String path);

    //Minio文件上传
    String upload(MultipartFile file);
}

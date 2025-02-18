package com.atguigu.daijia.driver.service;

import com.atguigu.daijia.model.vo.driver.DriverLicenseOcrVo;
import com.atguigu.daijia.model.vo.driver.IdCardOcrVo;
import org.springframework.web.multipart.MultipartFile;

public interface OcrService {

    //身份证认证
    IdCardOcrVo idCardOcr(MultipartFile file);

    DriverLicenseOcrVo driverLicenseOcrVo(MultipartFile file);
}

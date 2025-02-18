package com.atguigu.daijia.driver.service.impl;

import com.atguigu.daijia.common.execption.GuiguException;
import com.atguigu.daijia.common.result.Result;
import com.atguigu.daijia.common.result.ResultCodeEnum;
import com.atguigu.daijia.driver.client.OcrFeignClient;
import com.atguigu.daijia.driver.service.OcrService;
import com.atguigu.daijia.model.vo.driver.DriverLicenseOcrVo;
import com.atguigu.daijia.model.vo.driver.IdCardOcrVo;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.units.qual.A;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@SuppressWarnings({"unchecked", "rawtypes"})
public class OcrServiceImpl implements OcrService {


    @Autowired
    OcrFeignClient ocrFeignClient;



    //身份证上传
    @Override
    public IdCardOcrVo idCardOcr(MultipartFile file) {
        Result<IdCardOcrVo> idCardOcrVoResult = ocrFeignClient.idCardOcr(file);
        Integer code=idCardOcrVoResult.getCode();
        if (code!=200){
            throw new GuiguException(ResultCodeEnum.RECOGNIZE_FILE);
        }
        IdCardOcrVo idCardOcrVo=idCardOcrVoResult.getData();
        if (idCardOcrVo==null){
            throw new GuiguException(ResultCodeEnum.DATA_ERROR);

        }
        return idCardOcrVo;
    }

    //驾驶证上传
    @Override
    public DriverLicenseOcrVo driverLicenseOcr(MultipartFile file) {
        Result<DriverLicenseOcrVo> driverLicenseOcrVoResult = ocrFeignClient.driverLicenseOcr(file);
        Integer code=driverLicenseOcrVoResult.getCode();
        if (code!=200){
            throw new GuiguException(ResultCodeEnum.RECOGNIZE_FILE);
        }
        DriverLicenseOcrVo driverLicenseOcrVo=driverLicenseOcrVoResult.getData();
        if (driverLicenseOcrVo==null){
            throw new GuiguException(ResultCodeEnum.DATA_ERROR);

        }
        return driverLicenseOcrVo;
    }
}

package com.atguigu.daijia.driver.service;

import com.atguigu.daijia.model.form.order.OrderMonitorForm;
import org.springframework.web.multipart.MultipartFile;

public interface MonitorService {


    //上传录音
    Boolean upload(MultipartFile multipartFile, OrderMonitorForm orderMonitorForm);
}

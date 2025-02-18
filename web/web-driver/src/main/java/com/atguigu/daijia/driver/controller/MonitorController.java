package com.atguigu.daijia.driver.controller;

import com.atguigu.daijia.common.result.Result;
import com.atguigu.daijia.driver.service.MonitorService;
import com.atguigu.daijia.model.entity.order.OrderMonitor;
import com.atguigu.daijia.model.entity.order.OrderMonitorRecord;
import com.atguigu.daijia.model.form.order.OrderMonitorForm;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Tag(name = "监控接口管理")
@RestController
@RequestMapping(value="/monitor")
@SuppressWarnings({"unchecked", "rawtypes"})
public class MonitorController {

    @Autowired
    MonitorService monitorService;


    @Operation(summary = "上传录音")
    @PostMapping("/upload")
    public Result<Boolean> upload(@RequestParam MultipartFile multipartFile, OrderMonitorForm orderMonitorForm){
        return Result.ok(monitorService.upload(multipartFile, orderMonitorForm));
    }



}


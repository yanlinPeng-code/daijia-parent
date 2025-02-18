package com.atguigu.daijia.driver.service.impl;

import com.atguigu.daijia.driver.client.CiFeignClient;
import com.atguigu.daijia.driver.service.FileService;
import com.atguigu.daijia.driver.service.MonitorService;
import com.atguigu.daijia.model.entity.order.OrderMonitor;
import com.atguigu.daijia.model.entity.order.OrderMonitorRecord;
import com.atguigu.daijia.model.form.order.OrderMonitorForm;
import com.atguigu.daijia.model.vo.order.TextAuditingVo;
import com.atguigu.daijia.order.client.OrderMonitorFeignClient;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.units.qual.A;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@SuppressWarnings({"unchecked", "rawtypes"})
public class MonitorServiceImpl implements MonitorService {


    @Autowired
    FileService fileService;

    @Autowired
    OrderMonitorFeignClient orderMonitorFeignClient;

    @Autowired
    CiFeignClient ciFeignClient;

    @Override
    public Boolean upload(MultipartFile multipartFile, OrderMonitorForm orderMonitorForm) {

        //1.上传对话文件到minio
        String upload = fileService.upload(multipartFile);
        log.info("upload: {}", upload);

        //保存订单监控记录数
        OrderMonitorRecord orderMonitorRecord=new OrderMonitorRecord();
        orderMonitorRecord.setOrderId(orderMonitorForm.getOrderId());
        orderMonitorRecord.setContent(orderMonitorForm.getContent());
        orderMonitorRecord.setFileUrl(upload);


        //增加文本审核
        TextAuditingVo textAuditingVo = ciFeignClient.textAuditing(orderMonitorForm.getContent()).getData();
        orderMonitorRecord.setResult(textAuditingVo.getResult());
        orderMonitorRecord.setKeywords(textAuditingVo.getKeywords());

        //封装对象到OrderMonitorRecord，保存到mongoDb
        orderMonitorFeignClient.saveMonitorRecord(orderMonitorRecord);
         //更新订单监控统计
        OrderMonitor orderMonitor = orderMonitorFeignClient.getOrderMonitor(orderMonitorForm.getOrderId()).getData();
        int fileNum = orderMonitor.getFileNum() + 1;
        orderMonitor.setFileNum(fileNum);
        //审核结果: 0（审核正常），1 （判定为违规敏感文件），2（疑似敏感，建议人工复核）。
        if("3".equals(orderMonitorRecord.getResult())) {
            int auditNum = orderMonitor.getAuditNum() + 1;
            orderMonitor.setAuditNum(auditNum);
        }
        orderMonitorFeignClient.updateOrderMonitor(orderMonitor);

        return true;
    }
}

package com.atguigu.daijia.dispatch.xxl.job;

import com.xxl.job.core.handler.annotation.XxlJob;
import org.springframework.stereotype.Component;

@Component
public class DispatchJobHandler {
    @XxlJob("firstJob")
    public void testJob(){
        System.out.println("XXl.job项目集成测试");
    }
}

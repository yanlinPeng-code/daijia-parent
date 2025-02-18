package com.atguigu.daijia.order.testLock;


import com.atguigu.daijia.common.result.Result;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name="测试接口")
@RequestMapping("/order/test")
public class TestController {

@Autowired
    TestService testService;



@GetMapping("testLock")
public Result testLock(){
    testService.testLock();
    return Result.ok();
}
}

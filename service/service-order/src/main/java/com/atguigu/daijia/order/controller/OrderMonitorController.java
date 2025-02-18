package com.atguigu.daijia.order.controller;

import com.atguigu.daijia.common.result.Result;
import com.atguigu.daijia.model.entity.order.OrderMonitor;
import com.atguigu.daijia.model.entity.order.OrderMonitorRecord;
import com.atguigu.daijia.order.service.OrderMonitorService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;


//@Controller 与 @RestController 的区别
//视图解析：
//
//@Controller：返回视图名称，通常用于 Web 应用的 MVC 模式，视图会被渲染（如返回 HTML 页面）。
//@RestController：返回数据，通常用于 RESTful API 服务，数据通常是 JSON 或 XML 格式。
@RestController
@RequestMapping("/order/monitor")
@SuppressWarnings({"unchecked", "rawtypes"})

//@SuppressWarnings({"unchecked", "rawtypes"})
//public void someMethod() {
//    List list = new ArrayList();
//    list.add("Hello");
//    String item = (String) list.get(0);  // 不会显示 unchecked 和 rawtypes 的警告
//}
//总结：
//unchecked：用于抑制与泛型不安全的类型转换相关的警告。
//rawtypes：用于抑制使用原始类型（如 List）时的警告。
public class OrderMonitorController {

    @Autowired
    OrderMonitorService orderMonitorService;


    @Operation(summary = "保存订单监控记录数据")
    @PostMapping("/saveOrderMonitorRecord")
    public Result<Boolean> saveMonitorRecord(@RequestBody OrderMonitorRecord orderMonitorRecord){

        return Result.ok(orderMonitorService.saveOrderMonitorRecord(orderMonitorRecord));

    }

    @Operation(summary = "根据订单id获取订单监控信息")
    @GetMapping("/getOrderMonitor/{orderId}")
    public Result<OrderMonitor> getOrderMonitor(@PathVariable Long orderId){
        return Result.ok(orderMonitorService.getOrderMonitor(orderId));
    }
    @Operation(summary = "更新订单监控信息")
    @PostMapping("/updateOrderMonitor")
    public Result<Boolean> updateOrderMonitor(@RequestBody OrderMonitor orderMonitor){
        return Result.ok(orderMonitorService.updateOrderMonitor(orderMonitor));
    }
    @Operation(summary = "保存订单监监控数据")
    @PostMapping("/saveOrderMonitor")
    public Result<Long> saveOrderMonitor(@RequestBody OrderMonitor orderMonitor){
        return Result.ok(orderMonitorService.saveOrderMonitor(orderMonitor));
    }

}


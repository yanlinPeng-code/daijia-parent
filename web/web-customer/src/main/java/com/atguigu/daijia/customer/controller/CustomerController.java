package com.atguigu.daijia.customer.controller;

import com.atguigu.daijia.common.login.YanLogin;
import com.atguigu.daijia.common.result.Result;
import com.atguigu.daijia.common.util.AuthContextHolder;
import com.atguigu.daijia.customer.service.CustomerService;
import com.atguigu.daijia.model.form.customer.UpdateWxPhoneForm;
import com.atguigu.daijia.model.vo.customer.CustomerLoginVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Slf4j
@Tag(name = "客户API接口管理")
@RestController
@RequestMapping("/customer")
@SuppressWarnings({"unchecked", "rawtypes"})
public class CustomerController {
    @Autowired
    private CustomerService customerService;





    @Operation(summary = "小程序授权登录")
    @GetMapping("/login/{code}")
    public Result<String> wxLogin(@PathVariable String code) {
        return Result.ok(customerService.login(code));
    }


    @Operation(summary = "获取客户登录信息")
    @YanLogin
    @GetMapping("/getCustomerLoginInfo")
    public Result<CustomerLoginVo> getCustomerLoginInfo() {
        //1.ThreadLocal获取用户id

        Long customerId = AuthContextHolder.getUserId();
        //HttpServletRequest request
        //String token = request.getHeader("token");

        //2.调用service方法根据token获取用户信息
        //调用service
        CustomerLoginVo customerLoginVo = customerService.getCustomerInfo(customerId);

        return Result.ok(customerLoginVo);

    }
//    @Operation(summary = "获取客户登录信息")

//    @GetMapping("/getCustomerLoginInfo")
//    public Result<CustomerLoginVo> getCustomerLoginInfo(@RequestHeader(value = "token") String token) {
//      //1.从请求头里获取token字符串
//        //HttpServletRequest request
//        //String token = request.getHeader("token");
//
//        //2.调用service方法根据token获取用户信息
//        //调用service
//        CustomerLoginVo customerLoginVo = customerService.getCustomerLoginInfo(token);
//
//        return Result.ok(customerLoginVo);
//
//    }



    @Operation(summary = "更新用户微信手机号")
    @YanLogin
    @PostMapping("/updateWxPhone")
    public Result updateWxPhone(@RequestBody UpdateWxPhoneForm updateWxPhoneForm){

        updateWxPhoneForm.setCustomerId(AuthContextHolder.getUserId());
//        Boolean b = customerService.updateWxPhoneNumber(updateWxPhoneForm);
        return Result.ok(true);

    }

}


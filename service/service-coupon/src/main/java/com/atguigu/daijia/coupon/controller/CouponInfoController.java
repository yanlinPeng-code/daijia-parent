package com.atguigu.daijia.coupon.controller;

import com.atguigu.daijia.common.result.Result;
import com.atguigu.daijia.coupon.service.CouponInfoService;
import com.atguigu.daijia.model.entity.coupon.CouponInfo;
import com.atguigu.daijia.model.form.coupon.UseCouponForm;
import com.atguigu.daijia.model.vo.base.PageVo;
import com.atguigu.daijia.model.vo.coupon.AvailableCouponVo;
import com.atguigu.daijia.model.vo.coupon.NoReceiveCouponVo;
import com.atguigu.daijia.model.vo.coupon.NoUseCouponVo;
import com.atguigu.daijia.model.vo.coupon.UsedCouponVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;


@Tag(name = "优惠券活动接口管理")
@RestController
@RequestMapping(value="/coupon/info")
@SuppressWarnings({"unchecked", "rawtypes"})
public class CouponInfoController {

    @Autowired
    CouponInfoService couponInfoService;


    //1. INNER JOIN (内连接)
    //作用：返回两个表中 匹配 的记录。
    //行为：只会返回两个表中符合连接条件的行，如果一方表没有匹配的记录，那么这一行不会出现在结果集中。
    //典型用途：用于查询两个表中有关联的记录。
    //结果：仅包括在两个表中都有的匹配数据。

    //2. LEFT JOIN (左连接)
    //作用：返回 左表 中的所有记录，以及与右表中匹配的记录。如果右表中没有匹配的记录，则右表的所有列都将返回 NULL。
    //行为：无论右表中是否有匹配的记录，左表的所有记录都会被返回。如果右表没有匹配项，则相应列将为 NULL。
    //典型用途：用于保留左表的所有记录，并获取与右表匹配的记录（如果有）。
    //结果：返回左表的所有记录，右表中没有匹配的部分会显示 NULL。
    //区别总结：
    //INNER JOIN：只返回两个表中匹配的记录。
    //LEFT JOIN：返回左表的所有记录，即使右表没有匹配的记录，右表的字段将返回 NULL

    /***
     left join （左连接）：返回包括左表中的所有记录和右表中连接字段相等的记录。
     　　right join （右连接）：返回包括右表中的所有记录和左表中连接字段相等的记录。
     　　inner join （等值连接或者叫内连接）：只返回两个表中连接字段相等的行。
     　　full join （全外连接）：返回左右表中所有的记录和左右表中连接字段相等的记录。
     ***/
    @Operation(summary = "查询未领取优惠券分页列表")
    @GetMapping("findNoReceivePage/{customerId}/{page}/{limit}")
    public Result<PageVo<NoReceiveCouponVo>> findNoReceivePage(@Parameter(name = "customerId", description = "乘客id", required = true)
                                                               @PathVariable Long customerId,
                                                               @Parameter(name = "page", description = "当前页码", required = true)
                                                               @PathVariable Long page,
                                                               @Parameter(name = "limit", description = "每页记录数", required = true)
                                                               @PathVariable Long limit) {
        Page<CouponInfo> param = new Page<>(page, limit);
        PageVo<NoReceiveCouponVo> pageVo = couponInfoService.findNoReceivePage(param, customerId);
        pageVo.setPage(page);
        pageVo.setLimit(limit);
        return Result.ok(pageVo);
    }


    @Operation(summary = "查询未使用优惠券分页列表")
    @GetMapping("findNoUsePage/{customerId}/{page}/{limit}")
    public Result<PageVo<NoUseCouponVo>> findNoUsePage(
            @Parameter(name = "customerId", description = "乘客id", required = true)
            @PathVariable Long customerId,

            @Parameter(name = "page", description = "当前页码", required = true)
            @PathVariable Long page,

            @Parameter(name = "limit", description = "每页记录数", required = true)
            @PathVariable Long limit
    ) {
        Page<CouponInfo> param = new Page<>(page, limit);
        PageVo<NoUseCouponVo> pageVo = couponInfoService.findNoUsePage(param, customerId);
        pageVo.setLimit(limit);
        pageVo.setPage(page);
        return Result.ok(pageVo);
    }

    @Operation(summary = "查询已使用优惠券分页列表")
    @GetMapping("findUsedPage/{customerId}/{page}/{limit}")
    public Result<PageVo<UsedCouponVo>> findUsedPage(
            @Parameter(name = "customerId", description = "乘客id", required = true)
            @PathVariable Long customerId,

            @Parameter(name = "page", description = "当前页码", required = true)
            @PathVariable Long page,

            @Parameter(name = "limit", description = "每页记录数", required = true)
            @PathVariable Long limit
    ){
        Page<CouponInfo> param=new Page<>(page,limit);
        PageVo<UsedCouponVo> pageVo=couponInfoService.findUsedPage(param,customerId);
        pageVo.setPage(page);
        pageVo.setLimit(limit);
        return Result.ok(pageVo);

    }

    @Operation(summary = "使用优惠券")
    @PostMapping("/useCoupon")
    public Result<BigDecimal> useCoupon(@RequestBody UseCouponForm useCouponForm){
        return Result.ok(couponInfoService.useCoupon(useCouponForm));
    }


    @Operation(summary = "领取优惠券")
    @GetMapping("/receive/{customerId}/{couponId}")
    public Result<Boolean> receive(@PathVariable Long customerId,@PathVariable Long couponId){
        return Result.ok(couponInfoService.receive(customerId, couponId));
    }

    @Operation(summary = "获取未使用的最佳优惠券信息")
    @GetMapping("/findAvailableCoupon/{customerId}/{orderAmount}")
    public Result<List<AvailableCouponVo>> findAvailableCoupon(@PathVariable Long customerId,@PathVariable BigDecimal orderAmount){
        return Result.ok(couponInfoService.findAvailableCoupon(customerId, orderAmount));

    }



}


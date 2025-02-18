package com.atguigu.daijia.order.mapper;

import com.atguigu.daijia.model.entity.order.OrderInfo;
import com.atguigu.daijia.model.vo.order.OrderListVo;
import com.atguigu.daijia.model.vo.order.OrderPayVo;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface OrderInfoMapper extends BaseMapper<OrderInfo> {

    //分页
    IPage<OrderListVo> selectCustomerOrderPage(Page<OrderInfo> pageParm, Long customerId);

    //////获取司机订单分页列表
    IPage<OrderListVo> selectDriverOrderPage(Page<OrderInfo> pageParam, Long driverId);


    //获取订单支付信息
    OrderPayVo selectOrderPayVo(@Param("orderNo") String orderNo, @Param("customerId") Long customerId);
}

package com.atguigu.daijia.order.service.impl;

import com.atguigu.daijia.model.entity.order.OrderMonitor;
import com.atguigu.daijia.model.entity.order.OrderMonitorRecord;
import com.atguigu.daijia.order.mapper.OrderMonitorMapper;
import com.atguigu.daijia.order.repository.OrderMonitorRecordRepository;
import com.atguigu.daijia.order.service.OrderMonitorService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@SuppressWarnings({"unchecked", "rawtypes"})
public class OrderMonitorServiceImpl extends ServiceImpl<OrderMonitorMapper, OrderMonitor> implements OrderMonitorService {


    @Autowired
    OrderMonitorRecordRepository monitorRecordRepository;

    @Autowired
    OrderMonitorMapper orderMonitorMapper;
    //保存订单监控记录数据"
    @Override
    public Boolean saveOrderMonitorRecord(OrderMonitorRecord orderMonitorRecord) {

        monitorRecordRepository.save(orderMonitorRecord);
        return true;

    }

    @Override
    public Boolean updateOrderMonitor(OrderMonitor orderMonitor) {
        return this.updateById(orderMonitor);
    }

    @Override
    public OrderMonitor getOrderMonitor(Long orderId) {
//        LambdaQueryWrapper<OrderMonitor> lambdaQueryWrapper=new LambdaQueryWrapper<>();
//        lambdaQueryWrapper.eq(OrderMonitor::getOrderId,orderId);
//        OrderMonitor orderMonitor = orderMonitorMapper.selectOne(lambdaQueryWrapper);
//        return orderMonitor;
        return this.getOne(new LambdaQueryWrapper<OrderMonitor>().eq(OrderMonitor::getOrderId, orderId));
    }

    @Override
    public Long saveOrderMonitor(OrderMonitor orderMonitor) {
        orderMonitorMapper.insert(orderMonitor);
        return orderMonitor.getId();
    }
}

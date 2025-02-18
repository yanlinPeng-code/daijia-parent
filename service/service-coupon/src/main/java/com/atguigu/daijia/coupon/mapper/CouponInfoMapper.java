package com.atguigu.daijia.coupon.mapper;

import com.atguigu.daijia.model.entity.coupon.CouponInfo;
import com.atguigu.daijia.model.vo.coupon.NoReceiveCouponVo;
import com.atguigu.daijia.model.vo.coupon.NoUseCouponVo;
import com.atguigu.daijia.model.vo.coupon.UsedCouponVo;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CouponInfoMapper extends BaseMapper<CouponInfo> {

    //查询未领取优惠券分页列表
    IPage<NoReceiveCouponVo> findNoReceivePage(Page<CouponInfo> param, @Param("customerId") Long customerId);

    //查询未使用优惠券分页列表
    IPage<NoUseCouponVo> findNoUsePage(Page<CouponInfo> param, @Param("customerId") Long customerId);

    //查询使用优惠券分页列表
    IPage<UsedCouponVo> findUsedPage(Page<CouponInfo> param, @Param("customerId") Long customerId);

    //做领取优惠卷操作
    int updateReceiveCount(Long couponId);

    //采用乐观锁方式，来进行原子操作解决并发问题
    int updateReceiveCountByLimit(Long couponId);

    //获取未使用的优惠卷信息
    List<NoUseCouponVo> findNoUseList(Long customerId);


    //使用优惠券
    int updateUseCount(Long id);
}

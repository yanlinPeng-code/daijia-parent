package com.atguigu.daijia.coupon.service.impl;

import com.atguigu.daijia.common.constant.RedisConstant;
import com.atguigu.daijia.common.execption.GuiguException;
import com.atguigu.daijia.common.result.ResultCodeEnum;
import com.atguigu.daijia.coupon.mapper.CouponInfoMapper;
import com.atguigu.daijia.coupon.mapper.CustomerCouponMapper;
import com.atguigu.daijia.coupon.service.CouponInfoService;
import com.atguigu.daijia.model.entity.coupon.CouponInfo;
import com.atguigu.daijia.model.entity.coupon.CustomerCoupon;
import com.atguigu.daijia.model.form.coupon.UseCouponForm;
import com.atguigu.daijia.model.vo.base.PageVo;
import com.atguigu.daijia.model.vo.coupon.AvailableCouponVo;
import com.atguigu.daijia.model.vo.coupon.NoReceiveCouponVo;
import com.atguigu.daijia.model.vo.coupon.NoUseCouponVo;
import com.atguigu.daijia.model.vo.coupon.UsedCouponVo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@SuppressWarnings({"unchecked", "rawtypes"})
public class CouponInfoServiceImpl extends ServiceImpl<CouponInfoMapper, CouponInfo> implements CouponInfoService {


    @Autowired
    CouponInfoMapper couponInfoMapper;

    @Autowired
    CustomerCouponMapper customerCouponMapper;


    @Autowired
    RedissonClient redissonClient;
    //查询未领取优惠券分页列表
    @Override
    public PageVo<NoReceiveCouponVo> findNoReceivePage(Page<CouponInfo> param, Long customerId) {
      IPage<NoReceiveCouponVo> couponInfoIPage= couponInfoMapper.findNoReceivePage(param,customerId);
        return new PageVo<>(couponInfoIPage.getRecords(),couponInfoIPage.getPages(),couponInfoIPage.getTotal());
    }

    //查询未使用优惠券分页列表
    @Override
    public PageVo<NoUseCouponVo> findNoUsePage(Page<CouponInfo> param, Long customerId) {
       IPage<NoUseCouponVo> couponVoIPage=couponInfoMapper.findNoUsePage(param,customerId);
       return new PageVo<>(couponVoIPage.getRecords(),couponVoIPage.getPages(),couponVoIPage.getTotal());
    }

    //查询已使用优惠券分页列表
    @Override
    public PageVo<UsedCouponVo> findUsedPage(Page<CouponInfo> param, Long customerId) {
       IPage<UsedCouponVo> couponVoIPage=couponInfoMapper.findUsedPage(param,customerId);
        return new PageVo<>(couponVoIPage.getRecords(),couponVoIPage.getPages(),couponVoIPage.getTotal());
    }


    //使用优惠券
    @Override
    public BigDecimal useCoupon(UseCouponForm useCouponForm) {
       //根据useCouponForm获取优惠卷信息
        CustomerCoupon customerCoupon = customerCouponMapper.selectById(useCouponForm.getCustomerCouponId());
        if(customerCoupon==null){
            throw new GuiguException(ResultCodeEnum.ARGUMENT_VALID_ERROR);
        }

        //获取优惠券信息
        CouponInfo couponInfo = couponInfoMapper.selectById(customerCoupon.getCouponId());
        if(couponInfo==null){
            throw new GuiguException(ResultCodeEnum.ARGUMENT_VALID_ERROR);
        }
        //判断该优惠券是否为乘客所有
        if(customerCoupon.getCustomerId().longValue()!=useCouponForm.getCustomerId().longValue()){
            throw new GuiguException(ResultCodeEnum.ILLEGAL_REQUEST);
        }
        //获取优惠券减免金额
        BigDecimal reduceAmount=null;
        //现金卷
        if(couponInfo.getCouponType().intValue()==1){
            //使用门槛判断
            //2.1.1.没门槛，订单金额必须大于优惠券减免金额
            if(couponInfo.getConditionAmount().doubleValue()==0&&useCouponForm.getOrderAmount().subtract(couponInfo.getAmount()).doubleValue()>0){
                //减免金额
                reduceAmount = couponInfo.getAmount();
            }
            //2.1.2.有门槛，订单金额大于优惠券门槛金额
            if(couponInfo.getConditionAmount().doubleValue()>0&&useCouponForm.getOrderAmount().subtract(couponInfo.getConditionAmount()).doubleValue()>0){
                //减免金额
                reduceAmount = couponInfo.getAmount();
            }

        }else {
            //使用门槛判断
            //订单折扣后金额
            BigDecimal discountOrderAmount=  useCouponForm.getOrderAmount().multiply(couponInfo.getDiscount()).divide(new BigDecimal("10")).setScale(2,RoundingMode.HALF_UP);
            //订单优惠金额
            //2.2.1.没门槛
            if(couponInfo.getConditionAmount().doubleValue()==0){
                //减免金额
              reduceAmount=  useCouponForm.getOrderAmount().subtract(discountOrderAmount);
            }

            //2.2.2.有门槛，订单折扣后金额大于优惠券门槛金额
            if (couponInfo.getConditionAmount().doubleValue()>0&&discountOrderAmount.subtract(couponInfo.getConditionAmount()).doubleValue()>0){
                //减免金额
               reduceAmount= useCouponForm.getOrderAmount().subtract(discountOrderAmount);
            }

            if(reduceAmount.doubleValue()>0){
               int rows= couponInfoMapper.updateUseCount(couponInfo.getId());
               if(rows==1){
                   CustomerCoupon coupon=new CustomerCoupon();
                   coupon.setId(customerCoupon.getId());
                   coupon.setUsedTime(new Date());
                   coupon.setOrderId(useCouponForm.getOrderId());
                   customerCouponMapper.updateById(coupon);
                   return reduceAmount;

               }
            }
            throw new GuiguException(ResultCodeEnum.DATA_ERROR);
        }



        return null;
    }

    //领取优惠券
    @Transactional(rollbackFor = Exception.class)
    @Override
    public Boolean receive(Long customerId, Long couponId) {
        //1、查询优惠券
        CouponInfo couponInfo = this.getById(couponId);
        if(null == couponInfo) {
            throw new GuiguException(ResultCodeEnum.DATA_ERROR);
        }

        //2、优惠券过期日期判断
        if (couponInfo.getExpireTime().before(new Date())) {
            throw new GuiguException(ResultCodeEnum.COUPON_EXPIRE);
        }

        //3、校验库存，优惠券领取数量判断
        if (couponInfo.getPublishCount() !=0 && couponInfo.getReceiveCount() >= couponInfo.getPublishCount()) {
            throw new GuiguException(ResultCodeEnum.COUPON_LESS);
        }

        RLock lock = null;
        try {
            // 初始化分布式锁
            //每人领取限制  与 优惠券发行总数 必须保证原子性，使用customerId减少锁的粒度，增加并发能力
            lock = redissonClient.getLock(RedisConstant.COUPON_LOCK + customerId);
            boolean flag = lock.tryLock(RedisConstant.COUPON_LOCK_WAIT_TIME, RedisConstant.COUPON_LOCK_LEASE_TIME, TimeUnit.SECONDS);
            if (flag) {
                //4、校验每人限领数量
                if (couponInfo.getPerLimit() > 0) {
                    //4.1、统计当前用户对当前优惠券的已经领取的数量
                    long count = customerCouponMapper.selectCount(new LambdaQueryWrapper<CustomerCoupon>().eq(CustomerCoupon::getCouponId, couponId).eq(CustomerCoupon::getCustomerId, customerId));
                    //4.2、校验限领数量
                    if (count >= couponInfo.getPerLimit()) {
                        throw new GuiguException(ResultCodeEnum.COUPON_USER_LIMIT);
                    }
                }

                //5、更新优惠券领取数量
                int row = 0;
                if (couponInfo.getPublishCount() == 0) {//没有限制
                    row = couponInfoMapper.updateReceiveCount(couponId);
                } else {
                    row = couponInfoMapper.updateReceiveCountByLimit(couponId);
                }
                if (row == 1) {
                    //6、保存领取记录
                    this.saveCustomerCoupon(customerId, couponId, couponInfo.getExpireTime());
                    return true;
                }
            }
        } catch (Exception e) {

        } finally {
            if (null != lock) {
                lock.unlock();
            }
        }
        throw new GuiguException(ResultCodeEnum.COUPON_LESS);
    }

    //获取未使用的最佳优惠券信息
  @Override
    public List<AvailableCouponVo> findAvailableCoupon(Long customerId, BigDecimal orderAmount) {
      //1.定义符合条件的优惠券信息容器
      List<AvailableCouponVo> availableCouponVoList=new ArrayList<>();
      //2.获取未使用的优惠券列表
      List<NoUseCouponVo> list=couponInfoMapper.findNoUseList(customerId);
      //2.1现金券
      List<NoUseCouponVo> typeList=list.stream().filter(item->item.getCouponType().intValue()==1).collect(Collectors.toList());
      for (NoUseCouponVo noUseCouponVo:typeList){
          //使用门槛判断
          //2.1.1.没门槛，订单金额必须大于优惠券减免金额
          //减免金额
          BigDecimal amount = noUseCouponVo.getAmount();
          if(noUseCouponVo.getConditionAmount().doubleValue()==0&&orderAmount.subtract(amount).doubleValue()>0){
              availableCouponVoList.add(this.buildBestNoUseCouponVo(noUseCouponVo,amount));
          }
          //2.1.2.有门槛，订单金额大于优惠券门槛金额
          if(noUseCouponVo.getConditionAmount().doubleValue()>0&&orderAmount.subtract(noUseCouponVo.getConditionAmount()).doubleValue()>0){
              availableCouponVoList.add(this.buildBestNoUseCouponVo(noUseCouponVo, amount));
          }


      }
      //2.2.折扣券
      List<NoUseCouponVo> typeList2=list.stream().filter(item->item.getCouponType().intValue()==2).collect(Collectors.toList());
      for (NoUseCouponVo noUseCouponVo:typeList2){
          //使用门槛判断
          //订单折扣后金额
         BigDecimal discountOrderAmount= orderAmount.multiply(noUseCouponVo.getDiscount()).divide(new BigDecimal("10")).setScale(2, RoundingMode.HALF_UP);
         //减免金额
          BigDecimal reduceAmount = orderAmount.subtract(discountOrderAmount);
          //订单优惠金额
          //2.2.1.没门槛
          if(noUseCouponVo.getCouponType().doubleValue()==0){
              availableCouponVoList.add(this.buildBestNoUseCouponVo(noUseCouponVo, reduceAmount));
          }
          //2.2.2.有门槛，订单折扣后金额大于优惠券门槛金额
          if(noUseCouponVo.getCouponType()>0&&discountOrderAmount.subtract(noUseCouponVo.getConditionAmount()).doubleValue()>0){
              availableCouponVoList.add(this.buildBestNoUseCouponVo(noUseCouponVo, reduceAmount));
          }
      }

      //排序
      if(!CollectionUtils.isEmpty(availableCouponVoList)){
          Collections.sort(availableCouponVoList, new Comparator<AvailableCouponVo>() {
              @Override
              public int compare(AvailableCouponVo o1, AvailableCouponVo o2) {
                  return o1.getReduceAmount().compareTo(o2.getReduceAmount());
              }
          });
      }
      return availableCouponVoList;
    }

    private AvailableCouponVo buildBestNoUseCouponVo(NoUseCouponVo noUseCouponVo, BigDecimal reduceAmount) {

        AvailableCouponVo availableCouponVo=new AvailableCouponVo();
        BeanUtils.copyProperties(noUseCouponVo,availableCouponVo);
        availableCouponVo.setCouponId(noUseCouponVo.getId());
        availableCouponVo.setReduceAmount(reduceAmount);
        return availableCouponVo;

    }

    private void saveCustomerCoupon(Long couponId, Long customerId, Date expireTime) {

        CustomerCoupon customerCoupon = new CustomerCoupon();
        customerCoupon.setCouponId(couponId);
        customerCoupon.setCustomerId(customerId);
        customerCoupon.setExpireTime(expireTime);
        customerCoupon.setReceiveTime(new Date());
        customerCoupon.setStatus(1);
        customerCouponMapper.insert(customerCoupon);
    }


}

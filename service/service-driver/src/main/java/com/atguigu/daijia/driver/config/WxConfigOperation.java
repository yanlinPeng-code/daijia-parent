package com.atguigu.daijia.driver.config;

import cn.binarywang.wx.miniapp.api.WxMaService;
import cn.binarywang.wx.miniapp.api.impl.WxMaServiceImpl;
import cn.binarywang.wx.miniapp.config.impl.WxMaDefaultConfigImpl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

@Component

public class WxConfigOperation {
    @Autowired
    private WxConfig wxConfig;
    @Bean
    public WxMaService wxMaService(){
        //微信小程序id和密钥
        WxMaDefaultConfigImpl config = new WxMaDefaultConfigImpl();
        config.setAppid(wxConfig.getAppid());
        config.setSecret(wxConfig.getSecret());
        WxMaService service= new WxMaServiceImpl();
        service.setWxMaConfig(config);
        return service;
    }
}

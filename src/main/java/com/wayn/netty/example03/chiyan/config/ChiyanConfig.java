package com.wayn.netty.example03.chiyan.config;

import lombok.Data;
import org.w3c.dom.stylesheets.LinkStyle;

import java.util.List;

@Data
public class ChiyanConfig {

    /**
     * 本地启动端口
     */
    private Integer localStartPort;
    private String localStartHost;

    /**
     * 页面域名
     */
    private String pageDomain;

    /**
     * 页面前缀
     */
    private List<String> pagePrefix;

    /**
     * 接口域名
     */
    private String interfaceDomain;


    /**
     * 代理主机（IP）
     */
    private String proxyHost;

    /**
     * 代理主机（端口）
     */
    private Integer proxyPort;


}

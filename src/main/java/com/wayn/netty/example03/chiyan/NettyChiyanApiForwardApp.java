package com.wayn.netty.example03.chiyan;

import cn.hutool.core.util.StrUtil;
import com.alibaba.dcm.DnsCacheManipulator;
import com.wayn.netty.example03.chiyan.config.ChiyanConfig;
import com.wayn.netty.example03.chiyan.handle.LocalChannelHandler;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.cli.*;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * mysql代理通过在建立代理连接设置proxyChannel = bootstrap.connect("121.89.238.61", 80).sync().channel();<br>
 * 同步阻塞保证channelRead方法中proxyChannel已经建立连接成功
 */
@Slf4j
public class NettyChiyanApiForwardApp {

    public static void main(String[] args) throws ParseException {

        // args
        Options options = new Options();
        options.addOption("h", false, "Help");
        options.addOption("localHost", true, "启动主机IP");
        options.addOption("localPort", true, "启动端口");
        options.addOption("pageDomain", true, "页面域名");
        options.addOption("pagePrefix", true, "页面前缀，多个请用都好分割");
        options.addOption("interfaceDomain", true, "接口域名");
        options.addOption("proxyHost", true, "代理主机IP");
        options.addOption("proxyPort", true, "代理主机端口");
        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(options, args);

        if (cmd.hasOption("h")) {
            // print help
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("options", options);
            return;
        }
        int localPort = Integer.parseInt(cmd.getOptionValue("localPort", "82"));
        String localhost = cmd.getOptionValue("localHost", "localhost");
        String pageDomain = cmd.getOptionValue("pageDomain", "api.chiyanjiasu.com");
        String pagePrefixs = cmd.getOptionValue("pagePrefix", "/windowsCommon,/person,/netbar");
        List<String> pagePrefix = new ArrayList<>();
        if (StrUtil.isNotBlank(pagePrefixs)) {
            pagePrefix = Arrays.asList(pagePrefixs.split(","));
        }
        String interfaceDomain = cmd.getOptionValue("interfaceDomain", "api.pre.chiyanjiasu.com");
        String proxyHost = cmd.getOptionValue("proxyHost", "119.97.143.63");
        int proxyPort = Integer.parseInt(cmd.getOptionValue("proxyPort", "80"));

        DnsCacheManipulator.setDnsCache("api.chiyanjiasu.com", "119.97.143.63");
        DnsCacheManipulator.setDnsCache("api.pre.chiyanjiasu.com", "119.97.143.63");

        ChiyanConfig chiyanConfig = new ChiyanConfig();
        chiyanConfig.setPageDomain(pageDomain);
        chiyanConfig.setInterfaceDomain(interfaceDomain);
        chiyanConfig.setLocalStartHost(localhost);
        chiyanConfig.setLocalStartPort(localPort);
        chiyanConfig.setPagePrefix(pagePrefix);
        chiyanConfig.setProxyHost(proxyHost);
        chiyanConfig.setProxyPort(proxyPort);
        log.info(chiyanConfig.toString());
        // 服务端启动，监听3307端口，转发到3306端口
        ServerBootstrap serverBootstrap = new ServerBootstrap();
        NioEventLoopGroup boss = new NioEventLoopGroup();
        NioEventLoopGroup worker = new NioEventLoopGroup();
        serverBootstrap
                .group(boss, worker)
                .channel(NioServerSocketChannel.class)
                .handler(new LoggingHandler(LogLevel.INFO))
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childHandler(new ChannelInitializer<NioSocketChannel>() {
                    protected void initChannel(NioSocketChannel ch) {
                        LocalChannelHandler localChannelHandler = new LocalChannelHandler(chiyanConfig);
                        // ch.pipeline().addLast(new LoggingHandler(LogLevel.INFO));
                        ch.pipeline().addLast(localChannelHandler);
                    }
                });

        serverBootstrap.bind(chiyanConfig.getLocalStartPort()).addListener(future -> {
            if (future.isSuccess()) {
                log.info("server start up on:{}", chiyanConfig.getLocalStartPort());
            }
        });
    }

    public static void closeOnFlush(Channel ch) {
        if (ch.isActive()) {
            ch.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        }
    }

}

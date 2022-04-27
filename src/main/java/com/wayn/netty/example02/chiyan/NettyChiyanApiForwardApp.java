package com.wayn.netty.example02.chiyan;

import com.wayn.netty.example02.chiyan.handle.LocalChannelHandler;
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

/**
 * mysql代理通过在建立代理连接设置proxyChannel = bootstrap.connect("121.89.238.61", 80).sync().channel();<br>
 * 同步阻塞保证channelRead方法中proxyChannel已经建立连接成功
 */
@Slf4j
public class NettyChiyanApiForwardApp {

    public static void main(String[] args) throws InterruptedException {
        // DnsCacheManipulator.setDnsCache("api.chiyanjiasu.com", "127.0.0.1");
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
                        LocalChannelHandler localChannelHandler = new LocalChannelHandler();
                        ch.pipeline().addLast(new LoggingHandler(LogLevel.INFO));
                        ch.pipeline().addLast(localChannelHandler);
                    }
                });

        int localPort = 82;
        serverBootstrap.bind(localPort).addListener(future -> {
            if (future.isSuccess()) {
                log.info("server start up on:{}", localPort);
            }
        });
    }

    public static void closeOnFlush(Channel ch) {
        if (ch.isActive()) {
            ch.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        }
    }

}

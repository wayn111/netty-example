package com.wayn.netty.example02.mysqlforward_1;

import com.wayn.netty.example02.mysqlforward_1.handle.LocalChannelHandler;
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
 * mysql代理通过ChannelOption.AUTO_READ为false不自动读
 */
@Slf4j
public class NettyMysqlForwardApp_1 {

    public static void main(String[] args) throws InterruptedException {
        // 服务端启动，监听3307端口，转发到3306端口
        ServerBootstrap serverBootstrap = new ServerBootstrap();
        NioEventLoopGroup boss = new NioEventLoopGroup();
        NioEventLoopGroup worker = new NioEventLoopGroup();
        serverBootstrap
                .group(boss, worker)
                .channel(NioServerSocketChannel.class)
                .handler(new LoggingHandler(LogLevel.INFO))
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childOption(ChannelOption.AUTO_READ, false)
                .childHandler(new ChannelInitializer<NioSocketChannel>() {
                    protected void initChannel(NioSocketChannel ch) {
                        LocalChannelHandler localChannelHandler = new LocalChannelHandler();
                        ch.pipeline().addLast(new LoggingHandler(LogLevel.INFO));
                        ch.pipeline().addLast(localChannelHandler);
                    }
                });

        int localPort = 9090;
        serverBootstrap.bind(localPort).addListener(future -> {
            log.info("server start up on:{}", localPort);
        });
    }

    public static void closeOnFlush(Channel ch) {
        if (ch.isActive()) {
            ch.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        }
    }

}

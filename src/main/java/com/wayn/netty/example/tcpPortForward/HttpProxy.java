package com.wayn.netty.example.tcpPortForward;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class HttpProxy {
    public static void main(String[] args) {
        ServerBootstrap serverBootstrap = new ServerBootstrap();
        NioEventLoopGroup bossGroup = new NioEventLoopGroup();
        NioEventLoopGroup workerGroup = new NioEventLoopGroup();
        try {
            int localPort = 3307;
            serverBootstrap
                    .group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .handler(new LoggingHandler(LogLevel.INFO))
                    .childHandler(new HexDumpProxyInitializer("localhost", 28079))
                    .childOption(ChannelOption.AUTO_READ, false)
                    .bind(localPort).addListener(future -> {
                        log.info("server start up on:{}", localPort);
                    }).sync().channel().closeFuture().sync();

        } catch (Exception e) {
            log.error(e.getMessage(), e);
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }

    }
}

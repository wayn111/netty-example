package com.wayn.netty.example02.mysqlforward_2.handle;

import com.wayn.netty.example02.mysqlforward_2.NettyMysqlForwardApp_2;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LocalChannelHandler extends ChannelInboundHandlerAdapter {

    private Channel proxyChannel;

    @Override
    public void channelActive(ChannelHandlerContext clientCtx) throws InterruptedException {
        final Channel localChannel = clientCtx.channel();
        Bootstrap bootstrap = new Bootstrap();
        bootstrap
                .group(new NioEventLoopGroup())
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ProxyChannelHandler proxyChannelHandler = new ProxyChannelHandler(localChannel);
                        ch.pipeline().addLast(new LoggingHandler(LogLevel.INFO));
                        ch.pipeline().addLast(proxyChannelHandler);
                    }
                });
        log.info("connect " + proxyChannel);
        proxyChannel = bootstrap.connect("121.89.238.61", 80).sync().channel();
        // proxyChannel = bootstrap.connect("localhost", 84).sync().channel();
        log.info("connect " + proxyChannel);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        log.info("channelRead " + proxyChannel);
        if (proxyChannel.isActive()) {
            proxyChannel.writeAndFlush(msg);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        if (proxyChannel != null) {
            NettyMysqlForwardApp_2.closeOnFlush(proxyChannel);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        NettyMysqlForwardApp_2.closeOnFlush(ctx.channel());
    }
}

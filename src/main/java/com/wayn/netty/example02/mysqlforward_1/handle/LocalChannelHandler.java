package com.wayn.netty.example02.mysqlforward_1.handle;

import com.wayn.netty.example02.mysqlforward_1.NettyMysqlForwardApp;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LocalChannelHandler extends ChannelInboundHandlerAdapter {

    private Channel proxyChannel;

    @Override
    public void channelActive(ChannelHandlerContext clientCtx) {
        final Channel localChannel = clientCtx.channel();
        Bootstrap bootstrap = new Bootstrap();
        ProxyChannelHandler proxyChannelHandler = new ProxyChannelHandler(localChannel);
        bootstrap
                .group(new NioEventLoopGroup())
                .option(ChannelOption.AUTO_READ, false)
                .channel(NioSocketChannel.class)
                .handler(proxyChannelHandler);
        ChannelFuture channelFuture = bootstrap.connect("localhost", 84);
        proxyChannel = channelFuture.channel();
        channelFuture.addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                localChannel.read();
            } else {
                localChannel.close();
            }
        });

    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (proxyChannel.isActive()) {
            proxyChannel.writeAndFlush(msg).addListener((ChannelFutureListener) future -> {
                if (future.isSuccess()) {
                    ctx.channel().read();
                } else {
                    future.channel().closeFuture();
                }
            });
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        if (proxyChannel != null) {
            NettyMysqlForwardApp.closeOnFlush(proxyChannel);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        NettyMysqlForwardApp.closeOnFlush(ctx.channel());
    }
}

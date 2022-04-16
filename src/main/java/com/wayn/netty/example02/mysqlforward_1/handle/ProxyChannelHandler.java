package com.wayn.netty.example02.mysqlforward_1.handle;

import com.wayn.netty.example02.mysqlforward_1.NettyMysqlForwardApp;
import io.netty.channel.*;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ProxyChannelHandler extends ChannelInboundHandlerAdapter {

    private Channel localChannel;

    public ProxyChannelHandler(Channel localChannel) {
        this.localChannel = localChannel;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        ctx.read();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        NettyMysqlForwardApp.closeOnFlush(localChannel);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        localChannel.writeAndFlush(msg).addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                ctx.channel().read();
            } else {
                future.channel().closeFuture();
            }
        });
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error(cause.getMessage(), cause);
        NettyMysqlForwardApp.closeOnFlush(ctx.channel());
    }
}

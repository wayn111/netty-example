package com.wayn.netty.example02.mysqlforward_2.handle;

import com.wayn.netty.example02.mysqlforward_2.NettyMysqlForwardApp_2;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ProxyChannelHandler extends ChannelInboundHandlerAdapter {

    private Channel localChannel;

    public ProxyChannelHandler(Channel localChannel) {
        this.localChannel = localChannel;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        // ctx.read();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        NettyMysqlForwardApp_2.closeOnFlush(localChannel);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        localChannel.writeAndFlush(msg);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error(cause.getMessage(), cause);
        NettyMysqlForwardApp_2.closeOnFlush(ctx.channel());
    }
}

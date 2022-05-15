package com.wayn.netty.example04.handle;

import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

public class HttpProxyRemoteHandler extends ChannelInboundHandlerAdapter {
    private Channel clientChannel;
    private Channel remoteChannel;

    public HttpProxyRemoteHandler(Channel clientChannel) {
        this.clientChannel = clientChannel;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        this.remoteChannel = ctx.channel();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        clientChannel.writeAndFlush(msg); // just forward
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        flushAndClose(clientChannel);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable e) {
        e.printStackTrace();
        flushAndClose(remoteChannel);
    }

    private void flushAndClose(Channel ch) {
        if (ch != null && ch.isActive()) {
            ch.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        }
    }
}

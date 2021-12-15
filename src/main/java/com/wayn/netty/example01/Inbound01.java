package com.wayn.netty.example01;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import java.nio.charset.StandardCharsets;

public class Inbound01 extends ChannelInboundHandlerAdapter {
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        String s = (String) msg;
        System.out.println("Inbound01 接收到消息:" + s);
        if ("quit".equals(s)) {
            ctx.close().addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    if (future.isSuccess()) {
                        System.out.println("关闭成功");
                    }
                }
            });
            return;
        }
        ctx.writeAndFlush(Unpooled.buffer().writeBytes("a".getBytes(StandardCharsets.UTF_8)));
        // ctx.fireChannelRead(Unpooled.buffer().writeBytes("a".getBytes(StandardCharsets.UTF_8)));
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        System.out.println("连接建立");
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        System.out.println("连接关闭1");
    }
}

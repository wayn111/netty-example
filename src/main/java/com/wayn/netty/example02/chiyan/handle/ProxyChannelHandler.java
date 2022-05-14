package com.wayn.netty.example02.chiyan.handle;

import com.wayn.netty.example02.chiyan.NettyChiyanApiForwardApp;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;

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
        NettyChiyanApiForwardApp.closeOnFlush(localChannel);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        ByteBuf byteBuf = (ByteBuf) msg;
        byte[] bytes = ByteBufUtil.getBytes(byteBuf);
        System.out.println(new String(bytes, StandardCharsets.UTF_8));
        localChannel.writeAndFlush(msg);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error(cause.getMessage(), cause);
        NettyChiyanApiForwardApp.closeOnFlush(ctx.channel());
    }
}

package com.wayn.netty.example03.chiyan.handle;

import com.wayn.netty.example03.chiyan.NettyChiyanApiForwardApp;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.ReferenceCountUtil;
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
        String resp = new String(bytes);
        if (resp.contains("/captchas/image")) {
            // resp = resp.replace("http://api.chiyanjiasu.com/captchas/image", "/captchas/image");
            log.debug(resp);
            ByteBuf byteBuf1 = Unpooled.copiedBuffer(resp.getBytes(StandardCharsets.UTF_8));
            localChannel.writeAndFlush(byteBuf1);
            ReferenceCountUtil.release(byteBuf);
            return;
        }

        if (resp.contains("Content-Length: 388009")) {
            resp = resp.replace("Content-Length: 388009", "Content-Length: 387983");
            log.debug(resp);
            ByteBuf byteBuf1 = Unpooled.copiedBuffer(resp.getBytes(StandardCharsets.UTF_8));
            localChannel.writeAndFlush(byteBuf1);
            byteBuf.release();
            return;
        }

        localChannel.writeAndFlush(msg);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error(cause.getMessage(), cause);
        NettyChiyanApiForwardApp.closeOnFlush(ctx.channel());
    }

    public static void main(String[] args) {
        String str = "http://api.chiyanjiasu.com/captchas/image";
        String str1 = "/captchas/image";
        System.out.println(str.getBytes().length);
        System.out.println(str1.getBytes().length);
    }
}

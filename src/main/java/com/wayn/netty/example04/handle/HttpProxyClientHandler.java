package com.wayn.netty.example04.handle;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;

import javax.sound.midi.Soundbank;
import java.nio.charset.StandardCharsets;

public class HttpProxyClientHandler extends ChannelInboundHandlerAdapter {
    /*代理服务端channel*/
    private Channel clientChannel;
    /*目标主机channel*/
    private Channel remoteChannel;
    /*解析真实客户端的header*/
    private HttpProxyClientHeader header = new HttpProxyClientHeader();

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        clientChannel = ctx.channel();
    }

    /**
     * 注意在真实客户端请求一个页面的时候，此方法不止调用一次，
     * 这是TCP底层决定的（拆包/粘包）
     */
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        ByteBuf byteBuf = (ByteBuf) msg;
        byte[] bytes = ByteBufUtil.getBytes(byteBuf);
        String req = new String(bytes, StandardCharsets.UTF_8);
        System.out.println(req);
        if (header.isComplete()) {
                /*如果在真实客户端的本次请求中已经解析过header了，
                说明代理客户端已经在目标主机建立了连接，直接将真实客户端的数据写给目标主机*/
            remoteChannel.writeAndFlush(msg); // just forward
            return;
        }
        ByteBuf in = (ByteBuf) msg;
        header.digest(in);/*解析目标主机信息*/
        if (!header.isComplete()) {
            /*如果解析过一次header之后未完成解析，直接返回，释放buf*/
            in.release();
            return;
        }
        // disable AutoRead until remote connection is ready
        clientChannel.config().setAutoRead(false);
        if (header.isHttps()) { // if https, respond 200 to create tunnel
            clientChannel.writeAndFlush(Unpooled.wrappedBuffer("HTTP/1.1 200 Connection Established\r\n\r\n".getBytes()));
        }
        /**
         *
         * 下面为真实客户端第一次来到的时候，代理客户端向目标客户端发起连接
         */
        Bootstrap b = new Bootstrap();
        b.group(clientChannel.eventLoop()) // use the same EventLoop
                .channel(clientChannel.getClass())
                .handler(new HttpProxyRemoteHandler(clientChannel));
        ChannelFuture f = b.connect(header.getHost(), header.getPort());
        remoteChannel = f.channel();
        f.addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                // connection is ready, enable AutoRead
                clientChannel.config().setAutoRead(true);
                // forward header and remaining bytes
                if (!header.isHttps()) {
                    // in读取一次缓冲区就没有了，header.byteBuf里面存了一份
                    remoteChannel.writeAndFlush(header.getByteBuf());
                }
            } else {
                in.release();
                clientChannel.close();
            }
        });
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        flushAndClose(remoteChannel);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable e) {
        e.printStackTrace();
        flushAndClose(clientChannel);
    }

    private void flushAndClose(Channel ch) {
        if (ch != null && ch.isActive()) {
            ch.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        }
    }
}

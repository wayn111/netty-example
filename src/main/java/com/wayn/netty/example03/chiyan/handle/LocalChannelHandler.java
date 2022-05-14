package com.wayn.netty.example03.chiyan.handle;

import cn.hutool.core.util.StrUtil;
import com.wayn.netty.example03.chiyan.NettyChiyanApiForwardApp;
import com.wayn.netty.example03.chiyan.config.ChiyanConfig;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;

@Slf4j
public class LocalChannelHandler extends ChannelInboundHandlerAdapter {

    private ChiyanConfig chiyanConfig;

    private static final String regex = "\r\n";

    public LocalChannelHandler(ChiyanConfig chiyanConfig) {
        this.chiyanConfig = chiyanConfig;
    }

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
                        // ch.pipeline().addLast(new LoggingHandler(LogLevel.INFO));
                        ch.pipeline().addLast(proxyChannelHandler);
                    }
                });
        proxyChannel = bootstrap.connect(chiyanConfig.getProxyHost(), chiyanConfig.getProxyPort()).sync().channel();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        ByteBuf byteBuf = (ByteBuf) msg;
        byte[] bytes = ByteBufUtil.getBytes(byteBuf);
        String req = new String(bytes, StandardCharsets.UTF_8);
        String[] split = req.split(regex);

        String host = chiyanConfig.getLocalStartHost() + ":" + chiyanConfig.getLocalStartPort();
        if (StrUtil.containsAnyIgnoreCase(split[0], chiyanConfig.getPagePrefix().toArray(new String[0]))) {
            if (proxyChannel.isActive()) {
                req = req.replace(host, chiyanConfig.getPageDomain());
                log.info(req);
                log.debug("channelRead " + proxyChannel);
                ByteBuf byteBuf1 = Unpooled.copiedBuffer(req.getBytes(StandardCharsets.UTF_8));
                if (proxyChannel.isActive()) {
                    proxyChannel.writeAndFlush(byteBuf1);
                }
            }
            byteBuf.release();
            return;
        }
        req = req.replace(host, chiyanConfig.getInterfaceDomain());
        ByteBuf byteBuf1 = Unpooled.copiedBuffer(req.getBytes(StandardCharsets.UTF_8));
        if (proxyChannel.isActive()) {
            proxyChannel.writeAndFlush(byteBuf1);
        }
        byteBuf.release();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        if (proxyChannel != null) {
            NettyChiyanApiForwardApp.closeOnFlush(proxyChannel);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        NettyChiyanApiForwardApp.closeOnFlush(ctx.channel());
    }
}

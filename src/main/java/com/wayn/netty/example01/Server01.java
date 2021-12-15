package com.wayn.netty.example01;

import cn.hutool.system.SystemUtil;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.DelimiterBasedFrameDecoder;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;

public class Server01 {

    public static void main(String[] args) {
        ServerBootstrap serverBootstrap = new ServerBootstrap();
        NioEventLoopGroup bossGroup = new NioEventLoopGroup();
        NioEventLoopGroup workGroup = new NioEventLoopGroup();
        try {

            if (SystemUtil.getOsInfo().isWindows()) {
                serverBootstrap.channel(NioServerSocketChannel.class);
            } else if (SystemUtil.getOsInfo().isLinux()) {
                serverBootstrap.channel(EpollServerSocketChannel.class);
            }
            serverBootstrap.group(bossGroup, workGroup);
            serverBootstrap.handler(new LoggingHandler(LogLevel.DEBUG));
            serverBootstrap.childHandler(new ChannelInitializer<NioSocketChannel>() {

                @Override
                protected void initChannel(NioSocketChannel ch) {
                    ByteBuf delimiter = Unpooled.copiedBuffer("#".getBytes());
                    ch.pipeline().addLast(new DelimiterBasedFrameDecoder(1024, delimiter));
                    ch.pipeline().addLast(new StringDecoder());
                    ch.pipeline().addLast(new Inbound01());
                    // ch.pipeline().addLast(new Inbound02());
                    // ch.pipeline().addLast(new Outbound01());
                    // ch.pipeline().addLast(new Outbound02());
                    ch.pipeline().addLast(new StringEncoder());
                }

            });
            ChannelFuture sync = serverBootstrap.bind("localhost", 8080).sync();
            System.out.println("bind 8080");
            sync.channel().closeFuture().sync();
        } catch (InterruptedException e) {
            e.printStackTrace();
            bossGroup.shutdownGracefully();
            workGroup.shutdownGracefully();
        }

    }
}

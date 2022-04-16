package com.wayn.netty.example02.mysqlforward;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class NettyMysqlForwardApp {

    public static void main(String[] args) throws InterruptedException {


        // 服务端启动，监听3307端口，转发到3306端口
        ServerBootstrap serverBootstrap = new ServerBootstrap();
        NioEventLoopGroup boss = new NioEventLoopGroup();
        NioEventLoopGroup worker = new NioEventLoopGroup();
        serverBootstrap
                .group(boss, worker)
                .channel(NioServerSocketChannel.class)
                .handler(new LoggingHandler(LogLevel.INFO))
                .childHandler(new ChannelInitializer<NioSocketChannel>() {
                    protected void initChannel(NioSocketChannel ch) {
                        final Channel[] outboundChannel = new Channel[1];
                        ch.pipeline().addLast(new LoggingHandler(LogLevel.INFO), new ChannelInboundHandlerAdapter() {
                            @Override
                            public void channelActive(ChannelHandlerContext clientCtx) {
                                final Channel inboundChannel = clientCtx.channel();
                                // 3306第一次建立连接时，启动3307端口连接
                                Bootstrap bootstrap = new Bootstrap();
                                bootstrap
                                        .group(new NioEventLoopGroup())
                                        .channel(NioSocketChannel.class)
                                        .handler(new ChannelInboundHandlerAdapter() {
                                            @Override
                                            public void channelActive(ChannelHandlerContext ctx) {
                                                ctx.read();
                                            }

                                            @Override
                                            public void channelInactive(ChannelHandlerContext ctx) {
                                                closeOnFlush(inboundChannel);
                                            }

                                            @Override
                                            public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                                                inboundChannel.writeAndFlush(msg).addListener(new ChannelFutureListener() {
                                                    @Override
                                                    public void operationComplete(ChannelFuture future) throws Exception {
                                                        if (future.isSuccess()) {
                                                            ctx.channel().read();
                                                        } else {
                                                            future.channel().closeFuture();
                                                        }
                                                    }
                                                });
                                            }

                                            @Override
                                            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                                                log.error(cause.getMessage(), cause);
                                                closeOnFlush(ctx.channel());
                                            }
                                        }).option(ChannelOption.AUTO_READ, false);

                                ChannelFuture connect = bootstrap.connect("localhost", 84);
                                // ChannelFuture connect = bootstrap.connect("waynmysql.mysql.rds.aliyuncs.com", 3306);
                                outboundChannel[0] = connect.channel();
                                connect.addListener(future -> {
                                    if (future.isSuccess()) {
                                        inboundChannel.read();
                                    } else {
                                        inboundChannel.close();
                                    }
                                });
                            }

                            @Override
                            public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                                if (outboundChannel[0].isActive()) {
                                    outboundChannel[0].writeAndFlush(msg).addListener(new ChannelFutureListener() {
                                        @Override
                                        public void operationComplete(ChannelFuture future) throws Exception {
                                            if (future.isSuccess()) {
                                                ctx.channel().read();
                                            } else {
                                                future.channel().close();
                                            }
                                        }
                                    });
                                }
                            }

                            @Override
                            public void channelInactive(ChannelHandlerContext ctx) {
                                if (outboundChannel[0] != null) {
                                    closeOnFlush(outboundChannel[0]);
                                }
                            }

                            @Override
                            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                                cause.printStackTrace();
                                closeOnFlush(ctx.channel());
                            }
                        });
                    }
                }).childOption(ChannelOption.AUTO_READ, false);

        int localPort = 3307;
        serverBootstrap.bind(localPort).addListener(future -> {
            log.info("server start up on:{}", localPort);
        }).sync().channel().closeFuture().sync();
    }

    static void closeOnFlush(Channel ch) {
        if (ch.isActive()) {
            ch.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        }
    }

}

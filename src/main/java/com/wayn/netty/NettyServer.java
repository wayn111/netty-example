package com.wayn.netty;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.CharsetUtil;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class NettyServer {

    private static final int port = 99;

    private static final Map<String, Object> channelMap = new HashMap<>();


    public static void main(String[] args) throws InterruptedException {
        ServerBootstrap serverBootstrap = new ServerBootstrap();
        NioEventLoopGroup boss = null;
        NioEventLoopGroup worker = null;
        try {
            boss = new NioEventLoopGroup();
            worker = new NioEventLoopGroup();
            serverBootstrap.group(boss, worker);
            serverBootstrap.channel(NioServerSocketChannel.class);
            serverBootstrap.childHandler(new ChannelInitializer<NioSocketChannel>() {
                protected void initChannel(NioSocketChannel ch) {
                    IdleStateHandler idleStateHandler = new IdleStateHandler(5, 0, 0) {

                        // 心跳检测
                        @Override
                        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
                            if (evt instanceof IdleStateEvent) {
                                IdleStateEvent e = (IdleStateEvent) evt;
                                if (e.state() == IdleState.READER_IDLE) {
                                    ByteBuf buffer = Unpooled.buffer();
                                    buffer.writeBytes("pong".getBytes(StandardCharsets.UTF_8));
                                    ctx.channel().writeAndFlush(buffer).addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
                                    String id = ctx.channel().id().asLongText();
                                    channelMap.forEach((s, o) -> {
                                        if (s.equals(id)) {
                                            channelMap.remove(s);
                                        }
                                    });
                                }
                            }
                        }

                        // 发生异常时，关闭连接
                        @Override
                        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                            log.error(cause.getMessage(), cause);
                            String id = ctx.channel().id().asLongText();
                            channelMap.remove(id);
                            ctx.close();
                        }

                    };
                    ch.pipeline().addLast("idleStateHandler", idleStateHandler);
                    // 换行符解码器
                    ch.pipeline().addLast(new LineBasedFrameDecoder(1024));
                    ch.pipeline().addLast(new LoggingHandler(LogLevel.INFO));
                    ch.pipeline().addLast("decoder", new StringDecoder(CharsetUtil.UTF_8));
                    ch.pipeline().addLast("MyHandle", new SimpleChannelInboundHandler<String>() {
                        @Override
                        public void channelActive(ChannelHandlerContext ctx) throws Exception {
                            String id = ctx.channel().id().asLongText();
                            log.info("客户端建立连接：{}", id);
                            channelMap.put(id, ctx.channel());
                            ctx.channel().writeAndFlush(id);
                            super.channelActive(ctx);
                        }

                        @Override
                        protected void channelRead0(ChannelHandlerContext ctx, String s) {
                            log.info("收到客户端消息：{}", s);
                            ctx.channel().writeAndFlush("server");
                        }

                    });
                    ch.pipeline().addLast("encoder", new StringEncoder(CharsetUtil.UTF_8));

                }
            });
            ChannelFuture channelFuture = serverBootstrap.bind(port).sync().addListener(future -> {
                if (future.isSuccess()) {
                    log.info("server start up on {}", port);
                }
            });
            channelFuture.channel().closeFuture().sync();
        } finally {
            if (boss != null) {
                boss.shutdownGracefully();
            }
            if (worker != null) {
                worker.shutdownGracefully();
            }
        }
    }

}


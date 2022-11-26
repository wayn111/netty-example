package com.wayn.netty.example05;

import cn.hutool.core.util.HexUtil;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
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

import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * 带有连接重试的tcp客户端
 */
@Slf4j
public class LengthFieldBasedNettyServer {

    // 绑定端口
    private static final int port = 99;

    // 维护channel列表，将用户信息和channel做绑定，一个channel代表一个tcp连接
    private static final Map<String, Object> channelMap = new HashMap<>();

    public static void main(String[] args) throws InterruptedException {
        // 创建服务端启动器
        ServerBootstrap serverBootstrap = new ServerBootstrap();
        // 指定连接接收线程，处理所有用户连接，转给工作线程
        final NioEventLoopGroup boss = new NioEventLoopGroup();
        // 指定工作线程，处理用户连接
        final NioEventLoopGroup worker = new NioEventLoopGroup();
        serverBootstrap.group(boss, worker); // 绑定线程组
        serverBootstrap.channel(NioServerSocketChannel.class);  // 底层使用nio处理
        serverBootstrap.childHandler(new ChannelInitializer<NioSocketChannel>() {
            protected void initChannel(NioSocketChannel ch) {
                ch.pipeline().addLast(new LoggingHandler(LogLevel.INFO));
                ch.pipeline().addLast("frameDecoder",
                        new MyLengthFieldBasedFrameDecoder(1024 * 50, 0, 2, 0, 0, true));
                // netty日志记录，打印包信息
                // 自定义解码器，实现自定义业务逻辑，使用SimpleChannelInboundHandler可以避免ByteBuf的回收问题
                ch.pipeline().addLast("MyHandle", new ChannelInboundHandlerAdapter() {
                    // 连接第一次创建会调用这个方法
                    @Override
                    public void channelActive(ChannelHandlerContext ctx) throws Exception {
                        String id = ctx.channel().id().asLongText();
                        log.info("客户端建立连接：{}", id);
                        channelMap.put(id, ctx.channel());
                        ctx.channel().writeAndFlush("this is come from server!");
                        super.channelActive(ctx);
                    }

                    // 获取来自客户端的数据
                    @Override
                    public void channelRead(ChannelHandlerContext ctx, Object msg) {
                        ByteBuf byteBuf = (ByteBuf) msg;
                        try {
                            short length = byteBuf.getShort(0);
                            long account = byteBuf.getShort(2);
                            long accToken = byteBuf.getLong(4);
                            long returnToken = byteBuf.getLong(12);
                            System.out.println(length);
                            System.out.println(account);
                            System.out.println(accToken);
                            System.out.println(returnToken);
                            System.out.println("----------------------------");
                            ByteBuf returnBuf = Unpooled.buffer();
                            returnBuf.writeShort(34);
                            returnBuf.writeLong(889408L);
                            returnBuf.writeLong(322337203685397019L);
                            returnBuf.writeLong(322337203685397019L);
                            returnBuf.writeShort(1);
                            returnBuf.writeLong(new Date().getTime() / 1000);
                            byte[] bytes = ByteBufUtil.getBytes(returnBuf);
                            System.out.println(HexUtil.encodeHex(bytes));
                            System.out.println(0xff);
                            ctx.channel().writeAndFlush(returnBuf);
                        } finally {
                            byteBuf.release();
                        }
                    }

                    @Override
                    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
                        super.channelInactive(ctx);
                    }

                    @Override
                    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                        ctx.close();
                        log.error(cause.getMessage(), cause);
                    }
                });
            }
        });
        ChannelFuture channelFuture = serverBootstrap.bind(port).addListener(future -> {
            if (future.isSuccess()) {
                log.info("server start up on {}", port);
            }
        });
        channelFuture.channel().closeFuture().addListener((ChannelFutureListener) future -> {
            boss.shutdownGracefully();
            worker.shutdownGracefully();
            log.info(future.channel().toString());
        });

    }

}

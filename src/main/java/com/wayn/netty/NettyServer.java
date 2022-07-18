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

/**
 * tcp服务端
 */
@Slf4j
public class NettyServer {

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
        try {
            serverBootstrap.group(boss, worker); // 绑定线程组
            serverBootstrap.channel(NioServerSocketChannel.class);  // 底层使用nio处理
            serverBootstrap.childHandler(new ChannelInitializer<NioSocketChannel>() {
                protected void initChannel(NioSocketChannel ch) {
                    // 创建一个心跳检测的handle，服务端创建连接后，5秒内没有收到客户端的心跳就会触发IdleState.READER_IDLE
                    IdleStateHandler idleStateHandler = new IdleStateHandler(5, 0, 0) {
                        // 5秒内没有收到客户端的心跳会触发userEventTriggered方法
                        @Override
                        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
                            if (evt instanceof IdleStateEvent) {
                                IdleStateEvent e = (IdleStateEvent) evt;
                                // 检测是读状态就发送pong消息
                                if (e.state() == IdleState.READER_IDLE) {
                                    ByteBuf buffer = Unpooled.buffer();
                                    buffer.writeBytes("pong".getBytes(StandardCharsets.UTF_8));
                                    ctx.channel().writeAndFlush(buffer).addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
                                    String id = ctx.channel().id().asLongText();
                                    // 删除channelMap中客户端连接
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
                    // 通过对象实例，而不是new创建，避免大量客户端连接导致浪费空间
                    ch.pipeline().addLast("idleStateHandler", idleStateHandler);
                    // 换行符解码器，指定包与包之间的分隔符是"\n"和"\r\n",避免拆包粘包
                    ch.pipeline().addLast(new LineBasedFrameDecoder(1024));
                    // netty日志记录，打印包信息
                    // ch.pipeline().addLast(new LoggingHandler(LogLevel.INFO));
                    // 入站字符解码器
                    ch.pipeline().addLast("decoder", new StringDecoder(CharsetUtil.UTF_8));
                    // 自定义解码器，实现自定义业务逻辑，使用SimpleChannelInboundHandler可以避免ByteBuf的回收问题
                    ch.pipeline().addLast("MyHandle", new SimpleChannelInboundHandler<String>() {
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
                        protected void channelRead0(ChannelHandlerContext ctx, String s) {
                            log.info("收到客户端消息：{}", s);
                            ctx.channel().writeAndFlush("server reply\r\n");
                        }

                    });
                    // 出站字符编码器
                    ch.pipeline().addLast("encoder", new StringEncoder(CharsetUtil.UTF_8));
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
        } finally {

        }

    }

}


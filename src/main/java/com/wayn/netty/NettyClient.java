package com.wayn.netty;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.CharsetUtil;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * 带有连接重试的tcp客户端
 */
@Slf4j
public class NettyClient {
    // 心跳发送包，使用unreleasableBuffer避免重复创建对象
    public static final ByteBuf HEART_BUF = Unpooled.unreleasableBuffer(Unpooled.copiedBuffer("ping", CharsetUtil.UTF_8));

    public static void main(String[] args) throws InterruptedException {
        // 创建客户端启动器
        Bootstrap bootstrap = new Bootstrap();
        NioEventLoopGroup group = new NioEventLoopGroup();

        bootstrap.group(group)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel ch) {
                        // 创建一个心跳检测的handle，客户端创建连接后，3秒内没有收到服务端的心跳就会触发IdleState.WRITER_IDLE
                        ch.pipeline().addLast("idleStateHandler", new IdleStateHandler(0, 3, 0));
                        // 使用双向处理器，发送ping消息给服务端
                        ch.pipeline().addLast(new ChannelDuplexHandler() {
                            // 服务端连接关闭时，进行重试操作
                            @Override
                            public void channelInactive(ChannelHandlerContext ctx) {
                                ctx.executor().schedule(() -> NettyClient.connect(bootstrap), 5, TimeUnit.SECONDS);
                            }

                            // 3秒内没有向服务端发送心跳会触发userEventTriggered方法
                            @Override
                            public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
                                if (evt instanceof IdleStateEvent) {
                                    IdleStateEvent e = (IdleStateEvent) evt;
                                    if (e.state() == IdleState.WRITER_IDLE) {
                                        ctx.channel().writeAndFlush(HEART_BUF);
                                    }
                                }
                            }

                            // 发生异常时，关闭连接
                            @Override
                            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                                log.error(cause.getMessage(), cause);
                                ctx.close();
                            }
                        });
                        // netty日志记录，打印包信息
                        // ch.pipeline().addLast(new LoggingHandler(LogLevel.INFO));
                        // 自定义解码器，实现自定义业务逻辑，使用ChannelInboundHandlerAdapter时需要手动关闭byteBuf
                        ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {

                            // 连接建立触发channelActive
                            @Override
                            public void channelActive(ChannelHandlerContext ctx) {
                                ByteBuf buffer = ctx.alloc().buffer();
                                buffer.writeBytes((new Date() + ": hello world!\r\n").getBytes(StandardCharsets.UTF_8));
                                ctx.writeAndFlush(buffer);
                            }

                            // 收到来自服务端的消息
                            @Override
                            public void channelRead(ChannelHandlerContext ctx, Object msg) {
                                ByteBuf byteBuf = (ByteBuf) msg;
                                try {
                                    log.info(new String(ByteBufUtil.getBytes(byteBuf)));
                                } finally { // 由于使用的是ChannelInboundHandlerAdapter，需要手动释放byteBuf
                                    byteBuf.release();
                                }
                            }

                        });
                    }
                });

        connect(bootstrap);

    }

    /**
     * 连接重试
     *
     * @param bootstrap
     */
    private static void connect(Bootstrap bootstrap) {
        try {
            bootstrap.connect("127.0.0.1", 99).addListener(future -> {
                if (future.isSuccess()) {
                    log.info("连接成功!");
                } else {
                    Thread.sleep(2000);
                    log.info("连接失败，开始重连");
                    connect(bootstrap);
                }
            });
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            connect(bootstrap);
        }
    }
}

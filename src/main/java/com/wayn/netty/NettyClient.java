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
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.concurrent.TimeUnit;

@Slf4j
public class NettyClient {
    // 心跳发送包，使用unreleasableBuffer避免重复创建对象
    public static final ByteBuf HEART_BUF = Unpooled.unreleasableBuffer(Unpooled.copiedBuffer(new char[]{'p', 'i', 'n', 'g'}, CharsetUtil.UTF_8));

    public static void main(String[] args) {
        final Bootstrap bootstrap = new Bootstrap();
        NioEventLoopGroup group = new NioEventLoopGroup();

        bootstrap.group(group)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel ch) {
                        ch.pipeline().addLast("idleStateHandler", new IdleStateHandler(0, 3, 0));
                        ch.pipeline().addLast(new ChannelDuplexHandler() {
                            @Override
                            public void channelInactive(ChannelHandlerContext ctx) {
                                ctx.executor().schedule(() -> NettyClient.connect(bootstrap), 5, TimeUnit.SECONDS);
                            }

                            @Override
                            public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
                                if (evt instanceof IdleStateEvent) {
                                    IdleStateEvent e = (IdleStateEvent) evt;
                                    if (e.state() == IdleState.WRITER_IDLE) {
                                        ctx.channel().writeAndFlush(HEART_BUF);
                                    }
                                }
                            }

                            @Override
                            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                                log.error(cause.getMessage(), cause);
                                ctx.close();
                            }
                        });
                        ch.pipeline().addLast(new LoggingHandler(LogLevel.INFO));
                        ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                            @Override
                            public void channelActive(ChannelHandlerContext ctx) {
                                ByteBuf buffer = ctx.alloc().buffer();
                                buffer.writeBytes((new Date() + ": hello world!\\r\\n").getBytes(StandardCharsets.UTF_8));
                                ctx.writeAndFlush(buffer);
                            }

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
    @SneakyThrows
    private static void connect(Bootstrap bootstrap) {
        bootstrap.connect("127.0.0.1", 99).sync().addListener(future -> {
            if (future.isSuccess()) {
                log.info("连接成功!");
            } else {
                Thread.sleep(2000);
                log.info("连接失败，开始重连");
                connect(bootstrap);
            }
        });
    }
}

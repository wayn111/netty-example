package com.wayn.netty.example05;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import lombok.extern.slf4j.Slf4j;

import java.nio.ByteOrder;

/**
 * 带有连接重试的tcp客户端
 */
@Slf4j
public class LengthFieldBasedNettyClient {

    public static void main(String[] args) throws InterruptedException {
        // 创建客户端启动器
        Bootstrap bootstrap = new Bootstrap();
        NioEventLoopGroup group = new NioEventLoopGroup();

        bootstrap.group(group)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel ch) {
                        // netty日志记录，打印包信息
                        ch.pipeline().addLast("frameDecoder",
                                new LengthFieldBasedFrameDecoder( 1024 * 50, 0, 2, 0, 0, true));
                        // ch.pipeline().addLast(new LoggingHandler(LogLevel.INFO));
                        // 自定义解码器，实现自定义业务逻辑，使用ChannelInboundHandlerAdapter时需要手动关闭byteBuf
                        ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                            // 连接建立触发channelActive
                            @Override
                            public void channelActive(ChannelHandlerContext ctx) {
                                for (int i = 0; i < 1; i++) {
                                    ByteBuf buffer = ctx.alloc().buffer();
                                    buffer.writeShort(18);
                                    buffer.writeShort(3);
                                    buffer.writeLong(889408L);
                                    buffer.writeLong(322337203685397019L);
                                    ctx.channel().writeAndFlush(buffer);
                                }
                            }

                            // 收到来自服务端的消息
                            @Override
                            public void channelRead(ChannelHandlerContext ctx, Object msg) {
                                ByteBuf byteBuf = (ByteBuf) msg;
                                try {
                                    short length = byteBuf.getShort(0);
                                    long account = byteBuf.getLong(2);
                                    long accToken = byteBuf.getLong(10);
                                    long returnToken = byteBuf.getLong(18);
                                    long status = byteBuf.getShort(26);
                                    long time = byteBuf.getLong(28);
                                    System.out.println(length);
                                    System.out.println(account);
                                    System.out.println(accToken);
                                    System.out.println(returnToken);
                                    System.out.println(status);
                                    System.out.println(time);
                                    System.out.println("----------------------------");
                                    // ctx.channel().writeAndFlush(returnBuf);
                                } finally {
                                    byteBuf.release();
                                }
                            }

                            @Override
                            public void channelInactive(ChannelHandlerContext ctx) throws Exception {
                                System.out.println("连接关闭");
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
            bootstrap.connect("localhost", 99).addListener(future -> {
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

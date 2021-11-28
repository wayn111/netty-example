package com.wayn.netty;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;

import java.nio.charset.StandardCharsets;
import java.util.Date;

public class NettyClient {
    public static void main(String[] args) {
        Bootstrap bootstrap = new Bootstrap();
        NioEventLoopGroup group = new NioEventLoopGroup();

        bootstrap.group(group)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel ch) {
                        ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                            @Override
                            public void channelActive(ChannelHandlerContext ctx) {
                                ByteBuf buffer = ctx.alloc().buffer();
                                buffer.writeBytes((new Date() + ": hello world!").getBytes(StandardCharsets.UTF_8));
                                ctx.writeAndFlush(buffer);
                            }

                            @Override
                            public void channelRead(ChannelHandlerContext ctx, Object msg) {
                                ByteBuf byteBuf = (ByteBuf) msg;
                                System.out.println(decode(byteBuf));
                            }

                            private String decode(ByteBuf byteBuf) {
                                int readableBytes = byteBuf.readableBytes();
                                byte[] bytes = new byte[readableBytes];
                                byteBuf.getBytes(byteBuf.readerIndex(), bytes);
                                return new String(bytes);
                            }
                        });
                    }
                });

        bootstrap.connect("127.0.0.1", 99).channel();

    }
}

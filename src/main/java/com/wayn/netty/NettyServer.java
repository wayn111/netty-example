package com.wayn.netty;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;

@Slf4j
public class NettyServer {

    public static void main(String[] args) {
        ServerBootstrap serverBootstrap = new ServerBootstrap();

        NioEventLoopGroup boss = new NioEventLoopGroup();
        NioEventLoopGroup worker = new NioEventLoopGroup();
        serverBootstrap.group(boss, worker);
        serverBootstrap.channel(NioServerSocketChannel.class);
        serverBootstrap.childHandler(new ChannelInitializer<NioSocketChannel>() {
            protected void initChannel(NioSocketChannel ch) {
                ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                    @Override
                    public void channelRead(ChannelHandlerContext ctx, Object msg) {
                        ByteBuf byteBuf = (ByteBuf) msg;
                        System.out.println(decode(byteBuf));
                        ctx.writeAndFlush(getByteBuf(ctx));
                    }

                    private String decode(ByteBuf byteBuf) {
                        int readableBytes = byteBuf.readableBytes();
                        byte[] bytes = new byte[readableBytes];
                        byteBuf.getBytes(byteBuf.readerIndex(), bytes);
                        return new String(bytes);
                    }

                    private ByteBuf getByteBuf(ChannelHandlerContext ctx) {
                        byte[] bytes = "server\r\n".getBytes(StandardCharsets.UTF_8);
                        ByteBuf buffer = ctx.alloc().buffer();
                        buffer.writeBytes(bytes);
                        return buffer;
                    }

                    @Override
                    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                        log.error(cause.getMessage(), cause);
                    }
                });
            }
        });
        serverBootstrap.bind(99);
    }


}

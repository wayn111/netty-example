package com.wayn.netty;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class NettyMysqlForwardApp {

    public static void main(String[] args) {


        // 服务端启动，监听3307端口，转发到3306端口
        ChannelHandlerContext[] ctxArr = {null};
        ServerBootstrap serverBootstrap = new ServerBootstrap();
        NioEventLoopGroup boss = new NioEventLoopGroup();
        NioEventLoopGroup worker = new NioEventLoopGroup();
        serverBootstrap.group(boss, worker);
        serverBootstrap.channel(NioServerSocketChannel.class);
        serverBootstrap.childHandler(new ChannelInitializer<NioSocketChannel>() {
            protected void initChannel(NioSocketChannel ch) {
                ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                    @Override
                    public void channelActive(ChannelHandlerContext clientCtx) {
                        // 3306第一次建立连接时，启动3307端口连接
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
                                                if (ctxArr[0] == null) {
                                                    ctxArr[0] = ctx;
                                                }
                                            }

                                            @Override
                                            public void channelRead(ChannelHandlerContext ctx, Object msg) {
                                                ByteBuf byteBuf = (ByteBuf) msg;
                                                byte[] bytes = new byte[byteBuf.readableBytes()];
                                                int index = byteBuf.readerIndex();
                                                byteBuf.getBytes(index, bytes);
                                                System.out.println(new String(bytes));
                                                clientCtx.writeAndFlush(byteBuf);
                                            }
                                        });


                                    }
                                });
                        Channel outboundChannel = bootstrap.connect("localhost", 28079).channel();
                        // bootstrap.connect("waynmysql.mysql.rds.aliyuncs.com", 3306).channel();
                    }

                    @Override
                    public void channelRead(ChannelHandlerContext clientCtx, Object msg) {
                        ByteBuf byteBuf = (ByteBuf) msg;
                        byte[] bytes = new byte[byteBuf.readableBytes()];
                        int index = byteBuf.readerIndex();
                        byteBuf.getBytes(index, bytes);
                        System.out.println(new String(bytes));
                        ctxArr[0].writeAndFlush(byteBuf);
                    }
                });
            }
        });
        serverBootstrap.bind(3307);
        log.info("server start up on 3307");
    }
}

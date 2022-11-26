package com.wayn.netty.heart.demp;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerDomainSocketChannel;
import io.netty.channel.unix.DomainSocketAddress;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

/**
 * tcp服务端
 */
@Slf4j
public class NettyUnixServer {

    // 绑定端口
    private static final int port = 99;

    // 维护channel列表，将用户信息和channel做绑定，一个channel代表一个tcp连接
    private static final Map<String, Object> channelMap = new HashMap<>();

    public static void main(String[] args) throws InterruptedException {
        // 创建服务端启动器
        ServerBootstrap serverBootstrap = new ServerBootstrap();
        // 指定连接接收线程，处理所有用户连接，转给工作线程
        EpollEventLoopGroup boss = null;
        // 指定工作线程，处理用户连接
        EpollEventLoopGroup worker = null;
        try {
            boss = new EpollEventLoopGroup();
            worker = new EpollEventLoopGroup();
            serverBootstrap.group(boss, worker); // 绑定线程组
            serverBootstrap.channel(EpollServerDomainSocketChannel.class);  // 底层使用nio处理
            ChannelFuture channelFuture = serverBootstrap.bind(new DomainSocketAddress(""))
                    .sync().addListener(future -> {
                        if (future.isSuccess()) {
                            log.info("server start up on {}", port);
                        }
                    });
            channelFuture.channel().closeFuture().sync();
        } finally {
            // 不要忘了在finally中关闭线程组
            if (boss != null) {
                boss.shutdownGracefully();
            }
            if (worker != null) {
                worker.shutdownGracefully();
            }
        }
    }

}


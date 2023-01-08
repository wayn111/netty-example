package com.wayn.netty.dns.udp;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.dns.*;
import io.netty.util.NetUtil;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;

@Slf4j
public final class DnsClient {

    public static void main(String[] args) throws Exception {
        try {
            EventLoopGroup boosGroup = new NioEventLoopGroup();
            Bootstrap b = new Bootstrap();
            b.group(boosGroup)
                    .channel(NioDatagramChannel.class)
                    .handler(new ChannelInitializer<DatagramChannel>() {
                        @Override
                        protected void initChannel(DatagramChannel ch) {
                            ChannelPipeline p = ch.pipeline();
                            // p.addLast(new LoggingHandler(LogLevel.INFO));
                            p.addLast(new DatagramDnsQueryEncoder())
                                    .addLast(new DatagramDnsResponseDecoder())
                                    .addLast(new SimpleChannelInboundHandler<DatagramDnsResponse>() {
                                        @Override
                                        public void channelActive(ChannelHandlerContext ctx) {
                                            log.info(ctx.channel().toString());
                                        }

                                        @Override
                                        protected void channelRead0(ChannelHandlerContext ctx, DatagramDnsResponse msg) {
                                            handleQueryResp(msg);

                                        }

                                        @Override
                                        public void exceptionCaught(ChannelHandlerContext ctx, Throwable e) {
                                            log.error(e.getMessage(), e);
                                        }
                                    });

                        }
                    });
            final Channel ch = b.bind(0).sync().addListener(future -> {
                log.info("绑定端口成功");
            }).channel();
            int count = 50000;
            List<CompletableFuture<Void>> list = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                CompletableFuture<Void> f1 = CompletableFuture.runAsync(() -> {
                    int randomID = new Random().nextInt(60000 - 1000) + 1000;
                    DnsQuery query = new DatagramDnsQuery(null, new InetSocketAddress("127.0.0.1`", 553), randomID).setRecord(
                            DnsSection.QUESTION,
                            new DefaultDnsQuestion("google.com", DnsRecordType.A));
                    try {
                        ch.writeAndFlush(query).sync();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                });
                list.add(f1);
            }
            CompletableFuture.allOf(list.toArray(new CompletableFuture[0])).join();
            ch.closeFuture().addListener((ChannelFutureListener) future -> {
                boosGroup.shutdownGracefully();
                log.info(future.channel().toString());
            });
        } catch (InterruptedException e) {
            log.error(e.getMessage(), e);
        }
    }

    private static void handleQueryResp(DatagramDnsResponse msg) {
        if (msg.count(DnsSection.QUESTION) > 0) {
            DnsQuestion question = msg.recordAt(DnsSection.QUESTION, 0);
            log.info("name: {}", question.name());
        }
        for (int i = 0, count = msg.count(DnsSection.ANSWER); i < count; i++) {
            DnsRecord record = msg.recordAt(DnsSection.ANSWER, i);
            if (record.type() == DnsRecordType.A) {
                // just print the IP after query
                DnsRawRecord raw = (DnsRawRecord) record;
                log.info(NetUtil.bytesToIpAddress(ByteBufUtil.getBytes(raw.content())));
            }
        }
    }
}

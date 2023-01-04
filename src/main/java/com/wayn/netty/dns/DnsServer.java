package com.wayn.netty.dns;

import com.wayn.netty.example03.chiyan.NettyChiyanApiForwardApp;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.dns.*;
import io.netty.util.NetUtil;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

public final class DnsServer {

    private static final byte[] QUERY_RESULT = new byte[]{(byte) 192, (byte) 168, 1, 1};

    public static void main(String[] args) throws Exception {
        final NioEventLoopGroup group = new NioEventLoopGroup();
        try {
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(group).channel(NioDatagramChannel.class)
                    .handler(new ChannelInitializer<NioDatagramChannel>() {
                        @Override
                        protected void initChannel(NioDatagramChannel nioDatagramChannel) {
                            nioDatagramChannel.pipeline().addLast(new DatagramDnsQueryDecoder());
                            nioDatagramChannel.pipeline().addLast(new DatagramDnsResponseEncoder());
                            nioDatagramChannel.pipeline().addLast(new SimpleChannelInboundHandler<DatagramDnsQuery>() {
                                @Override
                                protected void channelRead0(ChannelHandlerContext ctx, DatagramDnsQuery query) {
                                    // 假数据，域名和ip的对应关系应该放到数据库中
                                    DatagramDnsResponse response = new DatagramDnsResponse(query.recipient(), query.sender(), query.id());
                                    try {
                                        DefaultDnsQuestion dnsQuestion = query.recordAt(DnsSection.QUESTION);
                                        response.addRecord(DnsSection.QUESTION, dnsQuestion);
                                        DefaultDnsRawRecord queryAnswer = new DefaultDnsRawRecord(
                                                dnsQuestion.name(),
                                                DnsRecordType.A, 300, Unpooled.wrappedBuffer(QUERY_RESULT));
                                        response.addRecord(DnsSection.ANSWER, queryAnswer);
                                        ctx.writeAndFlush(response);
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                        System.out.println("异常了：" + e);
                                    }
                                }


                                @Override
                                public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                                    cause.printStackTrace();
                                    NettyChiyanApiForwardApp.closeOnFlush(ctx.channel());
                                }
                            });
                        }
                    }).option(ChannelOption.SO_BROADCAST, true);

            ChannelFuture future = bootstrap.bind(53).sync();
            future.channel().closeFuture().sync();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            group.shutdownGracefully();
        }
    }

}

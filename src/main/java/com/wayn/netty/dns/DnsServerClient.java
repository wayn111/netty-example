package com.wayn.netty.dns;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.dns.*;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;

@Slf4j
public final class DnsServerClient {

    private static final byte[] QUERY_RESULT = new byte[]{(byte) 192, (byte) 168, 1, 1};
    private static final int DNS_SERVER_PORT = 53;
    private static final String DNS_SERVER_HOST = "114.114.114.114";
    static InetSocketAddress addr = new InetSocketAddress(DNS_SERVER_HOST, DNS_SERVER_PORT);
    static InetSocketAddress localAddr = new InetSocketAddress("127.0.0.1", DNS_SERVER_PORT);

    static Channel proxyChannel;
    static Channel localChannel;

    public static void main(String[] args) throws Exception {
        final NioEventLoopGroup group = new NioEventLoopGroup();
        try {
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(group).channel(NioDatagramChannel.class)
                    .handler(new ChannelInitializer<NioDatagramChannel>() {
                        @Override
                        protected void initChannel(NioDatagramChannel nioDatagramChannel) {
                            nioDatagramChannel.pipeline().addLast(new DatagramDnsQueryDecoder());
                            nioDatagramChannel.pipeline().addLast(new SimpleChannelInboundHandler<DatagramDnsQuery>() {
                                @Override
                                public void channelActive(ChannelHandlerContext ctx) {
                                    localChannel = ctx.channel();
                                }

                                @Override
                                protected void channelRead0(ChannelHandlerContext ctx, DatagramDnsQuery msg) {
                                    // 假数据，域名和ip的对应关系应该放到数据库中
                                    try {

                                        DefaultDnsQuestion dnsQuestion = msg.recordAt(DnsSection.QUESTION);
                                        String name = dnsQuestion.name();
                                        DnsQuery query = new DatagramDnsQuery(null, addr, 1).setRecord(
                                                DnsSection.QUESTION,
                                                new DefaultDnsQuestion(name, DnsRecordType.A));
                                        proxyChannel.writeAndFlush(query);

                                        // DnsQuestion question = msg.recordAt(DnsSection.QUESTION);
                                        // DatagramDnsResponse dnsResponse = new DatagramDnsResponse(msg.recipient(), msg.sender(), msg.id());
                                        // dnsResponse.addRecord(DnsSection.QUESTION, question);
                                        //
                                        // for (int i = 0, count = msg.count(DnsSection.ANSWER); i < count; i++) {
                                        //     DnsRecord record = msg.recordAt(DnsSection.ANSWER, i);
                                        //     if (record.type() == DnsRecordType.A) {
                                        //         // just print the IP after query
                                        //         DefaultDnsRawRecord queryAnswer = new DefaultDnsRawRecord(
                                        //                 question.name(),
                                        //                 DnsRecordType.A, 600, Unpooled.wrappedBuffer(new byte[]{(byte) 192, (byte) 168, 1, 1}));
                                        //         dnsResponse.addRecord(DnsSection.ANSWER, queryAnswer);
                                        //     }
                                        // }
                                        // ctx.channel().writeAndFlush(dnsResponse);
                                    } catch (Exception e) {
                                        log.error(e.getMessage(), e);
                                    }
                                }

                                @Override
                                public void exceptionCaught(ChannelHandlerContext ctx, Throwable e) {
                                    log.error(e.getMessage(), e);
                                }
                            });
                            nioDatagramChannel.pipeline().addLast(new DatagramDnsResponseEncoder());

                        }
                    }).option(ChannelOption.SO_BROADCAST, true);

            ChannelFuture future = bootstrap.bind(553).sync();
            EventLoopGroup proxyGroup = new NioEventLoopGroup();
            Bootstrap b = new Bootstrap();
            b.group(proxyGroup)
                    .channel(NioDatagramChannel.class)
                    .handler(new ChannelInitializer<DatagramChannel>() {
                        @Override
                        protected void initChannel(DatagramChannel ch) throws Exception {
                            ChannelPipeline p = ch.pipeline();
                            p.addLast(new DatagramDnsQueryEncoder())
                                    .addLast(new DatagramDnsResponseDecoder())
                                    .addLast(new SimpleChannelInboundHandler<DatagramDnsResponse>() {
                                        @Override
                                        public void channelActive(ChannelHandlerContext ctx) throws Exception {
                                        }

                                        @Override
                                        protected void channelRead0(ChannelHandlerContext ctx, DatagramDnsResponse msg) {
                                            DnsQuestion question = msg.recordAt(DnsSection.QUESTION);
                                            DatagramDnsResponse dnsResponse = new DatagramDnsResponse(msg.sender(), msg.recipient(), msg.id());
                                            dnsResponse.addRecord(DnsSection.QUESTION, question);

                                            for (int i = 0, count = msg.count(DnsSection.ANSWER); i < count; i++) {
                                                DnsRecord record = msg.recordAt(DnsSection.ANSWER, i);
                                                if (record.type() == DnsRecordType.A) {
                                                    // just print the IP after query
                                                    DnsRawRecord raw = (DnsRawRecord) record;
                                                    DefaultDnsRawRecord queryAnswer = new DefaultDnsRawRecord(
                                                            question.name(),
                                                            DnsRecordType.A, 600, Unpooled.wrappedBuffer(ByteBufUtil.getBytes(raw.content())));
                                                    dnsResponse.addRecord(DnsSection.ANSWER, queryAnswer);
                                                }
                                            }
                                            localChannel.writeAndFlush(dnsResponse);
                                        }

                                        @Override
                                        public void exceptionCaught(ChannelHandlerContext ctx, Throwable e) {
                                            log.error(e.getMessage(), e);
                                        }
                                    });

                        }
                    });
            proxyChannel = b.bind(0).sync().channel();
            future.channel().closeFuture().sync();
        } catch (InterruptedException e) {
            log.error(e.getMessage(), e);
        }
    }
}

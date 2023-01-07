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
import java.util.Arrays;
import java.util.List;

import static java.util.stream.Collectors.toList;

@Slf4j
public final class DnsServer {

    private static final String DNS_SERVER_HOST = "114.114.114.114";

    private static final List<String> BLACK_LIST_DOMAIN = Arrays.stream(new String[]{
            "mgr.chiyanjiasu.com."
    }).collect(toList());

    static InetSocketAddress addr = new InetSocketAddress(DNS_SERVER_HOST, 53);
    static InetSocketAddress sender;
    static InetSocketAddress recipient;
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
                                protected void channelRead0(ChannelHandlerContext ctx, DatagramDnsQuery msg) {
                                    localChannel = ctx.channel();
                                    // 假数据，域名和ip的对应关系应该放到数据库中
                                    try {
                                        sender = msg.sender();
                                        recipient = msg.recipient();

                                        DefaultDnsQuestion dnsQuestion = msg.recordAt(DnsSection.QUESTION);
                                        String name = dnsQuestion.name();
                                        log.info(name);

                                        if (BLACK_LIST_DOMAIN.contains(name)) {
                                            DnsQuestion question = msg.recordAt(DnsSection.QUESTION);
                                            DatagramDnsResponse dnsResponse = new DatagramDnsResponse(msg.recipient(), msg.sender(), msg.id());
                                            dnsResponse.addRecord(DnsSection.QUESTION, question);

                                            // just print the IP after query
                                            DefaultDnsRawRecord queryAnswer = new DefaultDnsRawRecord(
                                                    question.name(),
                                                    DnsRecordType.A, 600, Unpooled.wrappedBuffer(new byte[]{(byte) 192, (byte) 168, 1, 1}));
                                            dnsResponse.addRecord(DnsSection.ANSWER, queryAnswer);
                                            localChannel.writeAndFlush(dnsResponse);
                                            return;
                                        }
                                        DnsQuery query = new DatagramDnsQuery(null, addr, msg.id()).setRecord(
                                                DnsSection.QUESTION,
                                                new DefaultDnsQuestion(name, DnsRecordType.A));
                                        proxyChannel.writeAndFlush(query);
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

            ChannelFuture future = bootstrap.bind(53).sync();
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
                                        public void channelActive(ChannelHandlerContext ctx) {
                                        }

                                        @Override
                                        protected void channelRead0(ChannelHandlerContext ctx, DatagramDnsResponse msg) {
                                            DnsQuestion question = msg.recordAt(DnsSection.QUESTION);
                                            DatagramDnsResponse dnsResponse = new DatagramDnsResponse(recipient, sender, msg.id());
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
            future.channel().closeFuture().addListener(future1 -> {
                if (future.isSuccess()) {
                    log.info(future.channel().toString());
                }
            });
        } catch (InterruptedException e) {
            log.error(e.getMessage(), e);
        }
    }
}

package com.wayn.netty.dns;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.dns.*;
import io.netty.util.NetUtil;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

public final class DnsClient {

    private static final String QUERY_DOMAIN = "api.chiyanjiasu.com";
    private static final int DNS_SERVER_PORT = 53;
    private static final String DNS_SERVER_HOST = "114.114.114.114";
    private static final byte[] QUERY_RESULT = new byte[]{(byte) 192, (byte) 168, 1, 1};

    private DnsClient() {
    }

    private static void handleQueryResp(DatagramDnsResponse msg) {
        if (msg.count(DnsSection.QUESTION) > 0) {
            DnsQuestion question = msg.recordAt(DnsSection.QUESTION, 0);
            System.out.printf("name: %s%n", question.name());
        }
        for (int i = 0, count = msg.count(DnsSection.ANSWER); i < count; i++) {
            DnsRecord record = msg.recordAt(DnsSection.ANSWER, i);
            if (record.type() == DnsRecordType.A) {
                // just print the IP after query
                DnsRawRecord raw = (DnsRawRecord) record;
                System.out.println(NetUtil.bytesToIpAddress(ByteBufUtil.getBytes(raw.content())));
            }
        }
    }

    public static void main(String[] args) throws Exception {
        InetSocketAddress addr = new InetSocketAddress(DNS_SERVER_HOST, DNS_SERVER_PORT);
        EventLoopGroup group = new NioEventLoopGroup();
        try {
            Bootstrap b = new Bootstrap();
            b.group(group)
                    .channel(NioDatagramChannel.class)
                    .handler(new ChannelInitializer<DatagramChannel>() {
                        @Override
                        protected void initChannel(DatagramChannel ch) throws Exception {
                            ChannelPipeline p = ch.pipeline();
                            p.addLast(new DatagramDnsQueryEncoder())
                                    .addLast(new DatagramDnsResponseDecoder())
                                    .addLast(new SimpleChannelInboundHandler<DatagramDnsResponse>() {
                                        @Override
                                        protected void channelRead0(ChannelHandlerContext ctx, DatagramDnsResponse msg) {
                                            try {
                                                handleQueryResp(msg);

                                                // // 假数据，域名和ip的对应关系应该放到数据库中
                                                // DatagramDnsResponse response = new DatagramDnsResponse(msg.sender(), msg.recipient(), msg.id());
                                                // try {
                                                //     DnsQuestion dnsQuestion = msg.recordAt(DnsSection.QUESTION);
                                                //     response.addRecord(DnsSection.QUESTION, dnsQuestion);
                                                //     DefaultDnsRawRecord queryAnswer = new DefaultDnsRawRecord(
                                                //             dnsQuestion.name(),
                                                //             DnsRecordType.A, 300, Unpooled.wrappedBuffer(QUERY_RESULT));
                                                //     response.addRecord(DnsSection.ANSWER, queryAnswer);
                                                //     ctx.writeAndFlush(response);
                                                // } catch (Exception e) {
                                                //     e.printStackTrace();
                                                //     System.out.println("异常了：" + e);
                                                // }
                                            } finally {
                                                ctx.close();
                                            }
                                        }
                                    });
                        }
                    });
            final Channel ch = b.bind(0).sync().channel();
            DnsQuery query = new DatagramDnsQuery(null, addr, 1).setRecord(
                    DnsSection.QUESTION,
                    new DefaultDnsQuestion(QUERY_DOMAIN, DnsRecordType.A));
            ch.writeAndFlush(query).sync();
            boolean succ = ch.closeFuture().await(10, TimeUnit.SECONDS);
            if (!succ) {
                System.err.println("dns query timeout!");
                ch.close().sync();
            }
        } finally {
            group.shutdownGracefully();
        }
    }
}

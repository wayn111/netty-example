package com.wayn.netty.dns;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.dns.*;
import io.netty.util.AttributeKey;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.List;

import static java.util.stream.Collectors.toList;

@Slf4j
public final class DnsServer2 {

    private static final List<String> BLACK_LIST_DOMAIN = Arrays.stream(new String[]{
            "mgr.chiyanjiasu.com."
    }).collect(toList());


    public static void main(String[] args) throws Exception {
        ProxyUdp2 proxyUdp2 = new ProxyUdp2();
        proxyUdp2.init();
        final int[] num = {0};

        final NioEventLoopGroup group = new NioEventLoopGroup();
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(group).channel(NioDatagramChannel.class)
                .handler(new ChannelInitializer<NioDatagramChannel>() {
                    @Override
                    protected void initChannel(NioDatagramChannel nioDatagramChannel) {
                        nioDatagramChannel.pipeline().addLast(new DatagramDnsQueryDecoder());
                        nioDatagramChannel.pipeline().addLast(new SimpleChannelInboundHandler<DatagramDnsQuery>() {

                            @Override
                            protected void channelRead0(ChannelHandlerContext ctx, DatagramDnsQuery msg) {
                                // 假数据，域名和ip的对应关系应该放到数据库中
                                try {
                                    DefaultDnsQuestion dnsQuestion = msg.recordAt(DnsSection.QUESTION);
                                    String name = dnsQuestion.name();
                                    log.info(name + ++num[0]);

                                    Channel channel = ctx.channel();
                                    int id = msg.id();
                                    channel.attr(AttributeKey.<DatagramDnsQuery>valueOf(String.valueOf(id))).set(msg);
                                    if (BLACK_LIST_DOMAIN.contains(name)) {
                                        DnsQuestion question = msg.recordAt(DnsSection.QUESTION);
                                        DatagramDnsResponse dnsResponse = new DatagramDnsResponse(msg.recipient(), msg.sender(), id);
                                        dnsResponse.addRecord(DnsSection.QUESTION, question);

                                        // just print the IP after query
                                        DefaultDnsRawRecord queryAnswer = new DefaultDnsRawRecord(
                                                question.name(),
                                                DnsRecordType.A, 600, Unpooled.wrappedBuffer(new byte[]{(byte) 192, (byte) 168, 1, 1}));
                                        dnsResponse.addRecord(DnsSection.ANSWER, queryAnswer);
                                        channel.writeAndFlush(dnsResponse);
                                        return;
                                    }
                                    proxyUdp2.send(name, msg.id(), channel);
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

        int port = 53;
        ChannelFuture future = bootstrap.bind(port).addListener(future1 -> {
            log.info("server listening port:{}", port);
        });

        future.channel().closeFuture().addListener(future1 -> {
            if (future.isSuccess()) {
                log.info(future.channel().toString());
            }
        });
    }
}

@Slf4j
class ProxyUdp2 {

    private Channel localChannel;
    private Channel proxyChannel;


    public void init() throws InterruptedException {
        EventLoopGroup proxyGroup = new NioEventLoopGroup();
        Bootstrap b = new Bootstrap();
        b.group(proxyGroup)
                .channel(NioDatagramChannel.class)
                .handler(new ChannelInitializer<DatagramChannel>() {
                    @Override
                    protected void initChannel(DatagramChannel ch) {
                        ChannelPipeline p = ch.pipeline();
                        p.addLast(new DatagramDnsQueryEncoder())
                                .addLast(new DatagramDnsResponseDecoder())
                                .addLast(new SimpleChannelInboundHandler<DatagramDnsResponse>() {
                                    @Override
                                    public void channelActive(ChannelHandlerContext ctx) {
                                        log.info(ctx.channel().toString());
                                    }

                                    @Override
                                    protected void channelRead0(ChannelHandlerContext ctx, DatagramDnsResponse msg) {
                                        DatagramDnsQuery dnsQuery = localChannel.attr(AttributeKey.<DatagramDnsQuery>valueOf(String.valueOf(msg.id()))).get();
                                        DnsQuestion question = msg.recordAt(DnsSection.QUESTION);
                                        DatagramDnsResponse dnsResponse = new DatagramDnsResponse(dnsQuery.recipient(), dnsQuery.sender(), msg.id());
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
        proxyChannel = b.bind(0).sync().addListener(future1 -> {
            log.info("绑定成功");
        }).channel();
    }

    public void send(String domain, int id, Channel localChannel) {
        this.localChannel = localChannel;
        DnsQuery query = new DatagramDnsQuery(null, new InetSocketAddress("114.114.114.114", 53), id).setRecord(
                DnsSection.QUESTION,
                new DefaultDnsQuestion(domain, DnsRecordType.A));
        this.proxyChannel.writeAndFlush(query);
    }

}

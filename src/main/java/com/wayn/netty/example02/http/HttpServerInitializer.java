package com.wayn.netty.example02.http;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;

public class HttpServerInitializer extends ChannelInitializer<SocketChannel> {

    @Override
    protected void initChannel(SocketChannel socketChannel) throws Exception {
        ChannelPipeline pipeline = socketChannel.pipeline();
        // http编解码
        pipeline.addLast(new HttpServerCodec());
        // http 消息聚合器
        pipeline.addLast("httpAggregator", new HttpObjectAggregator(512 * 1024));

        pipeline.addLast(new HttpRequestHandler());
    }
}

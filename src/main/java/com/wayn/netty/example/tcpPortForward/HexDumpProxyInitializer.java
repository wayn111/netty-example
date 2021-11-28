package com.wayn.netty.example.tcpPortForward;

import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;

public class HexDumpProxyInitializer extends ChannelInitializer {
    private String remoteHost;

    private Integer remotePort;

    public HexDumpProxyInitializer(String remoteHost, Integer remotePort) {
        this.remoteHost = remoteHost;
        this.remotePort = remotePort;
    }

    @Override
    protected void initChannel(Channel ch) {
        ch.pipeline().addLast(new LoggingHandler(LogLevel.INFO),
                new HexDumpProxyFrontendHandler(remoteHost, remotePort));

    }
}

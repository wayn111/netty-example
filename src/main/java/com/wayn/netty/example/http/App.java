package com.wayn.netty.example.http;

import lombok.extern.slf4j.Slf4j;

/**
 * Hello world!
 */
@Slf4j
public class App {
    public static void main(String[] args) throws InterruptedException {
        log.debug("启动netty http");
        HttpServer httpServer = new HttpServer(99);
        httpServer.start();
    }
}

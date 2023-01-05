package com.wayn.netty.dns;

import io.netty.util.internal.SocketUtils;

import java.net.UnknownHostException;

public class Test {

    public static void main(String[] args) throws UnknownHostException {
        System.out.println(SocketUtils.addressByName("api.test.chiyanjiasu.com"));
    }
}

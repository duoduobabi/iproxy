package org.cuiyang.iproxy;

public class ProxyServerTest {

    public static void main(String[] args) {
        ProxyServer proxyServer = new ProxyServer(ProxyConfig.builder()
                .build());
        proxyServer.start();
        Runtime.getRuntime().addShutdownHook(new Thread(proxyServer::stop));
    }
}

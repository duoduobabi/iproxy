package org.cuiyang.iproxy;

import org.cuiyang.iproxy.mitm.Authority;
import org.cuiyang.iproxy.mitm.DefaultMitmManagerImpl;

public class ProxyServerTest {

    public static void main(String[] args) {
        ProxyServer proxyServer = new ProxyServer(ProxyConfig.builder()
//                .proxyFactory(type -> Proxy.createHttpProxy("127.0.0.1", 8888))
                .mitmManager(new DefaultMitmManagerImpl(Authority.builder().build()))
                .build());
        proxyServer.start();
        Runtime.getRuntime().addShutdownHook(new Thread(proxyServer::stop));
    }
}

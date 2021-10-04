package org.cuiyang.iproxy;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.socksx.v5.Socks5CommandRequest;
import lombok.extern.slf4j.Slf4j;
import org.cuiyang.iproxy.mitm.Authority;
import org.cuiyang.iproxy.mitm.DefaultMitmManagerImpl;

@Slf4j
public class ProxyServerTest {

    public static void main(String[] args) {
        ProxyServer proxyServer = new ProxyServer(ProxyConfig.builder()
//                .proxyFactory(type -> Proxy.createHttpProxy("127.0.0.1", 8888))
                .proxyAuthenticator((username, password) -> "admin".equals(username) && "123456".equals(password))
                .mitmManager(new DefaultMitmManagerImpl(Authority.builder().build()))
                .interceptor((connection, msg) -> {
                    if (msg instanceof HttpRequest) {
                        HttpRequest request = (HttpRequest) msg;
                        if (connection.isSsl()) {
                            log.info("{} {} {} {}", connection.getId(), request.method(), "https://" + request.headers().get(HttpHeaderNames.HOST) + request.uri(), request.protocolVersion());
                        } else {
                            log.info("{} {} {} {}", connection.getId(), request.method(), request.uri(), request.protocolVersion());
                        }
                    } else if (msg instanceof HttpResponse) {
                        HttpResponse response = (HttpResponse) msg;
                        log.info("{} {}", connection.getId(), response.status());
                    } else if (msg instanceof Socks5CommandRequest) {
                        Socks5CommandRequest request = (Socks5CommandRequest) msg;
                        log.info("{} Socks5 {}:{}", connection.getId(), request.dstAddr(), request.dstPort());
                    }
                    return msg;
                })
                .build());
        proxyServer.start();
        Runtime.getRuntime().addShutdownHook(new Thread(proxyServer::stop));
    }
}

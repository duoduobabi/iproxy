package org.cuiyang.iproxy;

import org.cuiyang.iproxy.handler.*;
import org.cuiyang.iproxy.handler.http.HttpAuthHandler;
import org.cuiyang.iproxy.handler.http.HttpConnectHandler;
import org.cuiyang.iproxy.handler.socks.SocksAuthHandler;
import org.cuiyang.iproxy.handler.socks.SocksConnectHandler;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import lombok.Getter;
import lombok.SneakyThrows;

/**
 * 代理配置
 */
@Getter
public class ProxyConfig {

    private static final int DEFAULT_PORT = 8080;
    private static final int DEFAULT_CONNECT_TIMEOUT = 10000;
    private static final int DEFAULT_CONNECT_RETRY_TIMES = 0;

    private int port;
    private int connectTimeout;
    private int connectRetryTimes;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private ProxyAuthenticator proxyAuthenticator;
    private ProxyFactory proxyFactory;
    private ProxyTypeHandler proxyTypeHandler;
    private HttpAuthHandler httpAuthHandler;
    private HttpConnectHandler httpConnectHandler;
    private SocksAuthHandler socksAuthHandler;
    private SocksConnectHandler socksConnectHandler;

    @lombok.Builder(builderClassName = "Builder")
    public ProxyConfig(Integer port, Integer connectTimeout, Integer connectRetryTimes,
                       EventLoopGroup bossGroup, EventLoopGroup workerGroup,
                       ProxyAuthenticator proxyAuthenticator,
                       ProxyFactory proxyFactory,
                       ProxyTypeHandler proxyTypeHandler,
                       HttpAuthHandler httpAuthHandler,
                       HttpConnectHandler httpConnectHandler,
                       SocksAuthHandler socksAuthHandler,
                       SocksConnectHandler socksConnectHandler) {
        this.port = port == null ? DEFAULT_PORT : port;
        this.connectTimeout = connectTimeout == null ? DEFAULT_CONNECT_TIMEOUT : connectTimeout;
        this.connectRetryTimes = connectRetryTimes == null ? DEFAULT_CONNECT_RETRY_TIMES : connectRetryTimes;

        this.proxyAuthenticator = holdConfig(proxyAuthenticator);
        this.proxyFactory = holdConfig(proxyFactory);

        this.bossGroup = bossGroup == null ? new NioEventLoopGroup(1) : bossGroup;
        this.workerGroup = workerGroup == null ? new NioEventLoopGroup() : workerGroup;

        this.proxyTypeHandler = get(proxyTypeHandler, ProxyTypeHandler.class);
        this.httpAuthHandler = get(httpAuthHandler, HttpAuthHandler.class);
        this.httpConnectHandler = get(httpConnectHandler, HttpConnectHandler.class);
        this.socksAuthHandler = get(socksAuthHandler, SocksAuthHandler.class);
        this.socksConnectHandler = get(socksConnectHandler, SocksConnectHandler.class);
    }

    @SneakyThrows
    private <T> T get(T o, Class<T> clazz) {
        if (o == null) {
            return holdConfig(clazz.newInstance());
        } else {
            return holdConfig(o);
        }
    }

    private <T> T holdConfig(Object holder) {
        if (holder == null) {
            return null;
        }
        if (holder instanceof ProxyConfigHolder) {
            ((ProxyConfigHolder) holder).holdConfig(this);
        }
        //noinspection unchecked
        return (T) holder;
    }
}
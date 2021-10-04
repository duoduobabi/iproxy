package org.cuiyang.iproxy;

import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import lombok.Builder;
import lombok.Getter;
import lombok.SneakyThrows;
import org.cuiyang.iproxy.handler.ProxyTypeHandler;
import org.cuiyang.iproxy.handler.RelayHandler;
import org.cuiyang.iproxy.handler.http.HttpAuthHandler;
import org.cuiyang.iproxy.handler.http.HttpConnectHandler;
import org.cuiyang.iproxy.handler.http.HttpMitmConnectHandler;
import org.cuiyang.iproxy.handler.http.HttpTunnelConnectHandler;
import org.cuiyang.iproxy.handler.socks.SocksAuthHandler;
import org.cuiyang.iproxy.handler.socks.SocksConnectHandler;

/**
 * 代理配置
 */
@Getter
public class ProxyConfig {

    private static final int DEFAULT_PORT = 8080;
    private static final int DEFAULT_CONNECT_TIMEOUT = 1000;
    private static final int DEFAULT_CONNECT_RETRY_TIMES = 0;

    private int port;
    private int connectTimeout;
    private int connectRetryTimes;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private ProxyAuthenticator proxyAuthenticator;
    private ProxyFactory proxyFactory;
    private MitmManager mitmManager;
    private ProxyTypeHandler proxyTypeHandler;
    private HttpAuthHandler httpAuthHandler;
    private HttpConnectHandler httpConnectHandler;
    private HttpTunnelConnectHandler httpTunnelConnectHandler;
    private HttpMitmConnectHandler httpMitmConnectHandler;
    private SocksAuthHandler socksAuthHandler;
    private SocksConnectHandler socksConnectHandler;
    private Class<? extends RelayHandler> relayHandler;
    private Class<? extends Connection> connection;
    private Interceptor interceptor;

    @Builder
    public ProxyConfig(Integer port, Integer connectTimeout, Integer connectRetryTimes,
                       EventLoopGroup bossGroup, EventLoopGroup workerGroup,
                       ProxyAuthenticator proxyAuthenticator,
                       ProxyFactory proxyFactory,
                       MitmManager mitmManager,
                       ProxyTypeHandler proxyTypeHandler,
                       HttpAuthHandler httpAuthHandler,
                       HttpConnectHandler httpConnectHandler,
                       HttpTunnelConnectHandler httpTunnelConnectHandler,
                       HttpMitmConnectHandler httpMitmConnectHandler,
                       SocksAuthHandler socksAuthHandler,
                       SocksConnectHandler socksConnectHandler,
                       Class<? extends RelayHandler> relayHandler,
                       Class<? extends Connection> connection,
                       Interceptor interceptor) {
        this.port = port == null ? DEFAULT_PORT : port;
        this.connectTimeout = connectTimeout == null ? DEFAULT_CONNECT_TIMEOUT : connectTimeout;
        this.connectRetryTimes = connectRetryTimes == null ? DEFAULT_CONNECT_RETRY_TIMES : connectRetryTimes;

        this.proxyAuthenticator = holdConfig(proxyAuthenticator);
        this.proxyFactory = holdConfig(proxyFactory);
        this.mitmManager = holdConfig(mitmManager);

        this.bossGroup = bossGroup == null ? new NioEventLoopGroup(1) : bossGroup;
        this.workerGroup = workerGroup == null ? new NioEventLoopGroup() : workerGroup;

        this.proxyTypeHandler = get(proxyTypeHandler, ProxyTypeHandler.class);
        this.httpAuthHandler = get(httpAuthHandler, HttpAuthHandler.class);
        this.httpConnectHandler = get(httpConnectHandler, HttpConnectHandler.class);
        this.httpTunnelConnectHandler = get(httpTunnelConnectHandler, HttpTunnelConnectHandler.class);
        this.httpMitmConnectHandler = get(httpMitmConnectHandler, HttpMitmConnectHandler.class);
        this.socksAuthHandler = get(socksAuthHandler, SocksAuthHandler.class);
        this.socksConnectHandler = get(socksConnectHandler, SocksConnectHandler.class);

        this.relayHandler = relayHandler == null ? RelayHandler.class : relayHandler;
        this.connection = connection == null ? Connection.class : connection;
        this.interceptor = holdConfig(interceptor);
    }

    @SneakyThrows
    public Connection newConnection(Channel clientChannel) {
        Connection connection = this.connection.newInstance();
        connection.setClientChannel(clientChannel);
        connection.setMitm(this.getMitmManager() != null);
        connection.setConnectTimeout(this.getConnectTimeout());
        connection.setConnectRetryTimes(this.getConnectRetryTimes());
        return holdConfig(connection);
    }

    @SneakyThrows
    public RelayHandler newRelayHandler(Connection connection, Channel relayChannel) {
        RelayHandler relayHandler = this.relayHandler.newInstance();
        relayHandler.setConnection(connection);
        relayHandler.setRelayChannel(relayChannel);
        return holdConfig(relayHandler);
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

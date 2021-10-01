package org.cuiyang.iproxy.handler;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.socksx.v5.Socks5CommandRequest;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.Promise;
import lombok.extern.slf4j.Slf4j;
import org.cuiyang.iproxy.*;

import java.net.InetSocketAddress;

/**
 * 连接处理器基类
 *
 * @author cuiyang
 * @param <T>
 */
@Slf4j
public abstract class AbstractConnectHandler<T> extends SimpleChannelInboundHandler<T> implements ProxyConfigHolder {

    protected ProxyConfig config;

    @Override
    public void channelRead0(ChannelHandlerContext ctx, T request) {
        Connection connection = Connection.currentConnection(ctx);
        if (connection.getServerAddress() == null) {
            connection.setServerAddress(getTargetAddress(request));
        }
        Bootstrap bootstrap = bootstrap(connection, request);
        ChannelFuture connectFuture = connect(connection, request, bootstrap);
        handleConnectResult(ctx, connection, request, connectFuture);
    }

    /**
     * bootstrap
     */
    protected Bootstrap bootstrap(Connection connection, T request) {
        Bootstrap b = new Bootstrap();
        b.group(connection.eventLoop())
                .channel(NioSocketChannel.class)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connection.getConnectTimeout())
                .option(ChannelOption.SO_KEEPALIVE, true)
                .handler(handler(connection, request));
        return b;
    }

    /**
     * 处理器
     */
    protected ChannelHandler handler(Connection connection, T request) {
        Promise<Channel> promise = connection.newPromise();
        promise.addListener((FutureListener<Channel>) future -> {
            // 设置代理到服务端的Channel
            connection.setServerChannel(future.getNow());
            if (future.isSuccess()) {
                connectSuccess(connection, request);
            } else {
                connectFail(connection, request, future.cause());
            }
        });
        return new DirectClientHandler(promise);
    }

    /**
     * 建立连接
     */
    protected ChannelFuture connect(Connection connection, T request, Bootstrap bootstrap) {
        InetSocketAddress address;
        if (config.getProxyFactory() != null) {
            Proxy.Type type;
            if (request instanceof HttpRequest) {
                type = Proxy.Type.HTTP;
            } else {
                type = Proxy.Type.SOCKS;
            }
            Proxy proxy = config.getProxyFactory().getProxy(type);
            address = (InetSocketAddress) proxy.address();
        } else {
            address = connection.getServerAddress();
        }
        return bootstrap.connect(address);
    }

    /**
     * 处理连接结果
     */
    protected void handleConnectResult(ChannelHandlerContext ctx, Connection connection, T request, ChannelFuture connectFuture) {
        connectFuture.addListener(future -> {
            if (!future.isSuccess()) {
                // 连接失败重试
                int connectRetryTimes = connection.getConnectRetryTimes();
                if (connectRetryTimes > 0 && future.cause() instanceof ConnectTimeoutException) {
                    connection.setConnectRetryTimes(--connectRetryTimes);
                    this.channelRead0(ctx, request);
                } else {
                    connectFail(connection, request, future.cause());
                }
            }
        });
    }

    /**
     * 连接成功
     */
    protected void connectSuccess(Connection connection, T request) {
        connection.getClientPipeline().remove(this);
        connection.connect();
    }

    /**
     * 连接失败
     */
    protected void connectFail(Connection connection, T request, Throwable cause) {
        log.debug("连接失败", cause);
        connection.close();
    }

    /**
     * 目标地址
     */
    protected InetSocketAddress getTargetAddress(T msg) {
        if (msg instanceof HttpRequest) {
            HttpRequest request = (HttpRequest) msg;
            return ProxyServerUtils.httpAddress(request, 80);
        } else if (msg instanceof Socks5CommandRequest) {
            Socks5CommandRequest request = (Socks5CommandRequest) msg;
            return InetSocketAddress.createUnresolved(request.dstAddr(), request.dstPort());
        } else {
            throw new IllegalStateException("非法的消息 - " + msg.getClass());
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("连接处理器异常", cause);
        ProxyServerUtils.closeOnFlush(ctx.channel());
    }

    @Override
    public void holdConfig(ProxyConfig config) {
        this.config = config;
    }
}

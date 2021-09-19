package org.cuiyang.iproxy.handler;

import org.cuiyang.iproxy.*;
import org.cuiyang.iproxy.handler.http.HttpConnectHandler;
import org.cuiyang.iproxy.handler.socks.SocksConnectHandler;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.Attribute;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.Promise;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;

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
        ChannelFuture channelFuture = connect(ctx, request);
        handleConnectResult(ctx, request, channelFuture);
    }

    /**
     * 建立连接
     */
    protected ChannelFuture connect(ChannelHandlerContext ctx, T request) {
        Bootstrap b = bootstrap(ctx, request);

        InetSocketAddress address;
        if (config.getProxyFactory() != null) {
            Proxy.Type type = null;
            if (this instanceof HttpConnectHandler) {
                type = Proxy.Type.HTTP;
            } else if (this instanceof SocksConnectHandler) {
                type = Proxy.Type.SOCKS;
            }
            Proxy proxy = config.getProxyFactory().getProxy(type);
            address = (InetSocketAddress) proxy.address();
        } else {
            address = getTargetAddress(request);
        }
        return b.connect(address);
    }

    /**
     * 处理连接结果
     */
    protected void handleConnectResult(ChannelHandlerContext ctx, T request, ChannelFuture channelFuture) {
        Attribute<Integer> connectRetryTimes = ctx.channel().attr(Attributes.CONNECT_RETRY_TIMES);
        AtomicInteger times = new AtomicInteger();
        channelFuture.addListener(future -> {
            if (!future.isSuccess()) {
                log.debug("连接失败");
                if (times.getAndIncrement() < connectRetryTimes.get()) {
                    this.channelRead0(ctx, request);
                } else {
                    ProxyServerUtils.closeOnFlush(ctx.channel());
                }
            }
        });
    }

    /**
     * bootstrap
     */
    protected Bootstrap bootstrap(ChannelHandlerContext ctx, T request) {
        Attribute<Integer> connectTimeout = ctx.channel().attr(Attributes.CONNECT_TIMEOUT);
        Bootstrap b = new Bootstrap();
        b.group(ctx.channel().eventLoop())
                .channel(NioSocketChannel.class)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeout.get())
                .option(ChannelOption.SO_KEEPALIVE, true)
                .handler(handler(ctx, request));
        return b;
    }

    /**
     * 处理器
     */
    protected ChannelHandler handler(ChannelHandlerContext ctx, T request) {
        Promise<Channel> promise = ctx.executor().newPromise();
        promise.addListener(handleConnect(ctx, request));
        return new DirectClientHandler(promise);
    }

    /**
     * 处理连接
     */
    protected FutureListener<Channel> handleConnect(ChannelHandlerContext ctx, T request) {
        final Channel inboundChannel = ctx.channel();
        return future -> {
            final Channel outboundChannel = future.getNow();
            if (future.isSuccess()) {
                connectSuccess(ctx, request, inboundChannel, outboundChannel);
            } else {
                connectFail(ctx, request, inboundChannel, outboundChannel, future.cause());
            }
        };
    }

    /**
     * 连接成功
     */
    protected abstract void connectSuccess(ChannelHandlerContext ctx, T request,
                                           Channel inboundChannel, Channel outboundChannel);

    /**
     * 连接失败
     */
    protected abstract void connectFail(ChannelHandlerContext ctx, T request,
                                        Channel inboundChannel, Channel outboundChannel, Throwable cause);

    /**
     * 目标地址
     */
    protected abstract InetSocketAddress getTargetAddress(T request);

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("连接处理异常", cause);
        ProxyServerUtils.closeOnFlush(ctx.channel());
    }

    @Override
    public void holdConfig(ProxyConfig config) {
        this.config = config;
    }
}

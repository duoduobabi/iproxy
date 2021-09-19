package org.cuiyang.iproxy.handler.http;

import org.cuiyang.iproxy.ProxyServerUtils;
import org.cuiyang.iproxy.handler.AbstractConnectHandler;
import org.cuiyang.iproxy.handler.RelayHandler;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.net.InetSocketAddress;

/**
 * http连接处理器
 *
 * @author cuiyang
 */
@Slf4j
@ChannelHandler.Sharable
public class HttpConnectHandler extends AbstractConnectHandler<HttpRequest> {

    @Override
    public void channelRead0(ChannelHandlerContext ctx, HttpRequest request) {
        log.info("{} {} {}", request.method(), request.uri(), request.protocolVersion());
        super.channelRead0(ctx, request);
    }

    @Override
    protected void connectSuccess(ChannelHandlerContext ctx, HttpRequest request,
                                  Channel inboundChannel, Channel outboundChannel) {
        ChannelFuture responseFuture;
        if (request.method().equals(HttpMethod.CONNECT) && config.getProxyFactory() == null) {
            responseFuture = inboundChannel.writeAndFlush(new DefaultFullHttpResponse(request.protocolVersion(), HttpResponseStatus.OK));
        } else {
            ByteBuf byteBuf = ProxyServerUtils.http2byteBuf(request);
            responseFuture = outboundChannel.writeAndFlush(byteBuf);
        }
        responseFuture.addListener(channelFuture -> {
            if (!channelFuture.isSuccess()) {
                log.debug("Http请求失败", channelFuture.cause());
                ProxyServerUtils.closeOnFlush(inboundChannel);
                ProxyServerUtils.closeOnFlush(outboundChannel);
                return;
            }
            ctx.pipeline().remove(HttpConnectHandler.this);
            ctx.pipeline().remove(HttpServerCodec.class);
            outboundChannel.pipeline().addLast(new RelayHandler(inboundChannel));
            ctx.pipeline().addLast(new RelayHandler(outboundChannel));
        });
    }

    @Override
    protected void connectFail(ChannelHandlerContext ctx, HttpRequest request,
                               Channel inboundChannel, Channel outboundChannel, Throwable cause) {
        log.debug("Http响应失败", cause);
        ProxyServerUtils.closeOnFlush(inboundChannel);
        ProxyServerUtils.closeOnFlush(outboundChannel);
    }

    @Override
    protected InetSocketAddress getTargetAddress(HttpRequest request) {
        return getTargetAddress(request, 80);
    }

    protected InetSocketAddress getTargetAddress(HttpRequest request, int defaultPort) {
        String host;
        if (request.method().equals(HttpMethod.CONNECT)) {
            host = request.uri();
        } else {
            host = request.headers().get(HttpHeaderNames.HOST);
        }
        int port = defaultPort;
        if (host.contains(":")) {
            port = Integer.parseInt(StringUtils.substringAfter(host, ":"));
            host = StringUtils.substringBefore(host, ":");
        }
        return InetSocketAddress.createUnresolved(host, port);
    }
}

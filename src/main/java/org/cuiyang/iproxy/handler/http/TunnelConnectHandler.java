package org.cuiyang.iproxy.handler.http;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;
import lombok.extern.slf4j.Slf4j;
import org.cuiyang.iproxy.ProxyServerUtils;
import org.cuiyang.iproxy.handler.AbstractConnectHandler;
import org.cuiyang.iproxy.handler.RelayHandler;

import java.net.InetSocketAddress;

/**
 * http tunnel 连接处理器
 *
 * @author cuiyang
 */
@Slf4j
@ChannelHandler.Sharable
public class TunnelConnectHandler extends AbstractConnectHandler<HttpRequest> {

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
                ProxyServerUtils.closeOnFlush(inboundChannel, outboundChannel);
                return;
            }
            inboundChannel.pipeline().remove(TunnelConnectHandler.this);
            inboundChannel.pipeline().remove(HttpServerCodec.class);
            inboundChannel.pipeline().addLast(new RelayHandler(outboundChannel));
            outboundChannel.pipeline().addLast(new RelayHandler(inboundChannel));
        });
    }

    @Override
    protected void connectFail(ChannelHandlerContext ctx, HttpRequest request,
                               Channel inboundChannel, Channel outboundChannel, Throwable cause) {
        log.debug("HttpTunnel响应失败", cause);
        ProxyServerUtils.closeOnFlush(inboundChannel, outboundChannel);
    }

    @Override
    protected InetSocketAddress getTargetAddress(HttpRequest request) {
        return ProxyServerUtils.httpAddress(request, 80);
    }
}

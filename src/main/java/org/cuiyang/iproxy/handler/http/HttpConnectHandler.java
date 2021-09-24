package org.cuiyang.iproxy.handler.http;

import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpRequest;
import lombok.extern.slf4j.Slf4j;
import org.cuiyang.iproxy.ProxyServerUtils;
import org.cuiyang.iproxy.handler.AbstractConnectHandler;
import org.cuiyang.iproxy.handler.RelayHandler;

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
        ChannelFuture responseFuture = outboundChannel.writeAndFlush(request);
        responseFuture.addListener(channelFuture -> {
            if (!channelFuture.isSuccess()) {
                log.debug("Http请求失败", channelFuture.cause());
                ProxyServerUtils.closeOnFlush(inboundChannel, outboundChannel);
                return;
            }
            inboundChannel.pipeline().remove(HttpConnectHandler.this);
            inboundChannel.pipeline().addLast(new RelayHandler(outboundChannel));
            outboundChannel.pipeline().addLast(new RelayHandler(inboundChannel));
        });
    }

    @Override
    protected ChannelHandler handler(ChannelHandlerContext ctx, HttpRequest request) {
        return new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) {
                ch.pipeline().addLast(new HttpClientCodec());
                ch.pipeline().addLast(HttpConnectHandler.super.handler(ctx, request));
            }
        };
    }

    @Override
    protected void connectFail(ChannelHandlerContext ctx, HttpRequest request,
                               Channel inboundChannel, Channel outboundChannel, Throwable cause) {
        log.debug("Http响应失败", cause);
        ProxyServerUtils.closeOnFlush(inboundChannel, outboundChannel);
    }

    @Override
    protected InetSocketAddress getTargetAddress(HttpRequest request) {
        return ProxyServerUtils.httpAddress(request, 80);
    }
}

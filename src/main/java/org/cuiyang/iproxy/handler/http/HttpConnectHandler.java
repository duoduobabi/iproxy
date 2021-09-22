package org.cuiyang.iproxy.handler.http;

import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.util.concurrent.Promise;
import org.cuiyang.iproxy.ProxyServerUtils;
import org.cuiyang.iproxy.handler.AbstractConnectHandler;
import org.cuiyang.iproxy.handler.DirectClientHandler;
import org.cuiyang.iproxy.handler.RelayHandler;
import io.netty.handler.codec.http.*;
import lombok.extern.slf4j.Slf4j;

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
                ProxyServerUtils.closeOnFlush(inboundChannel);
                ProxyServerUtils.closeOnFlush(outboundChannel);
                return;
            }
            ctx.pipeline().remove(HttpConnectHandler.this);
            outboundChannel.pipeline().addLast(new RelayHandler(inboundChannel));
            ctx.pipeline().addLast(new RelayHandler(outboundChannel));
        });
    }

    @Override
    protected ChannelHandler handler(ChannelHandlerContext ctx, HttpRequest request) {
        if (request.method().equals(HttpMethod.CONNECT)) {
            return super.handler(ctx, request);
        } else {
            Promise<Channel> promise = ctx.executor().newPromise();
            promise.addListener(handleConnect(ctx, request));
            return new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel ch) {
                    ch.pipeline().addLast(new HttpClientCodec());
                    ch.pipeline().addLast(new DirectClientHandler(promise));
                }
            };
        }
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
        return ProxyServerUtils.httpAddress(request, 80);
    }
}

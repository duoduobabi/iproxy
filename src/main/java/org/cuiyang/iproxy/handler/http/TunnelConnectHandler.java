package org.cuiyang.iproxy.handler.http;

import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.Promise;
import lombok.extern.slf4j.Slf4j;
import org.cuiyang.iproxy.Connection;

/**
 * http tunnel 连接处理器
 *
 * @author cuiyang
 */
@Slf4j
@ChannelHandler.Sharable
public class TunnelConnectHandler extends HttpConnectHandler {

    @Override
    protected void connectSuccess(ChannelHandlerContext ctx, Connection connection, HttpObject msg) {
        if (config.getProxyFactory() == null) {
            connectSuccess0(ctx, connection, msg);
        } else {
            HttpRequest request = (HttpRequest) msg;
            Promise<Channel> promise = ctx.executor().newPromise();
            promise.addListener((FutureListener<Channel>) future -> {
                if (future.isSuccess()) {
                    connectSuccess0(ctx, connection, request);
                } else {
                    connectFail(ctx, connection, request, future.cause());
                }
            });
            connection.getOutboundPipeline().addLast(new HttpClientCodec());
            connection.getOutboundPipeline().addLast(new SimpleChannelInboundHandler<HttpResponse>() {
                @Override
                protected void channelRead0(ChannelHandlerContext ctx, HttpResponse msg) {
                    ctx.pipeline().remove(this);
                    if (HttpResponseStatus.OK.equals(msg.status())) {
                        connection.getOutboundPipeline().remove(HttpClientCodec.class);
                        promise.setSuccess(ctx.channel());
                    } else {
                        promise.setFailure(new IllegalStateException("Http Connect响应" + msg.status().code()));
                    }
                }

                @Override
                public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                    promise.setFailure(cause);
                }
            });
            connection.getOutboundPipeline().writeAndFlush(request);
        }
    }

    protected void connectSuccess0(ChannelHandlerContext ctx, Connection connection, HttpObject msg) {
        HttpRequest request = (HttpRequest) msg;
        ChannelFuture responseFuture = connection.getInboundChannel()
                .writeAndFlush(new DefaultFullHttpResponse(request.protocolVersion(), HttpResponseStatus.OK));
        responseFuture.addListener(f -> {
            if (!f.isSuccess()) {
                connectFail(ctx, connection, msg, f.cause());
                return;
            }
            connection.getInboundPipeline().remove(this);
            connection.getInboundPipeline().remove(HttpServerCodec.class);
            connection.connect();
        });
    }
}

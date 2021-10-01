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
public class HttpTunnelConnectHandler extends HttpConnectHandler {

    @Override
    protected void connectSuccess(Connection connection, HttpObject msg) {
        if (config.getProxyFactory() == null) {
            connectSuccess0(connection, msg);
        } else {
            HttpRequest request = (HttpRequest) msg;
            Promise<Channel> promise = connection.newPromise();
            promise.addListener((FutureListener<Channel>) future -> {
                if (future.isSuccess()) {
                    connectSuccess0(connection, request);
                } else {
                    connectFail(connection, request, future.cause());
                }
            });
            connection.getServerPipeline().addLast(new HttpClientCodec());
            connection.getServerPipeline().addLast(new SimpleChannelInboundHandler<HttpResponse>() {
                @Override
                protected void channelRead0(ChannelHandlerContext ctx, HttpResponse msg) {
                    ctx.pipeline().remove(this);
                    if (HttpResponseStatus.OK.equals(msg.status())) {
                        connection.getServerPipeline().remove(HttpClientCodec.class);
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
            connection.getServerPipeline().writeAndFlush(request);
        }
    }

    protected void connectSuccess0(Connection connection, HttpObject msg) {
        HttpRequest request = (HttpRequest) msg;
        ChannelFuture responseFuture = connection.getClientChannel()
                .writeAndFlush(new DefaultFullHttpResponse(request.protocolVersion(), HttpResponseStatus.OK));
        responseFuture.addListener(f -> {
            if (!f.isSuccess()) {
                connectFail(connection, msg, f.cause());
                return;
            }
            connection.getClientPipeline().remove(this);
            connection.getClientPipeline().remove(HttpServerCodec.class);
            connection.connect();
        });
    }
}

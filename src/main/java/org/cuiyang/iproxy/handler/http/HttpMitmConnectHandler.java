package org.cuiyang.iproxy.handler.http;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;
import io.netty.handler.ssl.SslHandler;
import lombok.extern.slf4j.Slf4j;
import org.cuiyang.iproxy.Connection;

import javax.net.ssl.SSLEngine;
import java.net.InetSocketAddress;

/**
 * HTTP MITM(Man In The Middle) 连接处理器
 *
 * @author cuiyang
 */
@Slf4j
@ChannelHandler.Sharable
public class HttpMitmConnectHandler extends TunnelConnectHandler {

    @Override
    protected void connectSuccess0(ChannelHandlerContext ctx, Connection connection, HttpObject msg) {
        InetSocketAddress serverAddress = connection.getServerAddress();
        SSLEngine sslEngine = config.getMitmManager().serverSslEngine(serverAddress.getHostName(), serverAddress.getPort());
        SslHandler sslHandler = new SslHandler(sslEngine);
        connection.getOutboundPipeline().addLast(sslHandler);
        connection.getOutboundPipeline().addLast(new HttpClientCodec());
        sslHandler.handshakeFuture().addListener(f -> {
            if (!f.isSuccess()) {
                log.debug("ssl握手失败", f.cause());
                connectFail(ctx, connection, msg, f.cause());
                return;
            }
            HttpRequest request = (HttpRequest) msg;
            ChannelFuture responseFuture = connection.getInboundChannel()
                    .writeAndFlush(new DefaultFullHttpResponse(request.protocolVersion(), HttpResponseStatus.OK));
            responseFuture.addListener(f2 -> {
                if (!f2.isSuccess()) {
                    connectFail(ctx, connection, msg, f2.cause());
                    return;
                }
                connection.getInboundPipeline().addFirst(new SslHandler(config.getMitmManager().clientSslEngineFor(request, sslEngine.getSession())));;
                connection.getInboundPipeline().remove(this);
                connection.connect();
            });
        });
    }
}

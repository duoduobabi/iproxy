package org.cuiyang.iproxy.handler.http;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
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
public class HttpMitmConnectHandler extends HttpTunnelConnectHandler {

    @Override
    protected void connectSuccess0(Connection connection, HttpObject msg) {
        InetSocketAddress serverAddress = connection.getServerAddress();
        SSLEngine sslEngine = config.getMitmManager().serverSslEngine(serverAddress.getHostName(), serverAddress.getPort());
        SslHandler sslHandler = new SslHandler(sslEngine);
        connection.getServerPipeline().addLast(sslHandler);
        connection.getServerPipeline().addLast(new HttpClientCodec());
        sslHandler.handshakeFuture().addListener(f -> {
            if (!f.isSuccess()) {
                log.debug("ssl握手失败", f.cause());
                connectFail(connection, msg, f.cause());
                return;
            }
            HttpRequest request = (HttpRequest) msg;
            ChannelFuture responseFuture = connection.getClientChannel()
                    .writeAndFlush(new DefaultFullHttpResponse(request.protocolVersion(), HttpResponseStatus.OK));
            responseFuture.addListener(f2 -> {
                if (!f2.isSuccess()) {
                    connectFail(connection, msg, f2.cause());
                    return;
                }
                connection.getClientPipeline().addFirst(new SslHandler(config.getMitmManager().clientSslEngineFor(request, sslEngine.getSession())));;
                connection.getClientPipeline().remove(this);
                connection.connect();
            });
        });
    }
}

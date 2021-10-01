package org.cuiyang.iproxy.handler.http;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.cuiyang.iproxy.Connection;
import org.cuiyang.iproxy.handler.AbstractConnectHandler;

/**
 * http连接处理器
 *
 * @author cuiyang
 */
@Slf4j
@ChannelHandler.Sharable
public class HttpConnectHandler extends AbstractConnectHandler<HttpObject> {

    @SneakyThrows
    @Override
    public void channelRead0(ChannelHandlerContext ctx, HttpObject msg) {
        Connection connection = Connection.currentConnection(ctx);
        connection.write(msg);
        if (msg instanceof HttpRequest) {
            HttpRequest request = (HttpRequest) msg;
            log.info("{} {} {}", request.method(), request.uri(), request.protocolVersion());
            super.channelRead0(ctx, request);
        }
    }

    @Override
    protected void connectSuccess(Connection connection, HttpObject request) {
        super.connectSuccess(connection, request);
        connection.getServerPipeline().addFirst(new HttpClientCodec());
        connection.flush();
    }
}

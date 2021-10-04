package org.cuiyang.iproxy.handler.http;

import io.netty.channel.ChannelPipeline;
import org.cuiyang.iproxy.Connection;
import org.cuiyang.iproxy.handler.AbstractAuthHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Http认证请求处理器
 *
 * @author cuiyang
 */
@Slf4j
@ChannelHandler.Sharable
public class HttpAuthHandler extends AbstractAuthHandler<HttpRequest> {

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, HttpRequest request) {
        String username = null;
        String password = null;
        String auth = request.headers().get(HttpHeaderNames.PROXY_AUTHORIZATION);
        if (config.getProxyAuthenticator() != null && StringUtils.isNotEmpty(auth)) {
            try {
                auth = StringUtils.substringAfter(auth, "Basic ").trim();
                byte[] decodedValue = Base64.getDecoder().decode(auth);
                String decodedString = new String(decodedValue, StandardCharsets.UTF_8);
                username = StringUtils.substringBefore(decodedString, ":");
                password = StringUtils.substringAfter(decodedString, ":");
            } catch (Exception e) {
                log.debug("解析Http Basic认证失败", e);
            }
        }
        Connection connection = Connection.currentConnection(ctx);
        if (authenticate(connection, username, password)) {
            authenticateSuccess(connection, request);
        } else {
            authenticateFail(connection, request);
        }
    }

    @Override
    protected void authenticateSuccess(Connection connection, HttpRequest request) {
        HttpHeaders headers = request.headers();
        headers.remove(HttpHeaderNames.PROXY_AUTHORIZATION);
        String proxyConnectionKey = "Proxy-Connection";
        if (headers.contains(proxyConnectionKey)) {
            String header = headers.get(proxyConnectionKey);
            headers.remove(proxyConnectionKey);
            headers.set(HttpHeaderNames.CONNECTION, header);
        }
        ChannelPipeline pipeline = connection.getClientPipeline();
        if (request.method().equals(HttpMethod.CONNECT)) {
            if (connection.isMitm()) {
                pipeline.addLast(config.getHttpMitmConnectHandler());
            } else {
                pipeline.addLast(config.getHttpTunnelConnectHandler());
            }
        } else {
            pipeline.addLast(config.getHttpConnectHandler());
        }
        pipeline.remove(this);
        pipeline.fireChannelRead(request);
    }

    @Override
    protected void authenticateFail(Connection connection, HttpRequest request) {
        FullHttpResponse response = new DefaultFullHttpResponse(request.protocolVersion(),
                HttpResponseStatus.PROXY_AUTHENTICATION_REQUIRED);
        response.headers().set(HttpHeaderNames.PROXY_AUTHENTICATE, "Basic");
        connection.response(response);
    }
}

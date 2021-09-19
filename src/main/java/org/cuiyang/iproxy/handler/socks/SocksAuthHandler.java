package org.cuiyang.iproxy.handler.socks;

import org.cuiyang.iproxy.ProxyServerUtils;
import org.cuiyang.iproxy.handler.AbstractAuthHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.socksx.SocksMessage;
import io.netty.handler.codec.socksx.v5.*;
import lombok.extern.slf4j.Slf4j;

/**
 * Socks认证处理器
 *
 * @author cuiyang
 */
@Slf4j
@ChannelHandler.Sharable
public class SocksAuthHandler extends AbstractAuthHandler<SocksMessage> {

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, SocksMessage socksRequest) {
        switch (socksRequest.version()) {
            case SOCKS4a:
                log.warn("only supports socks5 protocol!");
                ProxyServerUtils.closeOnFlush(ctx.channel());
                return;
            case SOCKS5:
                if (socksRequest instanceof Socks5InitialRequest) {
                    if (config.getProxyAuthenticator() == null) {
                        ctx.pipeline().addFirst(new Socks5CommandRequestDecoder());
                        ctx.writeAndFlush(new DefaultSocks5InitialResponse(Socks5AuthMethod.NO_AUTH));
                    } else {
                        ctx.pipeline().addFirst(new Socks5PasswordAuthRequestDecoder());
                        ctx.writeAndFlush(new DefaultSocks5InitialResponse(Socks5AuthMethod.PASSWORD));
                    }
                } else if (socksRequest instanceof Socks5PasswordAuthRequest) {
                    DefaultSocks5PasswordAuthRequest request = (DefaultSocks5PasswordAuthRequest) socksRequest;
                    if (authenticate(request.username(), request.password())) {
                        setAttributes(ctx, request, request.username());
                        ctx.pipeline().addFirst(new Socks5CommandRequestDecoder());
                        ctx.writeAndFlush(new DefaultSocks5PasswordAuthResponse(Socks5PasswordAuthStatus.SUCCESS));
                    } else {
                        authenticateFail(ctx, socksRequest);
                    }
                } else if (socksRequest instanceof Socks5CommandRequest) {
                    Socks5CommandRequest request = (Socks5CommandRequest) socksRequest;
                    if (request.type() == Socks5CommandType.CONNECT) {
                        authenticateSuccess(ctx, request);
                    } else {
                        ProxyServerUtils.closeOnFlush(ctx.channel());
                    }
                } else {
                    ProxyServerUtils.closeOnFlush(ctx.channel());
                }
                break;
            case UNKNOWN:
                log.warn("unknown socks protocol!");
                ProxyServerUtils.closeOnFlush(ctx.channel());
                break;
        }
    }

    @Override
    protected void authenticateSuccess(ChannelHandlerContext ctx, SocksMessage request) {
        ctx.pipeline().addLast(config.getSocksConnectHandler());
        ctx.pipeline().remove(this);
        ctx.fireChannelRead(request);
    }

    @Override
    protected void authenticateFail(ChannelHandlerContext ctx, SocksMessage request) {
        ctx.writeAndFlush(new DefaultSocks5PasswordAuthResponse(Socks5PasswordAuthStatus.FAILURE));
        ProxyServerUtils.closeOnFlush(ctx.channel());
    }
}

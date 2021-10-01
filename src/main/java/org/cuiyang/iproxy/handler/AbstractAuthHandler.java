package org.cuiyang.iproxy.handler;

import org.cuiyang.iproxy.*;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;

/**
 * 认证处理器基类
 *
 * @author cuiyang
 * @param <T>
 */
@Slf4j
public abstract class AbstractAuthHandler<T> extends SimpleChannelInboundHandler<T> implements ProxyConfigHolder {

    protected ProxyConfig config;

    /**
     * 认证
     */
    protected boolean authenticate(String username, String password) {
        if (config.getProxyAuthenticator() == null) {
            return true;
        }
        try {
            return config.getProxyAuthenticator().authenticate(username, password);
        } catch (Exception e) {
            log.error("认证异常", e);
            return false;
        }
    }

    /**
     * 构建Connection
     */
    protected void buildConnection(ChannelHandlerContext ctx, T request, String username) {
        Connection connection = Connection.builder()
                .clientChannel(ctx.channel())
                .username(username)
                .mitm(config.getMitmManager() != null)
                .connectTimeout(config.getConnectTimeout())
                .connectRetryTimes(config.getConnectRetryTimes())
                .build();
        ctx.channel().attr(Attributes.CONNECTION).set(connection);
    }

    /**
     * 认证成功
     */
    protected abstract void authenticateSuccess(ChannelHandlerContext ctx, T request);

    /**
     * 认证失败
     */
    protected abstract void authenticateFail(ChannelHandlerContext ctx, T request);

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("认证处理器异常", cause);
        ProxyServerUtils.closeOnFlush(ctx.channel());
    }

    @Override
    public void holdConfig(ProxyConfig config) {
        this.config = config;
    }
}

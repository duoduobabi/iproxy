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
    protected boolean authenticate(Connection connection, String username, String password) {
        connection.setUsername(username);
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
     * 认证成功
     */
    protected abstract void authenticateSuccess(Connection connection, T request);

    /**
     * 认证失败
     */
    protected abstract void authenticateFail(Connection connection, T request);

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

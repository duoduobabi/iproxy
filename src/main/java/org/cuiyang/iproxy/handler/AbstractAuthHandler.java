package org.cuiyang.iproxy.handler;

import org.cuiyang.iproxy.Attributes;
import org.cuiyang.iproxy.ProxyConfig;
import org.cuiyang.iproxy.ProxyConfigHolder;
import org.cuiyang.iproxy.ProxyServerUtils;
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
     * 设置属性
     */
    protected void setAttributes(ChannelHandlerContext ctx, T request, String username) {
        // 认证用户
        ctx.channel().attr(Attributes.USERNAME).set(username);
        // 连接超时
        ctx.channel().attr(Attributes.CONNECT_TIMEOUT).set(config.getConnectTimeout());
        // 连接重试次数
        ctx.channel().attr(Attributes.CONNECT_RETRY_TIMES).set(config.getConnectRetryTimes());
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
        log.error("认证处理异常", cause);
        ProxyServerUtils.closeOnFlush(ctx.channel());
    }

    @Override
    public void holdConfig(ProxyConfig config) {
        this.config = config;
    }
}

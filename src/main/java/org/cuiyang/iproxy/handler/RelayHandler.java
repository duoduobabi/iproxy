package org.cuiyang.iproxy.handler;

import lombok.Setter;
import org.cuiyang.iproxy.Connection;
import org.cuiyang.iproxy.ProxyConfig;
import org.cuiyang.iproxy.ProxyConfigHolder;
import org.cuiyang.iproxy.ProxyServerUtils;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.ReferenceCountUtil;
import lombok.extern.slf4j.Slf4j;

/**
 * @author cuiyang
 */
@Slf4j
public class RelayHandler extends ChannelInboundHandlerAdapter implements ProxyConfigHolder {
    protected ProxyConfig config;
    @Setter
    protected Connection connection;
    @Setter
    protected Channel relayChannel;

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        ctx.writeAndFlush(Unpooled.EMPTY_BUFFER);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (relayChannel.isActive()) {
            if (config.getInterceptor() != null) {
                msg = config.getInterceptor().intercept(connection, msg);
            }
            if (msg != null) {
                relayChannel.writeAndFlush(msg);
            }
        } else {
            ReferenceCountUtil.release(msg);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        ProxyServerUtils.closeOnFlush(relayChannel);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        ctx.close();
    }

    @Override
    public void holdConfig(ProxyConfig config) {
        this.config = config;
    }
}

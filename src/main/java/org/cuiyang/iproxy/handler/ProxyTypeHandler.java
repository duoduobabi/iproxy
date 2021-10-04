package org.cuiyang.iproxy.handler;

import org.cuiyang.iproxy.*;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.socksx.SocksPortUnificationServerHandler;
import io.netty.handler.codec.socksx.SocksVersion;
import lombok.extern.slf4j.Slf4j;

/**
 * 代理类型处理器
 *
 * @author cuiyang
 */
@Slf4j
@ChannelHandler.Sharable
public class ProxyTypeHandler extends SimpleChannelInboundHandler<ByteBuf> implements ProxyConfigHolder {

    private ProxyConfig config;

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) {
        final int readerIndex = msg.readerIndex();
        if (msg.writerIndex() == readerIndex) {
            return;
        }

        ChannelPipeline p = ctx.pipeline();
        final byte versionVal = msg.getByte(readerIndex);

        SocksVersion version = SocksVersion.valueOf(versionVal);
        if (version.equals(SocksVersion.SOCKS4a) || version.equals(SocksVersion.SOCKS5)) {
            // socks proxy
            p.addLast(new SocksPortUnificationServerHandler());
            p.addLast(config.getSocksAuthHandler());
        } else {
            // http / tunnel proxy
            p.addLast(new HttpServerCodec());
            p.addLast(config.getHttpAuthHandler());
        }
        p.remove(this);
        msg.retain();
        // 设置连接
        ctx.channel().attr(Attributes.CONNECTION).set(config.newConnection(ctx.channel()));
        ctx.fireChannelRead(msg);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("代理类型处理异常", cause);
        ProxyServerUtils.closeOnFlush(ctx.channel());
    }

    @Override
    public void holdConfig(ProxyConfig config) {
        this.config = config;
    }
}

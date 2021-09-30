package org.cuiyang.iproxy.handler.socks;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.socksx.v5.DefaultSocks5CommandResponse;
import io.netty.handler.codec.socksx.v5.Socks5CommandRequest;
import io.netty.handler.codec.socksx.v5.Socks5CommandStatus;
import lombok.extern.slf4j.Slf4j;
import org.cuiyang.iproxy.Connection;
import org.cuiyang.iproxy.handler.AbstractConnectHandler;

/**
 * socks连接处理器
 *
 * @author cuiyang
 */
@Slf4j
@ChannelHandler.Sharable
public class SocksConnectHandler extends AbstractConnectHandler<Socks5CommandRequest> {

    @Override
    public void channelRead0(ChannelHandlerContext ctx, Socks5CommandRequest request) {
        log.info("Socks5 {}:{}", request.dstAddr(), request.dstPort());
        super.channelRead0(ctx, request);
    }

    @Override
    protected void connectSuccess(ChannelHandlerContext ctx, Connection connection, Socks5CommandRequest request) {
        ChannelFuture responseFuture = ctx.channel().writeAndFlush(new DefaultSocks5CommandResponse(
                        Socks5CommandStatus.SUCCESS,
                        request.dstAddrType(),
                        request.dstAddr(),
                        request.dstPort()));
        responseFuture.addListener(channelFuture -> super.connectSuccess(ctx, connection, request));
    }

    @Override
    protected void connectFail(ChannelHandlerContext ctx, Connection connection, Socks5CommandRequest request, Throwable cause) {
        ctx.channel().writeAndFlush(new DefaultSocks5CommandResponse(Socks5CommandStatus.FAILURE, request.dstAddrType()));
        super.connectFail(ctx, connection, request, cause);
    }
}

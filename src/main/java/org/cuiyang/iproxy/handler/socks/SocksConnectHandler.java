package org.cuiyang.iproxy.handler.socks;

import org.cuiyang.iproxy.ProxyServerUtils;
import org.cuiyang.iproxy.handler.AbstractConnectHandler;
import org.cuiyang.iproxy.handler.RelayHandler;
import io.netty.channel.*;
import io.netty.handler.codec.socksx.v5.DefaultSocks5CommandResponse;
import io.netty.handler.codec.socksx.v5.Socks5CommandRequest;
import io.netty.handler.codec.socksx.v5.Socks5CommandStatus;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;

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
    protected void handleConnectResult(ChannelHandlerContext ctx, Socks5CommandRequest request,
                                       ChannelFuture channelFuture) {
        channelFuture.addListener(future -> {
            if (!future.isSuccess()) {
                ctx.channel().writeAndFlush(
                        new DefaultSocks5CommandResponse(Socks5CommandStatus.FAILURE, request.dstAddrType()));
                ProxyServerUtils.closeOnFlush(ctx.channel());
            }
        });
    }

    @Override
    protected void connectSuccess(ChannelHandlerContext ctx, Socks5CommandRequest request,
                                  Channel inboundChannel, Channel outboundChannel) {
        ChannelFuture responseFuture =
                ctx.channel().writeAndFlush(new DefaultSocks5CommandResponse(
                        Socks5CommandStatus.SUCCESS,
                        request.dstAddrType(),
                        request.dstAddr(),
                        request.dstPort()));

        responseFuture.addListener((ChannelFutureListener) channelFuture -> {
            ctx.pipeline().remove(SocksConnectHandler.this);
            outboundChannel.pipeline().addLast(new RelayHandler(ctx.channel()));
            ctx.pipeline().addLast(new RelayHandler(outboundChannel));
        });
    }

    @Override
    protected void connectFail(ChannelHandlerContext ctx, Socks5CommandRequest request,
                               Channel inboundChannel, Channel outboundChannel, Throwable cause) {
        ctx.channel().writeAndFlush(new DefaultSocks5CommandResponse(Socks5CommandStatus.FAILURE, request.dstAddrType()));
        ProxyServerUtils.closeOnFlush(ctx.channel());
    }

    @Override
    protected InetSocketAddress getTargetAddress(Socks5CommandRequest request) {
        return InetSocketAddress.createUnresolved(request.dstAddr(), request.dstPort());
    }
}

package org.cuiyang.iproxy.handler.socks;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
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
    protected void connectSuccess(Connection connection, Socks5CommandRequest request) {
        ChannelFuture responseFuture = connection.getClientChannel().writeAndFlush(new DefaultSocks5CommandResponse(
                        Socks5CommandStatus.SUCCESS,
                        request.dstAddrType(),
                        request.dstAddr(),
                        request.dstPort()));
        responseFuture.addListener(channelFuture -> super.connectSuccess(connection, request));
    }

    @Override
    protected void connectFail(Connection connection, Socks5CommandRequest request, Throwable cause) {
        connection.getClientChannel().writeAndFlush(new DefaultSocks5CommandResponse(Socks5CommandStatus.FAILURE, request.dstAddrType()));
        super.connectFail(connection, request, cause);
    }
}

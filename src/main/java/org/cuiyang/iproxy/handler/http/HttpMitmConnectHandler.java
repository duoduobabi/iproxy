package org.cuiyang.iproxy.handler.http;

import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.ssl.SslHandler;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.cuiyang.iproxy.ProxyServerUtils;
import org.cuiyang.iproxy.handler.AbstractConnectHandler;
import org.cuiyang.iproxy.handler.RelayHandler;

import javax.net.ssl.SSLEngine;
import java.net.InetSocketAddress;

/**
 * HTTP MITM(Man In The Middle) 连接处理器
 *
 * @author cuiyang
 */
@Slf4j
@ChannelHandler.Sharable
public class HttpMitmConnectHandler extends AbstractConnectHandler<HttpRequest> {

    @Override
    public void channelRead0(ChannelHandlerContext ctx, HttpRequest request) {
        log.info("{} {} {}", request.method(), request.uri(), request.protocolVersion());
        super.channelRead0(ctx, request);
    }

    @SneakyThrows
    @Override
    protected void connectSuccess(ChannelHandlerContext ctx, HttpRequest request,
                                  Channel inboundChannel, Channel outboundChannel) {
        InetSocketAddress targetAddress = getTargetAddress(request);
        SSLEngine sslEngine = config.getMitmManager().serverSslEngine(targetAddress.getHostName(), targetAddress.getPort());
        SslHandler sslHandler = new SslHandler(sslEngine);
        outboundChannel.pipeline().addFirst(sslHandler);
        sslHandler.handshakeFuture().addListener(f -> {
            if (!f.isSuccess()) {
                log.debug("ssl握手失败", f.cause());
                ProxyServerUtils.closeOnFlush(inboundChannel, outboundChannel);
                return;
            }
            ChannelFuture responseFuture = inboundChannel.writeAndFlush(new DefaultFullHttpResponse(request.protocolVersion(), HttpResponseStatus.OK));
            responseFuture.addListener(f2 -> {
                if (!f2.isSuccess()) {
                    log.debug("Http请求失败", f2.cause());
                    ProxyServerUtils.closeOnFlush(inboundChannel, outboundChannel);
                    return;
                }
                inboundChannel.pipeline().addFirst(new SslHandler(config.getMitmManager().clientSslEngineFor(request, sslEngine.getSession())));
                inboundChannel.pipeline().remove(HttpMitmConnectHandler.this);
                inboundChannel.pipeline().addLast(new RelayHandler(outboundChannel));
                outboundChannel.pipeline().addLast(new RelayHandler(inboundChannel));
            });
        });
    }

    @Override
    protected void handleConnectResult(ChannelHandlerContext ctx, HttpRequest request, ChannelFuture channelFuture) {
        super.handleConnectResult(ctx, request, channelFuture);
        if (config.getProxyFactory() != null) {
            channelFuture.addListener(f -> {
                if (f.isSuccess()) {
                    InetSocketAddress address = getTargetAddress(request);
                    Channel ch = channelFuture.channel();
                    HttpRequest connect = new DefaultFullHttpRequest(
                            request.protocolVersion(), HttpMethod.CONNECT, address.getHostString() + ":" + address.getPort(), Unpooled.EMPTY_BUFFER);
                    ch.writeAndFlush(connect).addListener(f2 -> {
                        if (!f2.isSuccess()) {
                            ProxyServerUtils.closeOnFlush(ch);
                            ProxyServerUtils.closeOnFlush(ctx.channel());
                        }
                    });
                }
            });
        }
    }

    @Override
    protected ChannelHandler handler(ChannelHandlerContext ctx, HttpRequest request) {
        ChannelHandler superHandler = super.handler(ctx, request);
        return new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) {
                if (config.getProxyFactory() == null) {
                    ch.pipeline().addLast(new HttpClientCodec());
                    ch.pipeline().addLast(superHandler);
                } else {
                    ch.pipeline().addLast(new HttpClientCodec());
                    ch.pipeline().addLast(new SimpleChannelInboundHandler<HttpResponse>() {
                        @Override
                        protected void channelRead0(ChannelHandlerContext ctx, HttpResponse msg) {
                            Channel ch = ctx.channel();
                            ChannelPipeline pipeline = ch.pipeline();
                            pipeline.remove(HttpClientCodec.class);
                            pipeline.remove(this);
                            ch.pipeline().addLast(new HttpClientCodec());
                            pipeline.addLast(superHandler);
                            pipeline.fireChannelActive();
                        }
                    });
                }
            }
        };
    }

    @Override
    protected void connectFail(ChannelHandlerContext ctx, HttpRequest request,
                               Channel inboundChannel, Channel outboundChannel, Throwable cause) {
        log.debug("HttpTunnel响应失败", cause);
        ProxyServerUtils.closeOnFlush(inboundChannel, outboundChannel);
    }

    @Override
    protected InetSocketAddress getTargetAddress(HttpRequest request) {
        return ProxyServerUtils.httpAddress(request, 443);
    }
}

package org.cuiyang.iproxy;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoop;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.ReferenceCounted;
import io.netty.util.concurrent.Promise;
import lombok.Data;

import java.io.Closeable;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
public class Connection implements Closeable, ProxyConfigHolder {
    private String id = UUID.randomUUID().toString().replace("-", "");
    /** config */
    private ProxyConfig config;
    /** 客户端到代理 */
    private Channel clientChannel;
    /** 代理到服务端 */
    private Channel serverChannel;
    /** 服务端地址 */
    private InetSocketAddress serverAddress;
    /** 认证用户 */
    private String username;
    /** 中间人攻击 */
    private boolean mitm;
    /** 连接超时 */
    private int connectTimeout;
    /** 连接重试次数 */
    private int connectRetryTimes;
    /** 临时消息 */
    private List<Object> messages = new ArrayList<>();

    public static Connection currentConnection(ChannelHandlerContext ctx) {
        return ctx.channel().attr(Attributes.CONNECTION).get();
    }

    public ChannelPipeline getClientPipeline() {
        return clientChannel.pipeline();
    }

    public ChannelPipeline getServerPipeline() {
        return serverChannel.pipeline();
    }

    public void connect() {
        getClientPipeline().addLast(config.newRelayHandler(this, serverChannel));
        getServerPipeline().addLast(config.newRelayHandler(this, clientChannel));
    }

    public EventLoop eventLoop() {
        return clientChannel.eventLoop();
    }

    public <V> Promise<V> newPromise() {
        return this.eventLoop().newPromise();
    }

    public boolean isSsl() {
        return getClientPipeline().context(SslHandler.class) != null;
    }

    public void request(Object msg) {
        if (serverChannel != null) {
            serverChannel.writeAndFlush(msg);
        } else {
            if (msg instanceof ReferenceCounted) {
                ((ReferenceCounted) msg).retain();
            }
            messages.add(msg);
        }
    }

    public void response(Object msg) {
        clientChannel.writeAndFlush(msg);
    }

    public void flush() {
        messages.forEach(msg -> clientChannel.pipeline().fireChannelRead(msg));
        messages.clear();
    }

    @Override
    public void close() {
        ProxyServerUtils.closeOnFlush(clientChannel, serverChannel);
    }

    @Override
    public void holdConfig(ProxyConfig config) {
        this.config = config;
    }
}

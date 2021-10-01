package org.cuiyang.iproxy;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoop;
import io.netty.util.ReferenceCounted;
import io.netty.util.concurrent.Promise;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.cuiyang.iproxy.handler.RelayHandler;

import java.io.Closeable;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Connection implements Closeable {
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
    @Builder.Default
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
        getClientPipeline().addLast(new RelayHandler(serverChannel));
        getServerPipeline().addLast(new RelayHandler(clientChannel));
    }

    public EventLoop eventLoop() {
        return clientChannel.eventLoop();
    }

    public <V> Promise<V> newPromise() {
        return this.eventLoop().newPromise();
    }

    public void write(Object msg) {
        if (serverChannel != null) {
            serverChannel.writeAndFlush(msg);
        } else {
            if (msg instanceof ReferenceCounted) {
                ((ReferenceCounted) msg).retain();
            }
            messages.add(msg);
        }
    }

    public void flush() {
        messages.forEach(msg -> clientChannel.pipeline().fireChannelRead(msg));
        messages.clear();
    }

    @Override
    public void close() {
        ProxyServerUtils.closeOnFlush(clientChannel, serverChannel);
    }
}

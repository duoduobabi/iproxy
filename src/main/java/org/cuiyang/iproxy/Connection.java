package org.cuiyang.iproxy;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.util.ReferenceCounted;
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
    private Channel inboundChannel;
    /** 代理到服务端 */
    private Channel outboundChannel;
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

    public void write(Object msg) {
        if (outboundChannel != null) {
            outboundChannel.writeAndFlush(msg);
        } else {
            if (msg instanceof ReferenceCounted) {
                ((ReferenceCounted) msg).retain();
            }
            messages.add(msg);
        }
    }

    public void flush() {
        messages.forEach(msg -> inboundChannel.pipeline().fireChannelRead(msg));
        messages.clear();
    }

    public static Connection currentConnection(ChannelHandlerContext ctx) {
        return ctx.channel().attr(Attributes.CONNECTION).get();
    }

    public Connection(Channel inboundChannel) {
        this.inboundChannel = inboundChannel;
    }

    public ChannelPipeline getInboundPipeline() {
        return inboundChannel.pipeline();
    }

    public ChannelPipeline getOutboundPipeline() {
        return outboundChannel.pipeline();
    }

    public void connect() {
        getInboundPipeline().addLast(new RelayHandler(outboundChannel));
        getOutboundPipeline().addLast(new RelayHandler(inboundChannel));
    }

    @Override
    public void close() {
        ProxyServerUtils.closeOnFlush(inboundChannel, outboundChannel);
    }
}

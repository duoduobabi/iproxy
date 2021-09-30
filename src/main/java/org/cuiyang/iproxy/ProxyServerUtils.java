package org.cuiyang.iproxy;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpRequestEncoder;
import org.apache.commons.lang3.StringUtils;

import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;

/**
 * 代理服务工具类
 *
 * @author cuiyang
 */
public final class ProxyServerUtils {

    private ProxyServerUtils() {
    }

    public static void closeOnFlush(Channel... chs) {
        for (Channel ch : chs) {
            try {
                if (ch != null && ch.isActive()) {
                    ch.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
                }
            } catch (Exception ignore){
            }
        }
    }

    public static byte[] byteBuf(ByteBuf byteBuf) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byteBuf.forEachByte(value -> {
            out.write(value);
            return true;
        });
        return out.toByteArray();
    }

    public static ByteBuf http2byteBuf(HttpRequest request) {
        EmbeddedChannel em = new EmbeddedChannel(new HttpRequestEncoder());
        em.writeOutbound(request);
        ByteBuf byteBuf = em.readOutbound();
        em.close();
        return byteBuf;
    }

    public static InetSocketAddress httpAddress(HttpRequest request, int defaultPort) {
        String host;
        if (request.method().equals(HttpMethod.CONNECT)) {
            host = request.uri();
        } else {
            host = request.headers().get(HttpHeaderNames.HOST);
        }
        int port = defaultPort;
        if (host.contains(":")) {
            port = Integer.parseInt(StringUtils.substringAfter(host, ":"));
            host = StringUtils.substringBefore(host, ":");
        }
        return InetSocketAddress.createUnresolved(host, port);
    }
}
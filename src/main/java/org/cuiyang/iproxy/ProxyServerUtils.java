package org.cuiyang.iproxy;


import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpRequestEncoder;

import java.io.ByteArrayOutputStream;

/**
 * 代理服务工具类
 *
 * @author cuiyang
 */
public final class ProxyServerUtils {

    private ProxyServerUtils() {
    }

    public static void closeOnFlush(Channel ch) {
        if (ch.isActive()) {
            ch.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
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
}
package org.cuiyang.iproxy;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import lombok.extern.slf4j.Slf4j;

/**
 * 代理服务
 *
 * @author cuiyang
 */
@Slf4j
public class ProxyServer {

    private ProxyConfig config;

    public ProxyServer(ProxyConfig config) {
        this.config = config;
    }

    public void start() {
        log.info("启动代理服务...");
        ServerBootstrap b = new ServerBootstrap();
        b.group(this.config.getBossGroup(), this.config.getWorkerGroup())
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast(config.getProxyTypeHandler());
                    }
                })
                .bind(this.config.getPort()).syncUninterruptibly().channel();
        log.info("代理服务启动成功!");
    }

    public void stop() {
        log.info("关闭代理服务...");
        this.config.getBossGroup().shutdownGracefully().syncUninterruptibly();
        this.config.getWorkerGroup().shutdownGracefully().syncUninterruptibly();
        log.info("代理服务关闭成功!");
    }
}

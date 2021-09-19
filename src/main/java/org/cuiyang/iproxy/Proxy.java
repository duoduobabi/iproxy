package org.cuiyang.iproxy;

import java.net.InetSocketAddress;

/**
 * 代理
 *
 * @author cuiyang
 */
public class Proxy extends java.net.Proxy {
    private String username;
    private String password;

    public Proxy(Type type, InetSocketAddress sa) {
        super(type, sa);
    }

    public Proxy(Type type, InetSocketAddress sa, String username, String password) {
        super(type, sa);
        this.username = username;
        this.password = password;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public static Proxy createHttpProxy(String host, int port, String username, String password) {
        return new Proxy(Type.HTTP, InetSocketAddress.createUnresolved(host, port), username, password);
    }

    public static Proxy createHttpProxy(String host, int port) {
        return new Proxy(Type.HTTP, InetSocketAddress.createUnresolved(host, port));
    }

    public static Proxy createSocksProxy(String host, int port, String username, String password) {
        return new Proxy(Type.SOCKS, InetSocketAddress.createUnresolved(host, port), username, password);
    }

    public static Proxy createSocksProxy(String host, int port) {
        return new Proxy(Type.SOCKS, InetSocketAddress.createUnresolved(host, port));
    }
}

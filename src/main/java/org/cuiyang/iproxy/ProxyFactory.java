package org.cuiyang.iproxy;

/**
 * 代理工厂
 *
 * @author cuiyang
 */
public interface ProxyFactory {

    Proxy getProxy(Proxy.Type type);
}

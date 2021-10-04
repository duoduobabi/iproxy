package org.cuiyang.iproxy;

/**
 * 拦截器
 *
 * @author cuiyang
 */
public interface Interceptor extends ProxyConfigHolder {

    /**
     * 拦截消息
     * @param connection 消息所属链接
     * @param message 消息
     * @return 修改后的消息, 如果返回null, 则终止消息传递
     */
    Object message(Connection connection, Object message);
}

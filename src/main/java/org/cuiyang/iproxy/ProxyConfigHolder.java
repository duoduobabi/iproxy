package org.cuiyang.iproxy;

/**
 * 代理配置持有
 *
 * @author cuiyang
 */
public interface ProxyConfigHolder {

    /**
     * 持有配置
     * @param config 代理配置
     */
    default void holdConfig(ProxyConfig config) {
    }
}

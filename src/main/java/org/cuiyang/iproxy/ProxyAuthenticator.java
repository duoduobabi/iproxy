package org.cuiyang.iproxy;

/**
 * 代理认证
 *
 * @author cuiyang
 */
public interface ProxyAuthenticator {

    /**
     * 认证
     * @param username 用户名
     * @param password 密码
     * @return 认证结果
     */
    boolean authenticate(String username, String password);
}

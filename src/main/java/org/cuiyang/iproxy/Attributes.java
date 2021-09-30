package org.cuiyang.iproxy;

import io.netty.util.AttributeKey;

public class Attributes {
    /** 连接用户 */
    public static final AttributeKey<String> USERNAME = AttributeKey.valueOf("username");
    /** CONNECTION */
    public static final AttributeKey<Connection> CONNECTION = AttributeKey.valueOf("connection");
}

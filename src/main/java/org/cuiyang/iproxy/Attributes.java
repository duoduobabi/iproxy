package org.cuiyang.iproxy;

import io.netty.util.AttributeKey;

public class Attributes {
    /** 连接超时 */
    public static final AttributeKey<Integer> CONNECT_TIMEOUT = AttributeKey.valueOf("connectTimeout");
    /** 连接重试次数 */
    public static final AttributeKey<Integer> CONNECT_RETRY_TIMES = AttributeKey.valueOf("connectRetryTimes");
    /** 连接用户 */
    public static final AttributeKey<String> USERNAME = AttributeKey.valueOf("username");
}

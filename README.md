## Test
```
# Http代理
curl --proxy http://127.0.0.1:8080 https://tool.oschina.net/
# socks5代理
curl --proxy socks5://127.0.0.1:8080 https://tool.oschina.net/
# 代理认证
curl --proxy http://127.0.0.1:8080 -U admin:123456 https://tool.oschina.net/
# 忽略证书
curl -k --proxy http://127.0.0.1:8080 https://tool.oschina.net/
# Mitm
curl -v --cacert /Users/cuiyang/code/cy/iproxy/littleproxy-mitm.pem --proxy http://127.0.0.1:8080 https://tool.oschina.net/
```
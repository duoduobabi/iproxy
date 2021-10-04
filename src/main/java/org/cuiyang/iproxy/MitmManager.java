package org.cuiyang.iproxy;

import io.netty.handler.codec.http.HttpRequest;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSession;


public interface MitmManager {

    SSLEngine serverSslEngine(String peerHost, int peerPort);

    SSLEngine clientSslEngineFor(HttpRequest httpRequest, SSLSession serverSslSession);
}

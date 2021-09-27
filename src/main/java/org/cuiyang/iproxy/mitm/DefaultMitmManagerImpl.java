package org.cuiyang.iproxy.mitm;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import lombok.SneakyThrows;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.OperatorCreationException;
import org.cuiyang.iproxy.MitmManager;

import javax.net.ssl.*;
import java.io.*;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class DefaultMitmManagerImpl implements MitmManager {
    private static final String KEY_STORE_TYPE = "PKCS12";
    private static final String KEY_STORE_FILE_EXTENSION = ".p12";
    private static final String PEM_FILE_EXTENSION = ".pem";

    private Authority authority;
    private Certificate caCert;
    private PrivateKey caPrivateKey;
    private SslContext sslContext;
    private Cache<String, SslContext> serverSSLContexts;

    @SneakyThrows
    public DefaultMitmManagerImpl(Authority authority) {
        this.authority = authority;
        this.serverSSLContexts = CacheBuilder.newBuilder()
                .expireAfterAccess(5, TimeUnit.MINUTES)
                .concurrencyLevel(16)
                .build();
        this.initializeKeyStore();
        this.initializeSSLContext();
    }

    @Override
    public SSLEngine serverSslEngine(String peerHost, int peerPort) {
        return sslContext.newEngine(null, peerHost, peerPort);
    }

    @Override
    public SSLEngine clientSslEngineFor(HttpRequest httpRequest, SSLSession serverSslSession) {
        try {
            X509Certificate upstreamCert = getCertificateFromSession(serverSslSession);
            String commonName = getCommonName(upstreamCert);

            SslContext ctx;
            if (serverSSLContexts == null) {
                ctx = createServerContext(commonName, upstreamCert.getSubjectAlternativeNames());
            } else {
                ctx = serverSSLContexts.get(commonName, () -> createServerContext(commonName, upstreamCert.getSubjectAlternativeNames()));
            }
            return ctx.newEngine(null);
        } catch (Exception e) {
            throw new RuntimeException("Creation dynamic certificate failed", e);
        }
    }

    private SslContext createServerContext(String commonName,
                                           Collection<List<?>> subjectAlternativeNames) throws GeneralSecurityException, IOException, OperatorCreationException {
        KeyStore ks = CertificateUtils.createServerCertificate(commonName, subjectAlternativeNames, authority, caCert, caPrivateKey);
        String keyManAlg = KeyManagerFactory.getDefaultAlgorithm();
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(keyManAlg);
        kmf.init(ks, authority.getPassword());
        return SslContextBuilder.forServer(kmf).build();
    }

    private void initializeKeyStore() throws GeneralSecurityException, OperatorCreationException, IOException {
        if (authority.aliasFile(KEY_STORE_FILE_EXTENSION).exists() && authority.aliasFile(PEM_FILE_EXTENSION).exists()) {
            return;
        }
        KeyStore keystore = CertificateUtils.createRootCertificate(authority, KEY_STORE_TYPE);

        try (OutputStream os = new FileOutputStream(authority.aliasFile(KEY_STORE_FILE_EXTENSION))) {
            keystore.store(os, authority.getPassword());
        }

        Certificate cert = keystore.getCertificate(authority.getAlias());
        try (JcaPEMWriter pw = new JcaPEMWriter(new FileWriter(authority.aliasFile(PEM_FILE_EXTENSION)))) {
            pw.writeObject(cert);
            pw.flush();
        }
    }

    private void initializeSSLContext() throws GeneralSecurityException, IOException {
        KeyStore ks = KeyStore.getInstance(KEY_STORE_TYPE);
        try (FileInputStream is = new FileInputStream(authority.aliasFile(KEY_STORE_FILE_EXTENSION))) {
            ks.load(is, authority.getPassword());
        }
        caCert = ks.getCertificate(authority.getAlias());
        caPrivateKey = (PrivateKey) ks.getKey(authority.getAlias(), authority.getPassword());
        sslContext = SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE).build();
    }

    private X509Certificate getCertificateFromSession(SSLSession sslSession)
            throws SSLPeerUnverifiedException {
        Certificate[] peerCerts = sslSession.getPeerCertificates();
        Certificate peerCert = peerCerts[0];
        if (peerCert instanceof java.security.cert.X509Certificate) {
            return (java.security.cert.X509Certificate) peerCert;
        }
        throw new IllegalStateException("Required java.security.cert.X509Certificate, found: " + peerCert);
    }

    private String getCommonName(X509Certificate c) {
        for (String each : c.getSubjectDN().getName().split(",\\s*")) {
            if (each.startsWith("CN=")) {
                return each.substring(3);
            }
        }
        throw new IllegalStateException("Missed CN in Subject DN: " + c.getSubjectDN());
    }
}

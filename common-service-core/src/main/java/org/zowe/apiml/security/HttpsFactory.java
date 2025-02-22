/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */
package org.zowe.apiml.security;

import com.netflix.discovery.shared.transport.jersey.EurekaJerseyClientImpl.EurekaJerseyClientBuilder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.UserTokenHandler;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.ssl.PrivateKeyStrategy;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.ssl.SSLContexts;
import org.zowe.apiml.message.log.ApimlLogger;
import org.zowe.apiml.message.yaml.YamlMessageServiceInstance;
import org.zowe.apiml.security.HttpsConfigError.ErrorCode;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.*;
import java.security.cert.CertificateException;
import java.util.concurrent.TimeUnit;


@Slf4j
@Data
public class HttpsFactory {

    private HttpsConfig config;
    private SSLContext secureSslContext;
    private KeyStore usedKeyStore = null;
    private ApimlLogger apimlLog;

    public HttpsFactory(HttpsConfig httpsConfig) {
        this.config = httpsConfig;
        this.secureSslContext = null;
        this.apimlLog = ApimlLogger.of(HttpsFactory.class, YamlMessageServiceInstance.getInstance());
    }


    public CloseableHttpClient createSecureHttpClient() {
        Registry<ConnectionSocketFactory> socketFactoryRegistry;
        RegistryBuilder<ConnectionSocketFactory> socketFactoryRegistryBuilder = RegistryBuilder
            .<ConnectionSocketFactory>create().register("http", PlainConnectionSocketFactory.getSocketFactory());
        UserTokenHandler userTokenHandler = context -> context.getAttribute("my-token");

        socketFactoryRegistryBuilder.register("https", createSslSocketFactory());
        socketFactoryRegistry = socketFactoryRegistryBuilder.build();
        RequestConfig requestConfig = RequestConfig.custom()
            .setConnectTimeout(config.getRequestConnectionTimeout()).build();
        ApimlPoolingHttpClientConnectionManager connectionManager =
            new ApimlPoolingHttpClientConnectionManager(socketFactoryRegistry, config.getTimeToLive());
        connectionManager.setDefaultMaxPerRoute(config.getMaxConnectionsPerRoute());
        connectionManager.closeIdleConnections(config.getIdleConnTimeoutSeconds(), TimeUnit.SECONDS);
        connectionManager.setMaxTotal(config.getMaxTotalConnections());

        return HttpClientBuilder.create().setDefaultRequestConfig(requestConfig).setSSLHostnameVerifier(createHostnameVerifier())
            .setConnectionManager(connectionManager).disableCookieManagement().setUserTokenHandler(userTokenHandler)
            .setKeepAliveStrategy(ApimlKeepAliveStrategy.INSTANCE)
            .disableAuthCaching().build();

    }

    public ConnectionSocketFactory createSslSocketFactory() {
        if (config.isVerifySslCertificatesOfServices() || config.isNonStrictVerifySslCertificatesOfServices()) {
            return createSecureSslSocketFactory();
        } else {
            apimlLog.log("org.zowe.apiml.common.ignoringSsl");
            return createIgnoringSslSocketFactory();
        }
    }

    /**
     * Method is only for testing purpose. It is stored only in case of empty keystore (not if keystore is provided).
     */
    KeyStore getUsedStore() {
        return usedKeyStore;
    }

    private ConnectionSocketFactory createIgnoringSslSocketFactory() {
        return new SSLConnectionSocketFactory(createIgnoringSslContext(), new NoopHostnameVerifier());
    }

    private SSLContext createIgnoringSslContext() {
        try {
            KeyStore emptyKeystore = KeyStore.getInstance(KeyStore.getDefaultType());
            emptyKeystore.load(null, null);
            usedKeyStore = emptyKeystore;
            return new SSLContextBuilder()
                .loadTrustMaterial(null, (certificate, authType) -> true)
                .loadKeyMaterial(emptyKeystore, null)
                .setProtocol(config.getProtocol()).build();
        } catch (Exception e) {
            apimlLog.log("org.zowe.apiml.common.errorInitSsl", e.getMessage());
            throw new HttpsConfigError("Error initializing SSL/TLS context: " + e.getMessage(), e,
                ErrorCode.SSL_CONTEXT_INITIALIZATION_FAILED, config);
        }
    }

    private void loadTrustMaterial(SSLContextBuilder sslContextBuilder)
        throws NoSuchAlgorithmException, KeyStoreException, CertificateException, IOException {
        if (StringUtils.isNotEmpty(config.getTrustStore())) {
            sslContextBuilder.setKeyStoreType(config.getTrustStoreType()).setProtocol(config.getProtocol());

            if (!config.getTrustStore().startsWith(SecurityUtils.SAFKEYRING)) {
                if (config.getTrustStorePassword() == null) {
                    apimlLog.log("org.zowe.apiml.common.truststorePasswordNotDefined");
                    throw new HttpsConfigError("server.ssl.trustStorePassword configuration parameter is not defined",
                        ErrorCode.TRUSTSTORE_PASSWORD_NOT_DEFINED, config);
                }

                log.info("Loading trust store file: " + config.getTrustStore());
                File trustStoreFile = new File(config.getTrustStore());

                sslContextBuilder.loadTrustMaterial(trustStoreFile, config.getTrustStorePassword());
            } else {
                log.info("Loading trust store key ring: " + config.getTrustStore());
                sslContextBuilder.loadTrustMaterial(keyRingUrl(config.getTrustStore()), config.getTrustStorePassword());
            }
        } else {
            if (config.isTrustStoreRequired()) {
                apimlLog.log("org.zowe.apiml.common.truststoreNotDefined");
                throw new HttpsConfigError(
                    "server.ssl.trustStore configuration parameter is not defined but trust store is required",
                    ErrorCode.TRUSTSTORE_NOT_DEFINED, config);
            } else {
                log.info("No trust store is defined");
            }
        }
    }

    private URL keyRingUrl(String uri) throws MalformedURLException {
        return SecurityUtils.keyRingUrl(uri, config.getTrustStore());
    }

    private void loadKeyMaterial(SSLContextBuilder sslContextBuilder) throws NoSuchAlgorithmException,
        KeyStoreException, CertificateException, IOException, UnrecoverableKeyException {
        if (StringUtils.isNotEmpty(config.getKeyStore())) {
            sslContextBuilder.setKeyStoreType(config.getKeyStoreType()).setProtocol(config.getProtocol());

            if (!config.getKeyStore().startsWith(SecurityUtils.SAFKEYRING)) {
                loadKeystoreMaterial(sslContextBuilder);
            } else {
                loadKeyringMaterial(sslContextBuilder);
            }
        } else {
            log.info("No key store is defined");
            KeyStore emptyKeystore = KeyStore.getInstance(KeyStore.getDefaultType());
            emptyKeystore.load(null, null);
            usedKeyStore = emptyKeystore;
            sslContextBuilder.loadKeyMaterial(emptyKeystore, null);
        }
    }

    private void loadKeystoreMaterial(SSLContextBuilder sslContextBuilder) throws UnrecoverableKeyException,
        NoSuchAlgorithmException, KeyStoreException, CertificateException, IOException {
        if (StringUtils.isEmpty(config.getKeyStore())) {
            apimlLog.log("org.zowe.apiml.common.keystoreNotDefined");
            throw new HttpsConfigError("server.ssl.keyStore configuration parameter is not defined",
                ErrorCode.KEYSTORE_NOT_DEFINED, config);
        }
        if (config.getKeyStorePassword() == null) {
            apimlLog.log("org.zowe.apiml.common.keystorePasswordNotDefined");
            throw new HttpsConfigError("server.ssl.keyStorePassword configuration parameter is not defined",
                ErrorCode.KEYSTORE_PASSWORD_NOT_DEFINED, config);
        }
        log.info("Loading key store file: " + config.getKeyStore());
        File keyStoreFile = new File(config.getKeyStore());
        sslContextBuilder.loadKeyMaterial(
            keyStoreFile, config.getKeyStorePassword(), config.getKeyPassword(),
            getPrivateKeyStrategy()
        );
    }

    private PrivateKeyStrategy getPrivateKeyStrategy() {
        return config.getKeyAlias() != null ? (aliases, socket) -> config.getKeyAlias() : null;
    }

    private void loadKeyringMaterial(SSLContextBuilder sslContextBuilder) throws UnrecoverableKeyException,
        NoSuchAlgorithmException, KeyStoreException, CertificateException, IOException {
        log.info("Loading trust key ring: " + config.getKeyStore());
        sslContextBuilder.loadKeyMaterial(keyRingUrl(config.getKeyStore()), config.getKeyStorePassword(),
            config.getKeyPassword(), getPrivateKeyStrategy());
    }

    private synchronized SSLContext createSecureSslContext() {
        if (secureSslContext == null) {
            log.debug("Protocol: {}", config.getProtocol());
            SSLContextBuilder sslContextBuilder = SSLContexts.custom();
            try {
                loadTrustMaterial(sslContextBuilder);
                loadKeyMaterial(sslContextBuilder);
                secureSslContext = sslContextBuilder.build();
                validateSslConfig();
                return secureSslContext;
            } catch (NoSuchAlgorithmException | KeyStoreException | CertificateException | IOException
                | UnrecoverableKeyException | KeyManagementException e) {
                log.error("error", e);
                apimlLog.log("org.zowe.apiml.common.sslContextInitializationError", e.getMessage());
                throw new HttpsConfigError("Error initializing SSL Context: " + e.getMessage(), e,
                    ErrorCode.HTTP_CLIENT_INITIALIZATION_FAILED, config);
            }
        } else {
            return secureSslContext;
        }
    }

    private void validateSslConfig() throws KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException {
        if (StringUtils.isNotEmpty(config.getKeyAlias())) {
            KeyStore ks = SecurityUtils.loadKeyStore(config);
            if (!ks.containsAlias(config.getKeyAlias())) {
                apimlLog.log("org.zowe.apiml.common.invalidKeyAlias", config.getKeyAlias());
                throw new HttpsConfigError(String.format("Invalid key alias '%s'", config.getKeyAlias()), ErrorCode.WRONG_KEY_ALIAS, config);
            }
        }
    }

    private ConnectionSocketFactory createSecureSslSocketFactory() {

        return new SSLConnectionSocketFactory(
            createSecureSslContext(),
            createHostnameVerifier()
        );
    }

    public SSLContext createSslContext() {
        if (config.isVerifySslCertificatesOfServices() || config.isNonStrictVerifySslCertificatesOfServices()) {
            return createSecureSslContext();
        } else {
            return createIgnoringSslContext();
        }
    }

    private void setSystemProperty(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }

    public void setSystemSslProperties() {
        setSystemProperty("javax.net.ssl.keyStore", SecurityUtils.replaceFourSlashes(config.getKeyStore()));
        setSystemProperty("javax.net.ssl.keyStorePassword",
            config.getKeyStorePassword() == null ? null : String.valueOf(config.getKeyStorePassword()));
        setSystemProperty("javax.net.ssl.keyStoreType", config.getKeyStoreType());

        setSystemProperty("javax.net.ssl.trustStore", SecurityUtils.replaceFourSlashes(config.getTrustStore()));
        setSystemProperty("javax.net.ssl.trustStorePassword",
            config.getTrustStorePassword() == null ? null : String.valueOf(config.getTrustStorePassword()));
        setSystemProperty("javax.net.ssl.trustStoreType", config.getTrustStoreType());
    }

    public HostnameVerifier createHostnameVerifier() {
        if (config.isVerifySslCertificatesOfServices() && !config.isNonStrictVerifySslCertificatesOfServices()) {
            return SSLConnectionSocketFactory.getDefaultHostnameVerifier();
        } else {
            return new NoopHostnameVerifier();
        }
    }

    public EurekaJerseyClientBuilder createEurekaJerseyClientBuilder(String eurekaServerUrl, String serviceId) {
        EurekaJerseyClientBuilder builder = new EurekaJerseyClientBuilder();
        builder.withClientName(serviceId);
        builder.withMaxTotalConnections(10);
        builder.withMaxConnectionsPerHost(10);
        builder.withConnectionIdleTimeout(10);
        builder.withConnectionTimeout(5000);
        builder.withReadTimeout(5000);
        // See:
        // https://github.com/Netflix/eureka/blob/master/eureka-core/src/main/java/com/netflix/eureka/transport/JerseyReplicationClient.java#L160
        if (eurekaServerUrl.startsWith("http://")) {
            apimlLog.log("org.zowe.apiml.common.insecureHttpWarning");
        } else {
            System.setProperty("com.netflix.eureka.shouldSSLConnectionsUseSystemSocketFactory", "true");

            if (config.isVerifySslCertificatesOfServices() || config.isNonStrictVerifySslCertificatesOfServices()) {
                setSystemSslProperties();
            }
            builder.withCustomSSL(createSslContext());

            builder.withHostnameVerifier(createHostnameVerifier());
        }
        return builder;
    }
}

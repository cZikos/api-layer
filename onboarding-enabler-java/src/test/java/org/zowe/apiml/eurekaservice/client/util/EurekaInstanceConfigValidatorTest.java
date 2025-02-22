/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.apiml.eurekaservice.client.util;

import ch.qos.logback.classic.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockServletContext;
import org.zowe.apiml.eurekaservice.client.config.ApiMediationServiceConfig;
import org.zowe.apiml.exception.MetadataValidationException;
import org.zowe.apiml.exception.ServiceDefinitionException;
import org.zowe.apiml.product.logging.LogMessageTracker;

import javax.servlet.ServletContext;
import java.util.Collections;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class EurekaInstanceConfigValidatorTest {

    private final EurekaInstanceConfigValidator validator = new EurekaInstanceConfigValidator();
    private final ApiMediationServiceConfigReader configReader = new ApiMediationServiceConfigReader();

    private final LogMessageTracker logTracker = new LogMessageTracker(validator.getClass());

    @BeforeEach
    void setup() {
        logTracker.startTracking();
    }

    @AfterEach
    void cleanUp() {
        logTracker.stopTracking();
    }

    @Test
    void givenServiceConfiguration_whenConfigurationIsValid_thenValidate() throws ServiceDefinitionException {
        ApiMediationServiceConfig testConfig = configReader.loadConfiguration("service-configuration.yml");
        assertDoesNotThrow(() -> validator.validate(testConfig));
    }

    @ParameterizedTest
    @CsvSource(value = {
        "bad-ssl-configuration.yml|SSL configuration was not provided. Try add apiml.service.ssl section.",
        "wrong-routes-service-configuration.yml|Routes parameters  ** gatewayUrl, serviceUrl ** are missing or were not replaced by the system properties.",
        "missing-ssl-configuration.yml|SSL parameters ** protocol, trustStore, keyStore, keyAlias, keyStoreType, trustStoreType, enabled, trustStorePassword, keyStorePassword, keyPassword ** are missing or were not replaced by the system properties."
    }, delimiter = '|')
    void givenConfigurationWithInvalidSsl_whenValidate_thenThrowException(String cfgFile, String expectedMsg) throws ServiceDefinitionException {
        ApiMediationServiceConfig testConfig = configReader.loadConfiguration(cfgFile);
        Exception exception = assertThrows(MetadataValidationException.class,
            () -> validator.validate(testConfig),
            "Expected exception is not MetadataValidationException");
        assertEquals(expectedMsg, exception.getMessage());
    }

    @Test
    void givenConfigurationWithHttpEurekaAndInvalidSsl_thenValidate() throws ServiceDefinitionException {
        ApiMediationServiceConfig testConfig = configReader.loadConfiguration("bad-ssl-configuration.yml");
        testConfig.setDiscoveryServiceUrls(Collections.singletonList("http://localhost:10011/eureka"));
        assertDoesNotThrow(() -> validator.validate(testConfig));
    }

    @Test
    void emptyKeyringPasswordsAreSupported() throws ServiceDefinitionException {
        ApiMediationServiceConfig testConfig = configReader.loadConfiguration("service-configuration-keyring.yml");
        testConfig.setDiscoveryServiceUrls(Collections.singletonList("https://localhost:10011/eureka"));
        assertDoesNotThrow(() -> validator.validate(testConfig));
    }

    @SuppressWarnings("squid:S5778") //Lambda formatting
    @Test
    void emptyPasswordsWithKeystoresAreValidatedAsErrors() {
        assertThrows(MetadataValidationException.class, () -> validator.validate(
            loadValidSslConfiguration(c -> c.getSsl().setKeyPassword(null))));
        assertThrows(MetadataValidationException.class, () -> validator.validate(
            loadValidSslConfiguration(c -> c.getSsl().setKeyStorePassword(null))));
        assertThrows(MetadataValidationException.class, () -> validator.validate(
            loadValidSslConfiguration(c -> c.getSsl().setTrustStorePassword(null))));
    }

    private ApiMediationServiceConfig loadValidSslConfiguration(Consumer<ApiMediationServiceConfig> configModifier) throws ServiceDefinitionException {
        ApiMediationServiceConfig testConfig = configReader.loadConfiguration("service-configuration.yml");
        testConfig.setDiscoveryServiceUrls(Collections.singletonList("https://localhost:10011/eureka"));
        configModifier.accept(testConfig);
        return testConfig;
    }

    @Test
    void givenSystemProperties_whenLoadFromFile_thenNoOverrideBySystemProp() throws Exception {
        System.setProperty("apiml.serviceId", "veronica");

        String internalFileName = "/service-configuration.yml";

        ApiMediationServiceConfig testConfig = configReader.loadConfiguration(internalFileName);
        validator.validate(testConfig);

        assertEquals("service", testConfig.getServiceId()); // no replace without wildcard
    }

    @Test
    void givenSystemProperties_whenLoadFromContext_thenNotOverrideBySystemProp() throws Exception {
        System.setProperty("apiml.serviceId", "veronica");
        ServletContext context = new MockServletContext();

        ApiMediationServiceConfigReader apiMediationServiceConfigReader = new ApiMediationServiceConfigReader();
        ApiMediationServiceConfig testConfig = apiMediationServiceConfigReader.loadConfiguration(context);
        validator.validate(testConfig);

        assertEquals("service", testConfig.getServiceId()); // no replace without wildcard
    }

    @Test
    void givenSystemProperties_whenLoadFromFileThatHasWildcardButPropsNotSetForMandatory_thenThrowException() throws Exception {
        // ssl.keystore has wildcard but is not set, exception will be thrown
        System.setProperty("apiml.serviceId", "veronica");
        System.clearProperty("apiml.keystore");
        ApiMediationServiceConfig testConfig = configReader.loadConfiguration("service-configuration-wildcard.yml");
        Exception exception = assertThrows(MetadataValidationException.class,
            () -> validator.validate(testConfig),
            "Expected exception is not MetadataValidationException");
        assertEquals("SSL parameters ** keyStore ** are missing or were not replaced by the system properties.", exception.getMessage());
    }

    @Test
    void givenSystemProperties_whenLoadFromFileThatHasWildcard_thenConfigOverriddenBySystemProp() throws Exception {
        System.setProperty("apiml.serviceId", "veronica");
        System.setProperty("prefix.description", "samantha");
        System.setProperty("apiml.keystore", "keystore");

        ApiMediationServiceConfig testConfig = configReader.loadConfiguration("service-configuration-wildcard.yml");
        validator.validate(testConfig);

        assertEquals("veronica", testConfig.getServiceId());    // wildcard is mandatory to replace
        assertEquals("${prefix.description}", testConfig.getDescription());  // it allows you to specify arbitrary prefix, yet only apiml prefix is replaced
        assertEquals("${apiml.title}", testConfig.getTitle());  // it leaves the unchanged prefixes
    }

    @Test
    void givenConfigurationEmptyCatalog_whenValidate_thenLog() throws Exception {
        ApiMediationServiceConfig testConfig = configReader.loadConfiguration("empty-catalog-service-configuration.yml");
        validator.validate(testConfig);

        assertNull(testConfig.getCatalog());
        assertTrue(logTracker.contains("The API Catalog UI tile configuration is not provided. Try to add apiml.service.catalog.tile section.", Level.WARN));
    }

    @Test
    void givenConfigurationEmptyApiInfo_whenValidate_thenLog() throws Exception {
        ApiMediationServiceConfig testConfig = configReader.loadConfiguration("empty-apiinfo-service-configuration.yml");
        validator.validate(testConfig);

        assertNull(testConfig.getApiInfo());
        assertTrue(logTracker.contains("The API info configuration is not provided. Try to add apiml.service.apiInfo section.", Level.WARN));
    }

    @Test
    void givenConfigurationWithKeyring_whenOtherConfigurationIsValid_thenValidate() throws Exception {
        ApiMediationServiceConfig testConfig = configReader.loadConfiguration("keyring-ssl-configuration.yml");
        assertDoesNotThrow(() -> validator.validate(testConfig));
    }

    @Test
    void givenConfigurationWithBadUrlSlashes_whenValidate_thenLogWarnings() throws Exception {
        ApiMediationServiceConfig testConfig = configReader.loadConfiguration("bad-url-slash-formatting.yml");
        assertDoesNotThrow(() -> validator.validate(testConfig));

        assertTrue(logTracker.contains("Relative URL parameters ** homePageRelativeUrl, healthCheckRelativeUrl, statusPageRelativeUrl, contextPath ** don't begin with '/' which often causes malformed URLs.", Level.WARN));
        assertTrue(logTracker.contains("The baseUrl parameter ends with a trailing '/'. This often causes malformed URLs when relative URLs are used.", Level.WARN));
        assertTrue(logTracker.contains("The contextPath parameter ends with a trailing '/'. This often causes malformed URLs when relative URLs are used.", Level.WARN));
    }

    @Test
    void givenConfigurationWithNoRelativeUrls_whenValidate_thenOnlyLogHomePageWarning() throws Exception {
        ApiMediationServiceConfig testConfig = configReader.loadConfiguration("no-relative-urls.yml");
        assertDoesNotThrow(() -> validator.validate(testConfig));

        assertTrue(logTracker.contains("The home page URL is not provided. Try to add apiml.service.homePageRelativeUrl property or check its value."));
        assertEquals(1, logTracker.getAllLoggedEventsWithLevel(Level.WARN).size());
    }

    @Test
    void givenConfigurationWithNoHomeUrlAndNoUiRoute_whenValidate_thenLogNoWarnings() throws Exception {
        ApiMediationServiceConfig testConfig = configReader.loadConfiguration("no-home-page-or-ui-route.yml");
        assertDoesNotThrow(() -> validator.validate(testConfig));

        assertEquals(0, logTracker.getAllLoggedEventsWithLevel(Level.WARN).size());
    }
}

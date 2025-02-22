#!/bin/sh

################################################################################
# This program and the accompanying materials are made available under the terms of the
# Eclipse Public License v2.0 which accompanies this distribution, and is available at
# https://www.eclipse.org/legal/epl-v20.html
#
# SPDX-License-Identifier: EPL-2.0
#
# Copyright IBM Corporation 2019, 2020
################################################################################

# Variables required on shell:
# - ZOWE_PREFIX
# - DISCOVERY_PORT - the port the discovery service will use
# - CATALOG_PORT - the port the api catalog service will use
# - GATEWAY_PORT - the port the api gateway service will use
# - VERIFY_CERTIFICATES - boolean saying if we accept only verified certificates
# - DISCOVERY_PORT - The port the data sets server will use
# - KEY_ALIAS
# - KEYSTORE - The keystore to use for SSL certificates
# - KEYSTORE_TYPE - The keystore type to use for SSL certificates
# - KEYSTORE_PASSWORD - The password to access the keystore supplied by KEYSTORE
# - KEY_ALIAS - The alias of the key within the keystore
# - ALLOW_SLASHES - Allows encoded slashes on on URLs through gateway
# - ZOWE_MANIFEST - The full path to Zowe's manifest.json file

if [[ -z "${LAUNCH_COMPONENT}" ]]
then
  # component should be started from component home directory
  LAUNCH_COMPONENT=$(pwd)/bin
fi

JAR_FILE="${LAUNCH_COMPONENT}/gateway-service-lite.jar"
# script assumes it's in the gateway component directory and common_lib needs to be relative path
if [[ -z ${CMMN_LB} ]]
then
    COMMON_LIB="../apiml-common-lib/bin/api-layer-lite-lib-all.jar"
else
    COMMON_LIB=${CMMN_LB}
fi

if [[ -z ${LIBRARY_PATH} ]]
then
    LIBRARY_PATH="../common-java-lib/bin/"
fi

# API Mediation Layer Debug Mode
export LOG_LEVEL=

if [[ ! -z ${APIML_DEBUG_MODE_ENABLED} && ${APIML_DEBUG_MODE_ENABLED} == true ]]
then
  export LOG_LEVEL="debug"
fi

DIAG_MODE=${APIML_DIAG_MODE_ENABLED}
if [ ! -z "$DIAG_MODE" ]
then
    LOG_LEVEL=$DIAG_MODE
fi

if [[ -z ${APIML_GATEWAY_CATALOG_ID} ]]
then
    APIML_GATEWAY_CATALOG_ID="apicatalog"
fi

if [ ${APIML_GATEWAY_CATALOG_ID} = "none" ]
then
    APIML_GATEWAY_CATALOG_ID=""
fi

if [ `uname` = "OS/390" ]; then
    QUICK_START=-Xquickstart
    GATEWAY_LOADER_PATH=${COMMON_LIB},/usr/include/java_classes/IRRRacf.jar
else
    GATEWAY_LOADER_PATH=${COMMON_LIB}
fi

# Check if the directory containing the Gateway shared JARs was set and append it to the GW loader path
if [[ ! -z ${ZWE_GATEWAY_SHARED_LIBS} ]]
then
    GATEWAY_LOADER_PATH=${ZWE_GATEWAY_SHARED_LIBS},${GATEWAY_LOADER_PATH}
fi

EXPLORER_HOST=${ZOWE_EXPLORER_HOST:-localhost}
GATEWAY_SERVICE_PORT=${GATEWAY_PORT:-7554}

echo "Setting loader path: "${GATEWAY_LOADER_PATH}

LIBPATH="$LIBPATH":"/lib"
LIBPATH="$LIBPATH":"/usr/lib"
LIBPATH="$LIBPATH":"${JAVA_HOME}"/bin
LIBPATH="$LIBPATH":"${JAVA_HOME}"/bin/classic
LIBPATH="$LIBPATH":"${JAVA_HOME}"/bin/j9vm
LIBPATH="$LIBPATH":"${JAVA_HOME}"/lib/s390/classic
LIBPATH="$LIBPATH":"${JAVA_HOME}"/lib/s390/default
LIBPATH="$LIBPATH":"${JAVA_HOME}"/lib/s390/j9vm
LIBPATH="$LIBPATH":"${LIBRARY_PATH}"

GATEWAY_CODE=AG
_BPX_JOBNAME=${ZOWE_PREFIX}${GATEWAY_CODE} java \
    -Xms32m -Xmx256m \
    ${QUICK_START} \
    -Dibm.serversocket.recover=true \
    -Dfile.encoding=UTF-8 \
    -Djava.io.tmpdir=/tmp \
    -Dspring.profiles.active=${APIML_SPRING_PROFILES:-} \
    -Dspring.profiles.include=$LOG_LEVEL \
    -Dapiml.service.hostname=${EXPLORER_HOST} \
    -Dapiml.service.port=${GATEWAY_SERVICE_PORT} \
    -Dapiml.service.discoveryServiceUrls=${ZWE_DISCOVERY_SERVICES_LIST:-"https://${EXPLORER_HOST}:${DISCOVERY_PORT:-7553}/eureka/"} \
    -Dapiml.service.preferIpAddress=${APIML_PREFER_IP_ADDRESS:-false} \
    -Dapiml.service.allowEncodedSlashes=${APIML_ALLOW_ENCODED_SLASHES:-true} \
    -Dapiml.service.corsEnabled=${APIML_CORS_ENABLED:-false} \
    -Dapiml.catalog.serviceId=${APIML_GATEWAY_CATALOG_ID:-apicatalog} \
    -Dapiml.cache.storage.location=${WORKSPACE_DIR}/api-mediation/ \
    -Dapiml.logs.location=${WORKSPACE_DIR}/api-mediation/logs \
    -Dapiml.service.ipAddress=${ZOWE_IP_ADDRESS:-127.0.0.1} \
    -Dapiml.gateway.timeoutMillis=${APIML_GATEWAY_TIMEOUT_MILLIS:-600000} \
    -Dapiml.security.ssl.verifySslCertificatesOfServices=${VERIFY_CERTIFICATES:-false} \
    -Dapiml.security.ssl.nonStrictVerifySslCertificatesOfServices=${NONSTRICT_VERIFY_CERTIFICATES:-false} \
    -Dapiml.security.auth.zosmf.serviceId=${APIML_ZOSMF_ID:-zosmf} \
    -Dapiml.security.auth.provider=${APIML_SECURITY_AUTH_PROVIDER:-zosmf} \
    -Dapiml.zoweManifest=${ZOWE_MANIFEST} \
    -Dserver.address=0.0.0.0 \
    -Dserver.maxConnectionsPerRoute=${APIML_MAX_CONNECTIONS_PER_ROUTE:-100} \
    -Dserver.maxTotalConnections=${APIML_MAX_TOTAL_CONNECTIONS:-1000} \
    -Dserver.ssl.enabled=${APIML_SSL_ENABLED:-true} \
    -Dserver.ssl.keyStore="${KEYSTORE}" \
    -Dserver.ssl.keyStoreType="${KEYSTORE_TYPE:-PKCS12}" \
    -Dserver.ssl.keyStorePassword="${KEYSTORE_PASSWORD}" \
    -Dserver.ssl.keyAlias="${KEY_ALIAS}" \
    -Dserver.ssl.keyPassword="${KEYSTORE_PASSWORD}" \
    -Dserver.ssl.trustStore="${TRUSTSTORE}" \
    -Dserver.ssl.trustStoreType="${KEYSTORE_TYPE:-PKCS12}" \
    -Dserver.ssl.trustStorePassword="${KEYSTORE_PASSWORD}" \
    -Dserver.internal.enabled=${APIML_GATEWAY_INTERNAL_ENABLED:-false} \
    -Dserver.internal.ssl.enabled=${APIML_SSL_ENABLED:-true} \
    -Dserver.internal.port=${APIML_GATEWAY_INTERNAL_PORT:-10017} \
    -Dserver.internal.ssl.keyAlias=${APIML_GATEWAY_INTERNAL_SSL_KEY_ALIAS:-localhost-multi} \
    -Dserver.internal.ssl.keyStore=${APIML_GATEWAY_INTERNAL_SSL_KEYSTORE:-keystore/localhost/localhost-multi.keystore.p12} \
    -Dapiml.security.auth.zosmf.jwtAutoconfiguration=${APIML_SECURITY_ZOSMF_JWT_AUTOCONFIGURATION_MODE:-auto} \
    -Dapiml.security.x509.enabled=${APIML_SECURITY_X509_ENABLED:-false} \
    -Dapiml.security.x509.externalMapperUrl=${APIML_GATEWAY_EXTERNAL_MAPPER:-"https://${EXPLORER_HOST}:${GATEWAY_SERVICE_PORT}/zss/api/v1/certificate/x509/map"} \
    -Dapiml.security.x509.externalMapperUser=${APIML_GATEWAY_MAPPER_USER:-ZWESVUSR} \
    -Dapiml.security.authorization.provider=${APIML_SECURITY_AUTHORIZATION_PROVIDER:-} \
    -Dapiml.security.authorization.endpoint.enabled=${APIML_SECURITY_AUTHORIZATION_ENDPOINT_ENABLED:-false} \
    -Dapiml.security.authorization.endpoint.url=${APIML_SECURITY_AUTHORIZATION_ENDPOINT_URL:-"https://${EXPLORER_HOST}:${GATEWAY_SERVICE_PORT}/zss/api/v1/saf-auth"} \
    -Dapiml.security.authorization.resourceClass=${RESOURCE_CLASS:-ZOWE} \
    -Dapiml.security.authorization.resourceNamePrefix=${RESOURCE_NAME_PREFIX:-APIML.} \
    -Dapiml.security.zosmf.applid=${APIML_SECURITY_ZOSMF_APPLID:-IZUDFLT} \
    -Djava.protocol.handler.pkgs=com.ibm.crypto.provider \
    -Dloader.path=${GATEWAY_LOADER_PATH} \
    -Djava.library.path=${LIBPATH} \
    -jar ${JAR_FILE} &

pid=$!
echo "pid=${pid}"

wait %1

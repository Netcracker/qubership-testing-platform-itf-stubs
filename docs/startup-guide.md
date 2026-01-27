# Qubership ATP-ITF-STUBS Startup Guide

## How to start backend locally

1. Clone repository
   `git clone <atp-itf-stubs repository url>`


2. Build the project
   `mvn -P github clean install`


3. Change default configuration [`.run/backend.run.xml`](../.run/backend.run.xml)

   * Go to Run menu and click Edit Configuration
   * Set parameters
   * Add the following parameters in VM options - click Modify Options and select "Add VM Options":

**NOTE:** Configuration file [`application.properties`](../stubs-common/application.properties)

**NOTE:** Configuration logging file [`logback-spring.xml`](../stubs-common/logback-spring.xml)

```properties
##==============================Undertow===============================
server.port=${HTTP_PORT}
embedded.https.enabled=${EMBEDDED_HTTPS_ENABLED}
embedded.tls.1.2.server.port=${EMBEDDED_TLS12_SERVER_PORT}
embedded.tls.1.3.server.port=${EMBEDDED_TLS13_SERVER_PORT}
embedded.ssl.server.port=${EMBEDDED_SSL_SERVER_PORT}
keystore.file=${KEYSTORE_FILE}
keystore.password=${KEYSTORE_PASSWORD}
# Undertow session timeout (in minutes) before authorization is expired
server.servlet.session.timeout=${UNDERTOW_SESSION_TIMEOUT}
server.compression.enabled=${UNDERTOW_COMPRESSION_ENABLED}
server.compression.mime-types=${UNDERTOW_COMPRESSION_MIMETYPE}
server.undertow.threads.io=${SERVER_UNDERTOW_IO_THREADS}
server.undertow.threads.worker=${SERVER_UNDERTOW_WORKER_THREADS}
server.undertow.accesslog.enabled=${SERVER_UNDERTOW_ACCESSLOG_ENABLED}
jboss.threads.eqe.statistics=${JBOSS_THREADS_EQE_STATISTICS}
##===============================ATP ITF===============================
logging.level.root=${LOG_LEVEL}
atp.integration.enabled=${ATP_INTEGRATION_ENABLED}
#Parameter for Java GC to avoid the uncontrolled memory consumption
max.ram.size=${MAX_RAM_SIZE}
trigger.folder=${TRIGGER_FOLDER}
trigger.lib=${TRIGGER_CUSTOM_LIB_FOLDER}
lock.provider.process.timeout=${LOCK_PROVIDER_PROCESS_TIMEOUT}
##===============================Triggers activation at startup===============================
start.transport.triggers.at.startup=${START_TRANSPORT_TRIGGERS_AT_STARTUP}
triggers.activation.sync=${TRIGGERS_ACTIVATION_SYNC}
##==================Integration with Spring Cloud======================
spring.application.name=${SERVICE_NAME}
eureka.client.enabled=${EUREKA_CLIENT_ENABLED}
eureka.client.serviceUrl.defaultZone=${SERVICE_REGISTRY_URL}
##==================atp-auth-spring-boot-starter=======================
spring.profiles.active=${SPRING_PROFILES}
keycloak.enabled=${KEYCLOAK_ENABLED}
##==================Keycloak===========================================
keycloak.resource=${KEYCLOAK_CLIENT_NAME}
keycloak.credentials.secret=${KEYCLOAK_SECRET}
keycloak.realm=${KEYCLOAK_REALM}
keycloak.auth-server-url=${KEYCLOAK_AUTH_URL}
##==================ATP-AUTH===========================================
atp-auth.project_info_endpoint=${PROJECT_INFO_ENDPOINT}
atp-auth.headers.content-security-policy=${CONTENT_SECURITY_POLICY}
##=============================Feign===================================
## public-gateway
atp.public.gateway.url=${ATP_PUBLIC_GATEWAY_URL}
atp.service.public=${ATP_SERVICE_PUBLIC}
atp.service.path=${ATP_SERVICE_PATH}
## internal-gateway
atp.internal.gateway.url=${ATP_INTERNAL_GATEWAY_URL}
atp.internal.gateway.enabled=${ATP_INTERNAL_GATEWAY_ENABLED}
atp.internal.gateway.name=${ATP_INTERNAL_GATEWAY_NAME}
feign.httpclient.disableSslValidation=${FEIGN_HTTPCLIENT_DISABLE_SSL}
feign.httpclient.enabled=${FEIGN_HTTPCLIENT_ENABLED}
feign.okhttp.enabled=${FEIGN_OKHTTP_ENABLED}
## datasets
feign.atp.datasets.name=${FEIGN_ATP_DATASETS_NAME}
feign.atp.datasets.url=${FEIGN_ATP_DATASETS_URL}
feign.atp.datasets.route=${FEIGN_ATP_DATASETS_ROUTE}
## bulk validator
feign.atp.bv.name=${FEIGN_ATP_BV_NAME}
feign.atp.bv.url=${FEIGN_ATP_BV_URL}
feign.atp.bv.route=${FEIGN_ATP_BV_ROUTE}
## environments
feign.atp.environments.name=${FEIGN_ATP_ENVIRONMENTS_NAME}
feign.atp.environments.url=${FEIGN_ATP_ENVIRONMENTS_URL}
feign.atp.environments.route=${FEIGN_ATP_ENVIRONMENTS_ROUTE}
## catalogue
feign.atp.catalogue.name=${FEIGN_ATP_CATALOGUE_NAME}
feign.atp.catalogue.route=${FEIGN_ATP_CATALOGUE_ROUTE}
feign.atp.catalogue.url=${FEIGN_ATP_CATALOGUE_URL}
## users
feign.atp.users.url=${FEIGN_ATP_USERS_URL}
feign.atp.users.name=${FEIGN_ATP_USERS_NAME}
feign.atp.users.route=${FEIGN_ATP_USERS_ROUTE}
## itf executor
feign.atp.executor.name=${FEIGN_ATP_ITF_EXECUTOR_NAME}
feign.atp.executor.url=${FEIGN_ATP_ITF_EXECUTOR_URL}
feign.atp.executor.route=${FEIGN_ATP_ITF_EXECUTOR_ROUTE}
##=============================ActiveMQ================================
message-broker.executor-stubs-sync.topic=${EXECUTOR_STUBS_SYNC_TOPIC}
message-broker.configurator-stubs.topic=${CONFIGURATOR_STUBS_TOPIC}
message-broker.stubs-configurator.topic=${STUBS_CONFIGURATOR_TOPIC}
message-broker.eds-update.topic=${EDS_UPDATE_TOPIC}
message-broker.stubs-executor-incoming-request.queue=${STUBS_EXECUTOR_INCOMING_QUEUE}
message-broker.executor-stubs-outgoing-response.queue=${EXECUTOR_STUBS_OUTGOING_QUEUE}
message-broker.executor-stubs.listenerContainerFactory.concurrency=${EXECUTOR_STUBS_CONCURRENCY}
message-broker.executor-stubs.listenerContainerFactory.maxMessagesPerTask=${EXECUTOR_STUBS_MAX_MESSAGES_PER_TASK}
message-broker.reports.queue=${REPORT_QUEUE}
message-broker.reports.message-time-to-live=${REPORTS_MESSAGES_TTL}
message-broker.reports.useCompression=${REPORT_USE_COMPRESSION}
message-broker.reports.useAsyncSend=${REPORT_USE_ASYNC_SEND}
message-broker.reports.maxThreadPoolSize=${REPORT_MAX_THREAD_POOL_SIZE}
message-broker.stubs-configurator.message-time-to-live=${STUBS_CONFIGURATOR_TOPIC_MESSAGES_TTL}
message-broker.stubs-executor-incoming-request.message-time-to-live=${STUBS_EXECUTOR_INCOMING_REQUEST_QUEUE_MESSAGES_TTL}
message-broker.url=${ATP_ITF_BROKER_URL_TCP}
##======================ATP Services integration=======================
# DataSets tool integration settings
dataset.service.url=${ATP_DATASET_URL}
##=============================File Uploader Settings=============================
eds.gridfs.enabled=${EDS_GRIDFS_ENABLED}
eds.gridfs.host=${MONGO_DB_ADDR}
eds.gridfs.port=${MONGO_DB_PORT}
eds.gridfs.database=${EDS_GRIDFS_DB}
eds.gridfs.username=${EDS_GRIDFS_USER}
eds.gridfs.password=${EDS_GRIDFS_PASSWORD}
spring.servlet.multipart.max-file-size=${SPRING_SERVLET_MULTIPART_MAX_FILE_SIZE}
spring.servlet.multipart.max-request-size=${SPRING_SERVLET_MULTIPART_MAX_REQUEST_SIZE}
##=================Monitoring==========================================
management.server.port=${MONITOR_PORT}
management.endpoints.web.exposure.include=${MONITOR_WEB_EXPOSE}
management.endpoints.web.base-path=${MONITOR_WEB_BASE}
management.endpoints.web.path-mapping.prometheus=${MONITOR_WEB_MAP_PROM}
##===============Hibernate-multi-tenancy=================================
atp.multi-tenancy.enabled=${MULTI_TENANCY_HIBERNATE_ENABLED}
##=================== Swagger =======================
springdoc.api-docs.enabled=${SWAGGER_ENABLED}
##==============Hazelcast==========================================================
hazelcast.cache.enabled=${HAZELCAST_CACHE_ENABLED}
hazelcast.address=${HAZELCAST_ADDRESS}
##==================Zipkin=====================
spring.sleuth.enabled=${ZIPKIN_ENABLE}
spring.sleuth.sampler.probability=${ZIPKIN_PROBABILITY}
spring.zipkin.baseUrl=${ZIPKIN_URL}
```

5. Click `Apply` and `OK`

6. Run the project

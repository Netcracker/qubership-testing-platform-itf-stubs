# Qubership ATP-ITF-STUBS Installation Guide

## Third-party dependencies

| Name       | Version | Mandatory/Optional | Comment                |
|------------|---------|--------------------|------------------------|
| PostgreSQL | 14+     | Mandatory          | JDBC connection string |
| GridFS     | 4.2+    | Mandatory          | For storing files      |

## HWE

|                  | CPU request | CPU limit | RAM request | RAM limit |
|------------------|-------------|-----------|-------------|-----------|
| Dev level        | 50m         | 500m      | 300Mi       | 1500Mi    |
| Production level | 50m         | 1500m     | 3Gi         | 3Gi       |

## Minimal parameters set

```properties
-DHTTP_PORT
-DKEYSTORE_FILE
-DKEYSTORE_PASSWORD
-DTRIGGER_FOLDER
-DSPRING_PROFILES=default
-DKEYCLOAK_ENABLED
-DKEYCLOAK_CLIENT_NAME
-DKEYCLOAK_SECRET
-DKEYCLOAK_REALM
-DKEYCLOAK_AUTH_URL
-DEXECUTOR_STUBS_SYNC_TOPIC
-DCONFIGURATOR_STUBS_TOPIC
-DSTUBS_CONFIGURATOR_TOPIC
-DEDS_UPDATE_TOPIC
-DSTUBS_EXECUTOR_INCOMING_QUEUE
-DEXECUTOR_STUBS_OUTGOING_QUEUE
-DREPORT_QUEUE
-DATP_ITF_BROKER_URL_TCP
```

### Full ENV VARs list per container

| Deploy Parameter Name                                | Mandatory | Example                                                                                                          | Description                                                        |
|------------------------------------------------------|-----------|------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------|
| `HTTP_PORT`                                          | Yes       | 8080                                                                                                             | Server port number                                                 |
| `EMBEDDED_HTTPS_ENABLED`                             | No        | true                                                                                                             | Enable or disable support https                                    |
| `EMBEDDED_TLS12_SERVER_PORT`                         | No        | 9443                                                                                                             | TLS 1.2 server port number                                         |
| `EMBEDDED_TLS13_SERVER_PORT`                         | No        | 10443                                                                                                            | TLS 1.3 server port number                                         |
| `EMBEDDED_SSL_SERVER_PORT`                           | No        | 8443                                                                                                             | SSL server port number                                             |
| `KEYSTORE_FILE`                                      | Yes       | keystore.jks                                                                                                     | Keystore filename                                                  |
| `KEYSTORE_PASSWORD`                                  | Yes       | changeit                                                                                                         | Keystore password value                                            |
| `UNDERTOW_SESSION_TIMEOUT`                           | No        | 58m                                                                                                              | Server servlet session timeout value                               |
| `UNDERTOW_COMPRESSION_ENABLED`                       | No        | false                                                                                                            | Enable or disable undertow server compression                      |
| `UNDERTOW_COMPRESSION_MIMETYPE`                      | No        | `text/html,text/xml,text/plain,text/css,text/javascript,application/javascript,application/json,application/xml` | Undertow server compression mime-types value                       |
| `SERVER_UNDERTOW_IO_THREADS`                         | No        | 4                                                                                                                | Undertow server threads io number                                  |
| `SERVER_UNDERTOW_WORKER_THREADS`                     | No        | 32                                                                                                               | Undertow server threads worker number                              |
| `SERVER_UNDERTOW_ACCESSLOG_ENABLED`                  | No        | true                                                                                                             | Enable or disable undertow server accesslog                        |
| `JBOSS_THREADS_EQE_STATISTICS`                       | No        | true                                                                                                             | Jboss threads statistics                                           |
| `LOG_LEVEL`                                          | No        | INFO                                                                                                             | Logging level value                                                |
| `ATP_INTEGRATION_ENABLED`                            | No        | false                                                                                                            | Enable or disable atp integration                                  |
| `MAX_RAM_SIZE`                                       | No        | 2560m                                                                                                            | Max ram size value                                                 |
| `TRIGGER_FOLDER`                                     | Yes       | ./triggers                                                                                                       | Trigger lib folder name                                            |
| `TRIGGER_CUSTOM_LIB_FOLDER`                          | No        | -                                                                                                                | Trigger custom lib folder name                                     |
| `LOCK_PROVIDER_PROCESS_TIMEOUT`                      | No        | 60000                                                                                                            | Lock provider process timeout                                      |
| `START_TRANSPORT_TRIGGERS_AT_STARTUP`                | No        | true                                                                                                             | Enable or disable activate transport triggers at startup           |
| `TRIGGERS_ACTIVATION_SYNC`                           | No        | true                                                                                                             | Enable or disable activation sync                                  |
| `TEST_SERVER_AVAILABILITY`                           | No        | false                                                                                                            | Test server availability                                           |
| `SERVICE_NAME`                                       | No        | atp-itf-reporting                                                                                                | Service system name                                                |
| `EUREKA_CLIENT_ENABLED`                              | No        | false                                                                                                            | Enable or disable eureka integration                               |
| `SERVICE_REGISTRY_URL`                               | No        | [Registry URL](http://atp-registry-service:8761/eureka)                                                          | Eureka serviceUrl defaultZone value                                |
| `SPRING_PROFILES`                                    | Yes       | default                                                                                                          | Spring active profiles                                             |
| `KEYCLOAK_ENABLED`                                   | Yes       | false                                                                                                            | Enable or disable Keycloak integration                             |
| `KEYCLOAK_CLIENT_NAME`                               | Yes       | atp2                                                                                                             | Keycloak resource name                                             |
| `KEYCLOAK_SECRET`                                    | Yes       | f3e17149-94d0-47ed-a5b7-744c332fdf66                                                                             | keycloak secret    value                                           |
| `KEYCLOAK_REALM`                                     | Yes       | atp2                                                                                                             | Keycloak realm name                                                |
| `KEYCLOAK_AUTH_URL`                                  | Yes       | localhost                                                                                                        | Keycloak auth URL                                                  |
| `PROJECT_INFO_ENDPOINT`                              | No        | /api/v1/users/projects                                                                                           | Project metadata API endpoint                                      |
| `CONTENT_SECURITY_POLICY`                            | No        | default-src 'self' 'unsafe-inline' *                                                                             | Security policy settings for frontend                              |
| `ATP_PUBLIC_GATEWAY_URL`                             | No        | [Public Gateway URL](http://atp-public-gateway-service-address)                                                  | Public gateway URL                                                 |
| `ATP_INTERNAL_GATEWAY_URL`                           | No        | [Internal Gateway URL](http://atp-internal-gateway:8080)                                                         | Internal gateway URL                                               |
| `ATP_INTERNAL_GATEWAY_ENABLED`                       | No        | false                                                                                                            | Enable or disable Internal gateway                                 |
| `ATP_INTERNAL_GATEWAY_NAME`                          | No        | atp-internal-gateway                                                                                             | Internal gateway name                                              |
| `FEIGN_HTTPCLIENT_DISABLE_SSL`                       | No        | true                                                                                                             | Feign enable or disable SSL validation                             |
| `FEIGN_HTTPCLIENT_ENABLED`                           | No        | true                                                                                                             | Enable or disable feign                                            |
| `FEIGN_OKHTTP_ENABLED`                               | No        | true                                                                                                             | Enable or disable feign okhttp                                     |
| `ATP_SERVICE_PUBLIC`                                 | No        | true                                                                                                             | Enable or disable service public                                   |
| `ATP_SERVICE_PATH`                                   | No        | /api/atp-itf-stubs/v1/**                                                                                         | Service  path                                                      |
| `FEIGN_ATP_DATASETS_NAME`                            | No        | ATP-DATASETS                                                                                                     | Feign atp-dataset client name                                      |
| `FEIGN_ATP_DATASETS_URL`                             | No        | -                                                                                                                | Feign atp-dataset client URL                                       |
| `FEIGN_ATP_DATASETS_ROUTE`                           | No        | api/atp-datasets/v1                                                                                              | Feign atp-dataset client route                                     |
| `FEIGN_ATP_BV_NAME`                                  | No        | ATP-BV                                                                                                           | Feign atp-bv client name                                           |
| `FEIGN_ATP_BV_URL`                                   | No        | -                                                                                                                | Feign atp-bv client URL                                            |
| `FEIGN_ATP_BV_ROUTE`                                 | No        | api/bvtool/v1                                                                                                    | Feign atp-bv client route                                          |
| `FEIGN_ATP_ENVIRONMENTS_NAME`                        | No        | ATP-ENVIRONMENTS                                                                                                 | Feign atp-environments client name                                 |
| `FEIGN_ATP_ENVIRONMENTS_URL`                         | No        | -                                                                                                                | Feign atp-environments client URL                                  |
| `FEIGN_ATP_ENVIRONMENTS_ROUTE`                       | No        | api/atp-environments/v1                                                                                          | Feign atp-environments client route                                |
| `FEIGN_ATP_CATALOGUE_NAME`                           | No        | ATP-CATALOGUE                                                                                                    | Feign atp-catalogue client name                                    |
| `FEIGN_ATP_CATALOGUE_ROUTE`                          | No        | -                                                                                                                | Feign atp-catalogue client URL                                     |
| `FEIGN_ATP_CATALOGUE_URL`                            | No        | api/atp-catalogue/v1                                                                                             | Feign atp-catalogue client route                                   |
| `FEIGN_ATP_USERS_URL`                                | No        | ATP-USERS-BACKEND                                                                                                | Feign atp-users-backend client name                                |
| `FEIGN_ATP_USERS_NAME`                               | No        | -                                                                                                                | Feign atp-users-backend client URL                                 |
| `FEIGN_ATP_USERS_ROUTE`                              | No        | api/atp-users-backend/v1                                                                                         | Feign atp-users-backend client route                               |
| `FEIGN_ATP_ITF_EXECUTOR_NAME`                        | No        | ATP-ITF-EXECUTOR                                                                                                 | Feign atp-itf-executor client name                                 |
| `FEIGN_ATP_ITF_EXECUTOR_URL`                         | No        | -                                                                                                                | Feign atp-itf-executor client URL                                  |
| `FEIGN_ATP_ITF_EXECUTOR_ROUTE`                       | No        | api/atp-itf-executor/v1                                                                                          | Feign atp-itf-executor client route                                |
| `FEIGN_CONNECT_TIMEOUT`                              | No        | 160000000                                                                                                        | Feign client default connect timeout value                         |
| `FEIGN_READ_TIMEOUT`                                 | No        | 160000000                                                                                                        | Feign client default read timeout value                            |
| `EXECUTOR_STUBS_SYNC_TOPIC`                          | Yes       | executor_stubs_sync                                                                                              | Executor stubs sync topic name                                     |
| `CONFIGURATOR_STUBS_TOPIC`                           | Yes       | configurator_stubs                                                                                               | Configurator stubs topic name                                      |
| `STUBS_CONFIGURATOR_TOPIC`                           | Yes       | stubs_configurator                                                                                               | Stubs configurator topic name                                      |
| `EDS_UPDATE_TOPIC`                                   | Yes       | eds_update                                                                                                       | Eds-update topic name                                              |
| `STUBS_EXECUTOR_INCOMING_QUEUE`                      | Yes       | stubs_executor_incoming_request                                                                                  | Stubs executor incoming request queue name                         |
| `EXECUTOR_STUBS_OUTGOING_QUEUE`                      | Yes       | executor_stubs_outgoing_response                                                                                 | Executor stubs outgoing response queue name                        |
| `EXECUTOR_STUBS_CONCURRENCY`                         | No        | 120-900                                                                                                          | Executor-stubs listener container factory concurrency value        |
| `EXECUTOR_STUBS_MAX_MESSAGES_PER_TASK`               | No        | -1                                                                                                               | Executor stubs listener container factory maxMessagesPerTask value |
| `REPORT_QUEUE`                                       | Yes       | ReportExecution                                                                                                  | Reports queue name                                                 |
| `REPORTS_MESSAGES_TTL`                               | No        | 1800000                                                                                                          | Message time-to-live for queue 'REPORT_QUEUE'                      |
| `REPORT_USE_COMPRESSION`                             | No        | true                                                                                                             | Enable or disable use compression                                  |
| `REPORT_USE_ASYNC_SEND`                              | No        | true                                                                                                             | Enable or disable use asyncSend                                    |
| `REPORT_MAX_THREAD_POOL_SIZE`                        | No        | 1200                                                                                                             | Reports maxThreadPoolSize value                                    |
| `STUBS_CONFIGURATOR_TOPIC_MESSAGES_TTL`              | No        | 180000                                                                                                           | Message time-to-live for topic 'stubs-configurator'                |
| `STUBS_EXECUTOR_INCOMING_REQUEST_QUEUE_MESSAGES_TTL` | No        | 180000                                                                                                           | Message time-to-live for queue 'stubs-executor-incoming-request'   |
| `ATP_ITF_BROKER_URL_TCP`                             | Yes       | tcp://atp-activemq:61616?wireFormat.maxInactivityDuration=0&wireFormat.maxFrameSize=104857600                    | Broker URL                                                         |
| `ATP_DATASET_URL`                                    | No        | [Dataset Service URL](https://atp-dataset-service-address)                                                       | Dataset service integration URL                                    |
| `EDS_GRIDFS_ENABLED`                                 | No        | true                                                                                                             | Enable or disable external data storage                            |
| `MONGO_DB_ADDR`                                      | No        | localhost                                                                                                        | External data storage database host address                        |
| `MONGO_DB_PORT`                                      | No        | 27017                                                                                                            | External data storage database port number                         |
| `EDS_GRIDFS_DB`                                      | No        | local_itf_gridfs                                                                                                 | External data storage database name                                |
| `EDS_GRIDFS_USER`                                    | No        | admin                                                                                                            | External data storage database username value                      |
| `EDS_GRIDFS_PASSWORD`                                | No        | admin                                                                                                            | External data storage database password value                      |
| `SPRING_SERVLET_MULTIPART_MAX_FILE_SIZE`             | No        | 256MB                                                                                                            | Spring servlet multipart max-file-size value                       |
| `SPRING_SERVLET_MULTIPART_MAX_REQUEST_SIZE`          | No        | 256MB                                                                                                            | Spring servlet multipart max-request-size value                    |
| `MONITOR_PORT`                                       | No        | 8090                                                                                                             | Metric server port number                                          |
| `MONITOR_WEB_EXPOSE`                                 | No        | prometheus,health,info,env                                                                                       | Metric endpoints exposure include                                  |
| `MONITOR_WEB_BASE`                                   | No        | /                                                                                                                | Metric endpoints base-path                                         |
| `MONITOR_WEB_MAP_PROM`                               | No        | metrics                                                                                                          | Metric endpoints path-mapping prometheus                           |
| `MULTI_TENANCY_HIBERNATE_ENABLED`                    | No        | false                                                                                                            | Enable or disable atp multi-tenancy integration                    |
| `SWAGGER_ENABLED`                                    | No        | true                                                                                                             | Enable or disable Swagger integration                              |
| `HAZELCAST_CACHE_ENABLED`                            | No        | true                                                                                                             | Enable or disable hazelcast cache                                  |
| `HAZELCAST_ADDRESS`                                  | No        | 127.0.0.1:5701                                                                                                   | Hazelcast address                                                  |
| `ZIPKIN_ENABLE`                                      | No        | false                                                                                                            | Enable or disable Zipkin distributed tracing                       |
| `ZIPKIN_PROBABILITY`                                 | No        | 1.0                                                                                                              | Zipkin probability level                                           |
| `ZIPKIN_URL`                                         | No        | [Zipkin URL](http://jaeger-app-collector.jaeger.svc:9411)                                                        | Zipkin host address                                                |

## Helm

### Prerequisites

1. Install k8s locally
2. Install Helm

### How to deploy tool

1. Build snapshot (artifacts and Docker image) of [ITF-Stubs Repository](https://github.com/Netcracker/qubership-testing-platform-itf-stubs) in GitHub
2. Clone repository to a place, available from your openshift/kubernetes where you need to deploy the tool to
3. Navigate to <repository-root>/deployments/charts/atp-itf-stubs folder
4. Check/change configuration parameters in the ./values.yaml file according to your services installed
5. Execute the command: `helm install atp-itf-stubs`
6. After installation is completed, check deployment health

{{/* Helper functions, do NOT modify */}}
{{- define "env.default" -}}
{{- $ctx := get . "ctx" -}}
{{- $def := get . "def" | default $ctx.Values.SERVICE_NAME -}}
{{- $pre := get . "pre" | default (eq $ctx.Values.PAAS_PLATFORM "COMPOSE" | ternary "" $ctx.Release.Namespace) -}}
{{- get . "val" | default ((empty $pre | ternary $def (print $pre "_" (trimPrefix "atp-" $def))) | nospace | replace "-" "_") -}}
{{- end -}}

{{- define "env.factor" -}}
{{- $ctx := get . "ctx" -}}
{{- get . "def" | default (eq $ctx.Values.PAAS_PLATFORM "COMPOSE" | ternary "1" (default "3" $ctx.Values.KAFKA_REPLICATION_FACTOR)) -}}
{{- end -}}

{{- define "env.compose" }}
{{- range $key, $val := merge (include "env.lines" . | fromYaml) (include "env.secrets" . | fromYaml) }}
{{ printf "- %s=%s" $key $val }}
{{- end }}
{{- end }}

{{- define "env.cloud" }}
{{- range $key, $val := (include "env.lines" . | fromYaml) }}
{{ printf "- name: %s" $key }}
{{ printf "  value: \"%s\"" $val }}
{{- end }}
{{- $keys := (include "env.secrets" . | fromYaml | keys | uniq | sortAlpha) }}
{{- range $keys }}
{{ printf "- name: %s" . }}
{{ printf "  valueFrom:" }}
{{ printf "    secretKeyRef:" }}
{{ printf "      name: %s-secrets" $.Values.SERVICE_NAME }}
{{ printf "      key: %s" . }}
{{- end }}
{{- end }}

{{- define "env.host" -}}
{{- $url := .Values.ATP_ITF_STUBS_URL -}}
{{- if $url -}}
{{- regexReplaceAll "http(s)?://(.*)" $url "${2}" -}}
{{- else -}}
{{- $hosts := dict "KUBERNETES" "dev-kubernetes-address" "OPENSHIFT" "dev-cloud-address" -}}
{{- print .Values.SERVICE_NAME "-" .Release.Namespace "." (.Values.CLOUD_PUBLIC_HOST | default (index $hosts .Values.PAAS_PLATFORM)) -}}
{{- end -}}
{{- end -}}
{{/* Helper functions end */}}

{{/* Environment variables to be used AS IS */}}
{{- define "env.lines" }}
ATP_DATASET_URL: "{{ .Values.ATP_DATASET_URL }}"
ATP_INTEGRATION_ENABLED: "{{ .Values.ATP_INTEGRATION_ENABLED }}"
ATP_INTERNAL_GATEWAY_ENABLED: "{{ .Values.ATP_INTERNAL_GATEWAY_ENABLED }}"
ATP_INTERNAL_GATEWAY_NAME: "atp-internal-gateway"
ATP_INTERNAL_GATEWAY_URL: "{{ .Values.ATP_INTERNAL_GATEWAY_URL }}"
ATP_ITF_BROKER_URL_TCP: "{{ .Values.ATP_ITF_BROKER_URL_TCP }}"
ATP_ITF_STUBS_URL: "{{ .Values.ATP_ITF_STUBS_URL }}"
ATP_PUBLIC_GATEWAY_URL: "{{ .Values.ATP_PUBLIC_GATEWAY_URL }}"
ATP_SERVICE_PUBLIC: "{{ .Values.ATP_SERVICE_PUBLIC }}"
CLOUD_NAMESPACE: "{{ .Release.Namespace }}"
CONFIGURATOR_STUBS_TOPIC: "{{ include "env.default" (dict "ctx" . "val" .Values.CONFIGURATOR_STUBS_TOPIC "def" "configurator_stubs") }}"
CONTENT_SECURITY_POLICY: "{{ .Values.CONTENT_SECURITY_POLICY }}"
EDS_GRIDFS_DB: "{{ include "env.default" (dict "ctx" . "val" .Values.EDS_GRIDFS_DB "def" "atp-itf-eds-gridfs") }}"
EDS_GRIDFS_ENABLED: "{{ .Values.EDS_GRIDFS_ENABLED }}"
EDS_UPDATE_TOPIC: "{{ include "env.default" (dict "ctx" . "val" .Values.EDS_UPDATE_TOPIC "def" "eds_update") }}"
EMBEDDED_HTTPS_ENABLED: "{{ .Values.EMBEDDED_HTTPS_ENABLED }}"
EUREKA_CLIENT_ENABLED: "{{ .Values.EUREKA_CLIENT_ENABLED }}"
EXECUTOR_STUBS_CONCURRENCY: "{{ .Values.EXECUTOR_STUBS_CONCURRENCY }}"
EXECUTOR_STUBS_MAX_MESSAGES_PER_TASK: "{{ .Values.EXECUTOR_STUBS_MAX_MESSAGES_PER_TASK }}"
EXECUTOR_STUBS_OUTGOING_QUEUE: "{{ include "env.default" (dict "ctx" . "val" .Values.EXECUTOR_STUBS_OUTGOING_QUEUE "def" "executor_stubs_outgoing_response") }}"
EXECUTOR_STUBS_SYNC_TOPIC: "{{ include "env.default" (dict "ctx" . "val" .Values.EXECUTOR_STUBS_SYNC_TOPIC "def" "executor_stubs_sync") }}"
FEIGN_ATP_CATALOGUE_NAME: "{{ .Values.FEIGN_ATP_CATALOGUE_NAME }}"
FEIGN_ATP_CATALOGUE_ROUTE: "{{ .Values.FEIGN_ATP_CATALOGUE_ROUTE }}"
FEIGN_ATP_CATALOGUE_URL: "{{ .Values.FEIGN_ATP_CATALOGUE_URL }}"
FEIGN_ATP_ITF_EXECUTOR_NAME: "{{ .Values.FEIGN_ATP_ITF_EXECUTOR_NAME }}"
FEIGN_ATP_ITF_EXECUTOR_ROUTE: "{{ .Values.FEIGN_ATP_ITF_EXECUTOR_ROUTE }}"
FEIGN_ATP_ITF_EXECUTOR_URL: "{{ .Values.FEIGN_ATP_ITF_EXECUTOR_URL }}"
FEIGN_ATP_USERS_NAME: "{{ .Values.FEIGN_ATP_USERS_NAME }}"
FEIGN_ATP_USERS_ROUTE: "{{ .Values.FEIGN_ATP_USERS_ROUTE }}"
FEIGN_ATP_USERS_URL: "{{ .Values.FEIGN_ATP_USERS_URL }}"
FEIGN_HTTPCLIENT_DISABLE_SSL: "true"
FEIGN_HTTPCLIENT_ENABLED: "false"
FEIGN_OKHTTP_ENABLED: "true"
GRAYLOG_HOST: "{{ .Values.GRAYLOG_HOST }}"
GRAYLOG_ON: "{{ .Values.GRAYLOG_ON }}"
GRAYLOG_PORT: "{{ .Values.GRAYLOG_PORT }}"
HAZELCAST_ADDRESS: "{{ .Values.HAZELCAST_ADDRESS }}"
HAZELCAST_CACHE_ENABLED: "{{ .Values.HAZELCAST_CACHE_ENABLED }}"
JAVA_OPTIONS: "{{ if .Values.HEAPDUMP_ENABLED }}-XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/tmp/diagnostic{{ end }} -Dbootstrap.servers={{ .Values.KAFKA_SERVERS }} -Dcom.sun.management.jmxremote={{ .Values.JMX_ENABLE }} -Dcom.sun.management.jmxremote.port={{ .Values.JMX_PORT }} -Dcom.sun.management.jmxremote.rmi.port={{ .Values.JMX_RMI_PORT }} -Djava.rmi.server.hostname=127.0.0.1 -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false -Dserver.undertow.threads.io={{ .Values.SERVER_UNDERTOW_IO_THREADS }} -Dserver.undertow.threads.worker={{ .Values.SERVER_UNDERTOW_WORKER_THREADS }} -Djboss.threads.eqe.statistics={{ .Values.JBOSS_THREADS_EQE_STATISTICS }} {{ .Values.ADDITIONAL_JAVA_OPTIONS }}"
KEYCLOAK_AUTH_URL: "{{ .Values.KEYCLOAK_AUTH_URL }}"
KEYCLOAK_ENABLED: "{{ .Values.KEYCLOAK_ENABLED }}"
KEYCLOAK_REALM: "{{ .Values.KEYCLOAK_REALM }}"
KEYSTORE_FILE: "{{ .Values.KEYSTORE_FILE }}"
LOCK_PROVIDER_PROCESS_TIMEOUT: "{{ .Values.LOCK_PROVIDER_PROCESS_TIMEOUT }}"
MAX_RAM_SIZE: "{{ .Values.MAX_RAM_SIZE }}"
MICROSERVICE_NAME: "{{ .Values.SERVICE_NAME }}"
MONGO_DB_ADDR: "{{ .Values.MONGO_DB_ADDR }}"
MONGO_DB_PORT: "{{ .Values.MONGO_DB_PORT }}"
MULTI_TENANCY_HIBERNATE_ENABLED: "{{ .Values.MULTI_TENANCY_HIBERNATE_ENABLED }}"
PROFILER_ENABLED: "{{ .Values.PROFILER_ENABLED }}"
PROJECT_INFO_ENDPOINT: "{{ .Values.PROJECT_INFO_ENDPOINT }}"
REMOTE_DUMP_HOST: "{{ .Values.REMOTE_DUMP_HOST }}"
REMOTE_DUMP_PORT: "{{ .Values.REMOTE_DUMP_PORT }}"
REPORT_QUEUE: "{{ include "env.default" (dict "ctx" . "val" .Values.REPORT_QUEUE "def" "ReportExecution") }}"
SERVER_UNDERTOW_ACCESSLOG_ENABLED: "{{ .Values.SERVER_UNDERTOW_ACCESSLOG_ENABLED }}"
SERVICE_REGISTRY_URL: "{{ .Values.SERVICE_REGISTRY_URL }}"
SPRING_PROFILES: "{{ .Values.SPRING_PROFILES }}"
SPRING_SERVLET_MULTIPART_MAX_FILE_SIZE: "{{ .Values.SPRING_SERVLET_MULTIPART_MAX_FILE_SIZE }}"
SPRING_SERVLET_MULTIPART_MAX_REQUEST_SIZE: "{{ .Values.SPRING_SERVLET_MULTIPART_MAX_REQUEST_SIZE }}"
START_TRANSPORT_TRIGGERS_AT_STARTUP: "{{ .Values.START_TRANSPORT_TRIGGERS_AT_STARTUP }}"
STUBS_CONFIGURATOR_TOPIC: "{{ include "env.default" (dict "ctx" . "val" .Values.STUBS_CONFIGURATOR_TOPIC "def" "stubs_configurator") }}"
STUBS_CONFIGURATOR_TOPIC_MESSAGES_TTL: "{{ .Values.STUBS_CONFIGURATOR_TOPIC_MESSAGES_TTL }}"
STUBS_EXECUTOR_INCOMING_QUEUE: "{{ include "env.default" (dict "ctx" . "val" .Values.STUBS_EXECUTOR_INCOMING_QUEUE "def" "stubs_executor_incoming_request") }}"
STUBS_EXECUTOR_INCOMING_REQUEST_QUEUE_MESSAGES_TTL: "{{ .Values.STUBS_EXECUTOR_INCOMING_REQUEST_QUEUE_MESSAGES_TTL }}"
SWAGGER_ENABLED: "{{ .Values.SWAGGER_ENABLED }}"
TEST_SERVER_AVAILABILITY: "{{ .Values.TEST_SERVER_AVAILABILITY }}"
TRIGGERS_ACTIVATION_SYNC: "{{ .Values.TRIGGERS_ACTIVATION_SYNC }}"
TRIGGER_CUSTOM_LIB_FOLDER: "{{ .Values.TRIGGER_CUSTOM_LIB_FOLDER }}"
UNDERTOW_COMPRESSION_ENABLED: "{{ .Values.UNDERTOW_COMPRESSION_ENABLED }}"
UNDERTOW_COMPRESSION_MIMETYPE: "{{ .Values.UNDERTOW_COMPRESSION_MIMETYPE }}"
UNDERTOW_SESSION_TIMEOUT: "{{ .Values.UNDERTOW_SESSION_TIMEOUT }}"
ZIPKIN_ENABLE: "{{ .Values.ZIPKIN_ENABLE }}"
ZIPKIN_PROBABILITY: "{{ .Values.ZIPKIN_PROBABILITY }}"
ZIPKIN_URL: "{{ .Values.ZIPKIN_URL }}"
{{- end }}

{{/* Sensitive data to be converted into secrets whenever possible */}}
{{- define "env.secrets" }}
KEYCLOAK_CLIENT_NAME: "{{ default "atp-itf" .Values.KEYCLOAK_CLIENT_NAME }}"
KEYSTORE_PASSWORD: "{{ .Values.KEYSTORE_PASSWORD }}"
KEYCLOAK_SECRET: "{{ default "71b6a213-e3b0-4bf4-86c8-dfe11ce9e248" .Values.KEYCLOAK_SECRET }}"
EDS_GRIDFS_PASSWORD: "{{ include "env.default" (dict "ctx" . "val" .Values.EDS_GRIDFS_PASSWORD "def" "atp-itf-eds-gridfs") }}"
EDS_GRIDFS_USER: "{{ include "env.default" (dict "ctx" . "val" .Values.EDS_GRIDFS_USER "def" "atp-itf-eds-gridfs") }}"
{{- end }}

{{- define "env.deploy" }}
mongo_pass: "{{ .Values.mongo_pass }}"
mongo_user: "{{ .Values.mongo_user }}"
{{- end }}

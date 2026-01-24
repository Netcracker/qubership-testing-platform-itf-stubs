FROM bellsoft/liberica-openjdk-alpine-musl:8u472

LABEL maintainer="opensourcegroup@netcracker.com"
LABEL atp.service="atp-itf-stubs"

ENV HOME_EX=/itf
ENV JDBC_USER=itfu
ENV JDBC_PASS=X8eXuS
ENV JDBC_URL=jdbc:postgresql://kube01nd04cn:5433/itf
ENV PORT=8080

WORKDIR $HOME_EX

RUN echo "https://dl-cdn.alpinelinux.org/alpine/v3.21/community/" >/etc/apk/repositories && \
    echo "https://dl-cdn.alpinelinux.org/alpine/v3.21/main/" >>/etc/apk/repositories && \
    apk add --update --no-cache --no-check-certificate \
        bash=5.2.37-r0 \
        curl=8.14.1-r2 \
        font-dejavu=2.37-r5 \
        fontconfig=2.15.0-r1 \
        gcompat=1.1.0-r4 \
        gettext=0.22.5-r0 \
        git=2.47.3-r0 \
        htop=3.3.0-r0 \
        jq=1.7.1-r0 \
        libcrypto3=3.3.5-r0 \
        libssl3=3.3.5-r0 \
        net-tools=2.10-r3 \
        nss_wrapper=1.1.12-r1 \
        procps-ng=4.0.4-r2 \
        sysstat=12.7.6-r0 \
        tcpdump=4.99.5-r0 \
        wget=1.25.0-r0 \
        zip=3.0-r13 && \
      rm -rf /var/cache/apk/*

COPY deployments/install deployments/install
COPY deployments/atp-common-scripts deployments/atp-common-scripts
COPY build-context/common/target/ /tmp/

RUN mkdir -p dist/atp deployments/update && \
    cp -r deployments/install/* deployments/update/ && \
    find deployments -maxdepth 1 -regex '.*/\(install\|update\|atp-common-scripts\)$' -exec mv -t dist/atp {} +

RUN adduser -D -H -h /atp -s /bin/bash -u 1007 atp && \
    mkdir -p /etc/env /etc/alternatives /tmp/log/diagnostic /tmp/cert && \
    ln -s ${JAVA_HOME}/bin/java /etc/alternatives/java && \
    echo "${JAVA_HOME}/bin/java \$@" >/usr/bin/java && \
    chmod a+x /usr/bin/java

RUN unzip /tmp/*.zip -d $HOME_EX/ && \
    cp -r dist/atp /atp/ && chmod -R 775 /atp/ && \
    find $HOME_EX -type f -name '*.sh' -exec chmod a+x {} + && \
    find $HOME_EX -type d -exec chmod 777 {} \;

EXPOSE 10002 8080 8161 61616 61617

USER atp

CMD [ "./run.sh" ]
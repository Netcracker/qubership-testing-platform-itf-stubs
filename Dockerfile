FROM artifactory-service-address/path-to-java-image

LABEL maintainer="our-team@qubership.org"
LABEL atp.service="atp-itf-stubs"

ENV HOME_EX=/itf
ENV JDBC_USER=itfu
ENV JDBC_PASS=X8eXuS
ENV JDBC_URL=jdbc:postgresql://kube01nd04cn:5433/itf
ENV PORT=8080

WORKDIR $HOME_EX

COPY --chmod=775 dist/atp /atp/
COPY --chown=atp:root ./build $HOME_EX/

RUN find $HOME_EX -type f -name '*.sh' -exec chmod a+x {} + && \
    find $HOME_EX -type d -exec chmod 777 {} \;

EXPOSE 10002 8080 8161 61616 61617

USER atp

CMD [ "./run.sh" ]

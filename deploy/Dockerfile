# Docker host: build from the xipki-console repository root after packaging console-server/.
ARG JAVA_IMAGE=alpine:3.24@sha256:be2656061b536c1883c4034790e21d0a09c5aaaf51968b89368a3c0371ea2c9a
FROM ${JAVA_IMAGE} AS runtime-base
# Keep Java 17 while installing current vendor security patches. Scan the final image before release.
RUN apk upgrade --no-cache && apk add --no-cache openjdk17-jre-headless \
    && addgroup -g 10002 web && addgroup -g 10003 management \
    && adduser -D -H -u 10002 -G web web && addgroup web management
ENV JAVA_HOME=/usr/lib/jvm/java-17-openjdk
ENV PATH="${JAVA_HOME}/bin:${PATH}"
RUN java -version
FROM runtime-base AS console
WORKDIR /opt/xipki-console
RUN mkdir -p /var/lib/xipki-web/enrollment-keys \
    && chown -R web:web /var/lib/xipki-web && chmod 0700 /var/lib/xipki-web/enrollment-keys
COPY console-server/target/xipki-console-server-1.0.0-SNAPSHOT.jar app.jar
COPY console-server/target/lib/ lib/
COPY LICENSE NOTICE ./
COPY --chmod=0755 deploy/docker-entrypoint.sh docker-entrypoint.sh
USER web
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 CMD wget -q -T 8 -O /dev/null "http://localhost:${SERVER_PORT:-8080}/health/ready" || exit 1
ENTRYPOINT ["/opt/xipki-console/docker-entrypoint.sh"]

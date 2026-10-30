# Docker host: build from the repository root. Keep MariaDB 11.4.13 and its official initialization contract.
ARG MARIADB_IMAGE=mariadb:11.4.13@sha256:d1891ede5a559f1086f55053bb8cc1569b0298338c4d72d71725a7f74f6ba620
ARG ALPINE_IMAGE=alpine:3.23@sha256:ff7f0c727dd796aa36787bfcd9a64bf35a6ce72a73451b3359fde849b6703d63
FROM ${MARIADB_IMAGE} AS entrypoint
FROM ${ALPINE_IMAGE}
RUN apk upgrade --no-cache && apk add --no-cache mariadb mariadb-client bash coreutils findutils pwgen su-exec tzdata xz zstd \
    && mariadbd --version | grep -q '11.4.13-MariaDB' \
    && mkdir -p /run/mysqld /docker-entrypoint-initdb.d \
    && chown mysql:mysql /run/mysqld
COPY --from=entrypoint /usr/local/bin/docker-entrypoint.sh /usr/local/bin/docker-entrypoint.sh
COPY --from=entrypoint /usr/local/bin/healthcheck.sh /usr/local/bin/healthcheck.sh
# Alpine uses su-exec; reject an upstream entrypoint change before replacing its single gosu call.
RUN test "$(grep -c 'exec gosu mysql' /usr/local/bin/docker-entrypoint.sh)" -eq 1 \
    && sed -i 's/exec gosu mysql/exec su-exec mysql/' /usr/local/bin/docker-entrypoint.sh \
    && ! grep -q gosu /usr/local/bin/docker-entrypoint.sh
VOLUME /var/lib/mysql
EXPOSE 3306
ENTRYPOINT ["docker-entrypoint.sh"]
CMD ["mariadbd", "--skip-networking=0", "--bind-address=0.0.0.0"]

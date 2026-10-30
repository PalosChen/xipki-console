# XiPKI Console

**English** | [简体中文](README.zh-CN.md)

XiPKI Console is a web management interface for [XiPKI](https://github.com/xipki/xipki).
The React frontend supports English and Simplified Chinese. The Java 17 backend
uses embedded Tomcat and JDBC, communicates with the CA management endpoint over
mutual TLS, and uses XiPKI utilities for configuration and JSON encoding.

## Features

- Root/intermediate CA creation, external CA import, certificate chains and CA configuration.
- Certificate issuance, download, suspension, revocation, CRLs and republication.
- Profiles, signers, requestors, publishers, keypair generators and CA associations.
- Editable users and roles, operation permissions, CA/template scopes and audit records.
- Self-service account settings, password changes and English/Chinese localization.

Web accounts, permissions, sessions and audit data are stored in MariaDB.
The CA database is configured separately and can use H2 or an external database.

## Repository layout

| Directory | Contents |
| --- | --- |
| `console-server/` | Java backend, schema migrations and regression tests |
| `console-ui/` | React application, translations and frontend tests |
| `tests/api/` | Manually invoked API acceptance checks against a running system |
| `scripts/` | Build, archive packaging and local startup commands |
| `deploy/` | Runtime image, Compose deployment and startup scripts |
| `guides/` | Maintained configuration, authorization, testing and translation guides |

## Build

Requirements: JDK 17+, Maven, Node.js 22.16+ and npm. Build and test from the
repository root:

```sh
export XIPKI_SOURCE_DIR=/absolute/path/to/matching/xipki
sh scripts/build.sh
```

The backend depends on XiPKI `util-extra:6.6.0-SNAPSHOT`. When
`XIPKI_SOURCE_DIR` is set, the build installs the matching utility artifacts
from that checkout. Omit it when the artifacts are already available in the
local Maven repository. The script installs locked frontend dependencies,
runs frontend and backend tests, and builds both components. To create a host
distribution archive, run `sh scripts/package.sh`; output is in `dist/`.

Use a compatible XiPKI source revision for the version in
`console-server/pom.xml`. Other versions require compatibility testing.

The distribution consists of `console-server/target/xipki-console-server-1.0.0-SNAPSHOT.jar`
**and all of `console-server/target/lib/`**. After configuring the environment described
in [configuration](guides/configuration.md), run on the host:

```sh
java -Dsun.net.client.defaultConnectTimeout=3000 -Dsun.net.client.defaultReadTimeout=3000 \
  -jar console-server/target/xipki-console-server-1.0.0-SNAPSHOT.jar
```

For frontend development, run `npm run dev` from `console-ui/`. Vite listens on
port 5173 and proxies `/api` to `http://localhost:8080`; set `VITE_API_TARGET`
when the backend uses a different address.

## Docker deployment

`sh scripts/build.sh --docker` builds the Console and database images. The
Console image packages the prebuilt application on Alpine with Java 17. The
database image uses MariaDB 11.4.13 on Alpine. See the
[security guide](guides/security.md) for image verification requirements.

`deploy/compose.yml` starts the console, MariaDB and a separate CA image, with
named volumes for their persistent data. It requires the **matching XiPKI Docker
distribution image** that exports a management client to `/export/web` and runs
the management endpoint at `https://ca:8443/ca/mgmt`. The CA image must support
this provisioning contract.

The CA image is built separately from a matching XiPKI `6.6.0-SNAPSHOT`
distribution checkout containing `docker/ca/Dockerfile`. This repository does
not publish a CA image. Use the same XiPKI checkout for the Console build and
pin its revision for reproducible deployments. On a development host with
Docker running, build and scan the images before starting them:

```sh
export XIPKI_SOURCE_DIR=/absolute/path/to/matching/xipki
sh scripts/build.sh --docker
docker build -f "$XIPKI_SOURCE_DIR/docker/ca/Dockerfile" \
  -t xipki-ca:security-update "$XIPKI_SOURCE_DIR"
export CA_IMAGE=xipki-ca:security-update
export WEB_IMAGE=xipki-console:local
export DB_IMAGE=xipki-console-db:local
sh scripts/check-security.sh
export WEB_PORT=28127
sh scripts/local.sh up
```

The example uses local image tags. The security check requires Trivy; release
deployments should use reviewed image digests. Transfer the images to the Docker
host when it differs from the build host.

The script initializes missing secrets, starts a separate `xipki-console-local`
Compose project, waits for all three services to be healthy and checks for
restart loops. Open `http://<host-LAN-IP>:28127` from another device after it
reports readiness. `WEB_BIND_ADDRESS` defaults to `0.0.0.0`; set it to
`127.0.0.1` to limit access to the Docker host. It requires
Compose v2 with `--wait-timeout`; startup has a default 300-second timeout.

Use `sh scripts/local.sh status`, `sh scripts/local.sh logs` or
`sh scripts/local.sh stop` for daily operations. Stopping preserves all data;
the next `up` reuses it. These helpers read exported environment variables and
do not load `deploy/.env`. See [script usage](scripts/README.md) for host startup,
offline builds and archive packaging.

The initial username is `admin`. Read the generated password from
`deploy/secrets/admin-password` on the host; first login requires changing it.
Initialization preserves existing secrets and populated Web databases. Back up
database volumes, enrollment keys and their encryption secret together. See
[configuration](guides/configuration.md) before exposing the console through HTTPS.

## Documentation

- [Runtime configuration and deployment](guides/configuration.md)
- [Build, package and local startup](scripts/README.md)
- [Users, roles and permissions](guides/permissions.md)
- [Testing](guides/testing.md)
- [Adding translations](guides/internationalization.md)

## Contributing

Source code, API messages and maintained guides are written in English.
Localized UI text belongs in the language resources. Keep both README versions
in sync and run the checks in [testing](guides/testing.md) before submitting changes.

## License

XiPKI Console is licensed under the [Apache License, Version 2.0](LICENSE).
See [NOTICE](NOTICE) for the copyright statement. Third-party dependencies
retain their respective licenses.

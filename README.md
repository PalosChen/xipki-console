# XiPKI Console

**English** | [简体中文](README.zh-CN.md)

A lightweight web console for XiPKI, featuring CA and certificate management,
role-based access control, audit logging, and Docker deployment.

This is an independently developed companion to [XiPKI](https://github.com/xipki/xipki).
It includes an English/Simplified Chinese React frontend and a Java 17 backend
using embedded Tomcat, direct JDBC, and XiPKI's management client and JSON codec.
The backend does not require Spring Boot, Jackson, an ORM, or a connection pool.

## Features

- Root/intermediate CA creation, external CA import, certificate chains and CA configuration.
- Certificate issuance, download, suspension, revocation, CRLs and republication.
- Profiles, signers, requestors, publishers, keypair generators and CA associations.
- Editable users and roles, operation permissions, CA/template scopes and audit records.
- Self-service account settings, password changes and English/Chinese localization.

Web accounts, permissions, sessions and audit data use MariaDB. The CA retains
its own database configuration, including H2 or an external database.

## Repository layout

| Directory | Contents |
| --- | --- |
| `console-server/` | Lightweight backend, schema migrations and regression tests |
| `console-ui/` | React application, translations and frontend tests |
| `tests/api/` | Manually invoked API acceptance checks against a running system |
| `scripts/` | Build, archive packaging and local startup commands |
| `deploy/` | Runtime image, Compose deployment and startup scripts |
| `guides/` | Maintained configuration, authorization, testing and translation guides |

`docs/` is reserved for local AI notes, drafts and process reports. It is ignored
by Git and excluded from release archives; publish maintained instructions in `guides/`.

## Build on the development host

Requirements: JDK 17+, Maven, Node.js 22.16+ and npm. Build and test from the
repository root:

```sh
sh scripts/build.sh
```

This installs locked frontend dependencies, tests and builds the frontend, then
tests and packages the lightweight backend. To also create a host distribution
archive, use `sh scripts/package.sh`; output is under the ignored `dist/` directory.

Console retains **XiPKI `6.6.0-SNAPSHOT` utility** artifacts for JSON and configuration
encoding. The CA management client and cryptography use Console adapters and JDK
providers; the Console runtime contains no Bouncy Castle or XiPKI security module.
Install the matching utility artifacts first. To include that step in the
build, explicitly provide the source checkout:

```sh
XIPKI_SOURCE_DIR=/absolute/path/to/matching/xipki sh scripts/build.sh
```

Use the matching existing 6.x checkout. The latest upstream `master` is a 7.x
development line with different Java packages and Maven coordinates; changing
`xipki.version` alone does not upgrade this console. Compatibility with other
XiPKI releases must be verified before use.

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

On the development host with Docker running, build the application and both Console/database images:

```sh
sh scripts/build.sh --docker
```

The Dockerfile packages prebuilt artifacts; it does not compile or run tests.
Its build context excludes source files, credentials and local runtime data.
The runtime uses Java 17 headless packages on official Alpine. The database
image retains MariaDB 11.4.13 on Alpine and replaces the upstream Go-based
privilege helper with `su-exec`. Both images must pass [security checks](guides/security.md)
before publication; an updated upstream tag alone does not establish safety.

`deploy/compose.yml` starts the console, MariaDB and a separate CA image, with
named volumes for their persistent data. It requires the **matching XiPKI Docker
distribution image** that exports a management client to `/export/web` and runs
the management endpoint at `https://ca:8443/ca/mgmt`. An arbitrary upstream CA
image may not implement this provisioning contract.

On the Docker host, set the image references (public release images are not yet
specified), choose a free port, then start:

```sh
export CA_IMAGE=xipki-ca:security-update
export WEB_IMAGE=xipki-console:local
export WEB_PORT=28127
sh scripts/local.sh up
```

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

Use English for source code, identifiers, comments, logs, API messages and
maintained guides. Keep `README.md` as the primary English entry point and
update `README.zh-CN.md` when its content changes. Both versions link to each
other at the top.

Localized UI text belongs in the language resources. Native language names and
Unicode or translation test fixtures may contain their original characters.
See [testing](guides/testing.md) before submitting changes.

## License

The console uses the [MIT license](LICENSE). Third-party dependencies retain
their respective licenses.

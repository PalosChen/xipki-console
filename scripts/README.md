# Build, package and run

Run these POSIX Shell scripts from any working directory on a Linux/macOS
development host. They resolve paths relative to this repository. No extra
build framework or process manager is needed.

| Command (from repository root) | Result |
| --- | --- |
| `sh scripts/build.sh` | Install locked npm dependencies, run tests, build frontend and backend |
| `sh scripts/build.sh --docker` | Also build `WEB_IMAGE` and `DB_IMAGE` (defaults `xipki-console:local`, `xipki-console-db:local`) |
| `sh scripts/check-security.sh` | Block known npm and image vulnerabilities for Console, database and CA |
| `sh scripts/package.sh` | Build and create `dist/xipki-console-1.0.0-SNAPSHOT.tar.gz` |
| `sh scripts/local.sh up` | Start the local CA, MariaDB and Console; wait for health |
| `sh scripts/local.sh status` | Show local container status |
| `sh scripts/local.sh logs` | Follow the latest container logs |
| `sh scripts/local.sh stop` | Stop local containers, preserving their data |

## Local Docker startup

Start Docker Desktop (macOS) or the Docker daemon (Linux). Build commands need
JDK 25+, Maven, Node.js 22.16+ and npm on the development host. `local.sh` needs
only Docker Compose v2 with `--wait-timeout` and standard host utilities.

The XiPKI `7.0.0-SNAPSHOT` modules declared by `console-server/pom.xml` must be
available in the local Maven repository. Set `XIPKI_SOURCE_DIR` to the matching
XiPKI source checkout to let `build.sh` install the `util` module and its
dependencies before building the Console. This is optional when those artifacts
are already installed and is never inferred from a backup/worktree location.
For example:

```sh
export JAVA_HOME=/absolute/path/to/jdk-25
export XIPKI_SOURCE_DIR=/absolute/path/to/matching/xipki
sh scripts/build.sh --docker
export CA_IMAGE=your-xipki-7-ca-image
export WEB_IMAGE=xipki-console:local
export DB_IMAGE=xipki-console-db:local
sh scripts/check-security.sh
export WEB_PORT=28127
sh scripts/local.sh up
```

`CA_IMAGE` must be a separately maintained XiPKI Docker distribution that
exports its management client and exposes a healthy management endpoint as
described in the root README. The current upstream master does not include the
historical `docker/ca/Dockerfile`, and building Console does not build a CA
image. The security scan requires Trivy; release images should use reviewed,
immutable digests.
The example uses an unused local port; default `WEB_PORT` is 8080. Existing
services using the chosen port are not stopped or replaced automatically.

Open `http://<host-LAN-IP>:28127` from another device after the script reports
readiness. The initial user
is `admin` and the generated password is in `deploy/secrets/admin-password`.
Read that file locally; the startup script does not print secrets. First login
requires a password change. Existing database accounts keep their current passwords.

`local.sh` uses exported environment variables, not `deploy/.env`. Optional
settings include `WEB_IMAGE`, `DB_IMAGE`, `WEB_BIND_ADDRESS`, `WEB_PORT`, `WEB_ADMIN_USERNAME`, `XIPKI_CA_NAME`,
`SESSION_COOKIE_SECURE`, `WAIT_SECONDS` (default 300), and `COMPOSE_PROJECT_NAME`
(default `xipki-console-local`). Keep the same project name for status/stop and
future starts. All local projects use `deploy/secrets/` for their initial secrets.
The default project has its own named volumes and does not reuse worktree data.

`stop` preserves containers and volumes. `up` starts them again and reuses data.
Database and hosted-enrollment initialization is automatic. Linux startup runs
secret ownership checks through the CA container; see [configuration](../guides/configuration.md).

## Host archive

```sh
sh scripts/package.sh
```

The archive includes the application JAR, `lib/`, both README languages, `guides/`,
this script usage guide, `LICENSE` and `NOTICE`. It contains no secrets or database contents.
Extract into a new directory, set
the database and CA mTLS settings from `guides/configuration.md`, then run:

```sh
java -Dsun.net.client.defaultConnectTimeout=3000 -Dsun.net.client.defaultReadTimeout=3000 \
  -jar xipki-console-server-1.0.0-SNAPSHOT.jar
```

The backend serves the bundled frontend. An external MariaDB and running CA
are still required for host execution. For frontend hot reload, use
`cd console-ui && npm run dev` against a configured backend.

Pass `--offline` to either build script when all npm/Maven artifacts are cached.
`package.sh --docker` also builds the Docker image. Build outputs are ignored by
Git (`console-server/target/`, `console-ui/dist/`, `dist/`, `console-ui/node_modules/`).

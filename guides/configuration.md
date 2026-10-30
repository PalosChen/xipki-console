# Runtime configuration

Settings apply to the Java backend, whether run directly on the host or inside
the container. JVM properties take precedence over environment variables.
Password files may end with one LF or CRLF. Set only one source per secret.

| Setting | Default / purpose |
| --- | --- |
| `SERVER_PORT` | `8080` |
| `SERVER_BASE_DIR` | `/tmp/xipki-console`, writable Tomcat work directory |
| `WEB_DB_URL` | `jdbc:mariadb://web-db:3306/xipki_web` |
| `WEB_DB_USER` | `xipki_web` |
| `WEB_DB_PASSWORD` / `WEB_DB_PASSWORD_FILE` | Required database password |
| `WEB_ADMIN_USERNAME` | `admin`, used only for a fresh database |
| `WEB_ADMIN_PASSWORD` / `WEB_ADMIN_PASSWORD_FILE` | Required bootstrap password; existing accounts are preserved |
| `SESSION_COOKIE_SECURE` | `false`; set `true` when accessing through HTTPS |
| `XIPKI_CA_URL` | `https://ca:8443/ca/mgmt`; HTTPS with client authentication |
| `XIPKI_CLIENT_DIR` | `/run/xipki-client`, containing `client.p12`, `trustanchors.pem` and `password` |
| `XIPKI_CLIENT_KEYSTORE` / `XIPKI_CLIENT_TRUSTANCHORS` | Optional individual file paths |
| `XIPKI_CLIENT_PASSWORD_FILE` | Defaults to `${XIPKI_CLIENT_DIR}/password` |
| `WEB_ENROLLMENT_KEY_FILE` | Optional hosted-enrollment encryption secret: exactly 32 raw bytes |
| `WEB_ENROLLMENT_KEY_DIR` | Required with that secret: writable persistent mount |

Legacy CA settings `xipki.ca-server.url`, `xipki.ssl.keystore`,
`xipki.ssl.truststore` and `xipki.ssl.keystore-password` remain supported.
Conflicting settings are rejected. Configure both enrollment settings to use
hosted CA keys; otherwise that storage is unavailable. Back up the secret and
key directory together. The Linux container entrypoint validates permissions,
secret size and persistent mounts before Java starts.

## Compose on the Docker host

For local use, `sh scripts/local.sh up` wraps Compose, initializes secrets and
waits for health on both Linux and macOS. It reads exported variables and uses
the separate `xipki-console-local` project by default. See
[script usage](../scripts/README.md). The instructions below cover direct Compose
use, whose default project is `xipki-console`; do not mix project names when
managing an existing deployment.

`deploy/.env.example` lists Compose settings. Either export them in the host
shell, or copy that example to the ignored `deploy/.env` and supply it with
`docker compose --env-file deploy/.env -f deploy/compose.yml ...`. Running
`init-secrets.sh` still requires `CA_IMAGE` exported in the host shell.

`CA_IMAGE` and `WEB_IMAGE` are required. Other Compose defaults include
`WEB_BIND_ADDRESS=0.0.0.0`, `WEB_PORT=8080`, `WEB_ADMIN_USERNAME=admin`,
`SESSION_COOKIE_SECURE=false` and `XIPKI_CA_NAME=demo-ca`. The Console port is
reachable through the host's network interfaces. For access outside a trusted
demo network, place an HTTPS reverse proxy in front of it and enable secure cookies. Keep
the CA management endpoint and MariaDB on the internal network.

For online SubCA issuance, set `XIPKI_CA_RDN_ORDER` to the comma-separated OIDs
from the CA's effective `org.xipki.ca.rdnorder.cfg` when that file differs from
XiPKI's default. Console uses this order to reject CSR subjects that XiPKI
would reorder. Keep this setting synchronized with the CA; the management API
does not expose its effective order. Profiles with fixed subject values are
rejected before online issuance because the issued subject would differ from
the CSR.

Compose creates Web MariaDB automatically. Its database secrets are under
`deploy/secrets/` and excluded from Git. The container runs as UID 10002 and
uses GID 10003 to read the exported CA management client. Secret initialization
uses the CA image's OpenSSL; it requires no OpenSSL, Java or Node on the host.

The included CA configuration uses that image's default H2 persistence. An
external CA database can be enabled with the included
`deploy/compose.external-db.yml` override after provisioning the CA database
and a dedicated password file.
Web database settings do not configure the CA database. An existing external
Web MariaDB can instead be used by configuring the backend settings above and
providing the same CA client and enrollment mounts in your deployment.

## Readiness and persistence

- `/health/live` checks the running application independently of the database.
- `/health/ready` checks the Web schema and actual CA management availability.
  Results, including failures, are cached for five seconds. Concurrent requests
  during a refresh return unavailable without starting more dependency checks.
- Named volumes hold CA data, Web MariaDB data, the management client and hosted enrollment keys.

Check that a container is running and is not restarting before checking its
application health. `deploy/wait-ready.sh` targets Linux hosts with
`/proc/uptime`, `awk`, `sleep` and Compose v2. Set `WAIT_SECONDS` to override
the default 300-second timeout. It does not stop services or remove data.

Web initializes/migrates its own business schema to v5 under a database lock.
It adds `lite_session` for hashed opaque sessions (`LITE_SESSION`, 30-minute idle
expiry). Anonymous CSRF bootstrap uses a signed, ten-minute cookie/header token
and creates no database rows. Bootstrap tokens are local to the current process;
after a restart or expiration, refresh the login page or explicitly retry after a
denied request. Authenticated sessions remain database-backed. New passwords use
JDK PBKDF2-HMAC-SHA256; valid legacy BCrypt hashes are upgraded after successful
login without resetting accounts or changing authorization versions.
Existing business records, permissions and enrollment
request hashes retain their formats. Sessions from the old Spring backend are
not shared, so users must log in again. Do not balance both runtimes as one
session service. Back up an existing Web database before upgrading; a rollback
must use a compatible schema and application version.

Compose uses `DB_IMAGE` (default `xipki-console-db:local`), built by
`sh scripts/build.sh --docker`, instead of the unmodified upstream database image.
Before changing a database image, back up its volume and confirm version
compatibility. Image changes do not authorize deleting or reinitializing data.

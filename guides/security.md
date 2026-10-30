# Dependency and image security

Console uses JDK providers for cryptography and a BC-free management protocol
adapter. Maven rejects Bouncy Castle dependencies, including transitive and test
dependencies. Packaging refreshes `target/lib/` so obsolete runtime JARs do not
survive a build without `clean`. Legacy BCrypt verification uses a small separate
compatibility library; new password hashes use JDK PBKDF2-HMAC-SHA256.

On the development/release host, build and test first, then scan the actual images:

```sh
sh scripts/build.sh --docker
export WEB_IMAGE=xipki-console:local
export DB_IMAGE=xipki-console-db:local
export CA_IMAGE=your-reviewed-ca-image
sh scripts/check-security.sh
```

The check requires npm, Trivy and Docker. It audits development dependencies too,
downloads advisory database updates, and fails on known findings at every severity without
ignoring unfixed vulnerabilities. A failed scan or unavailable advisory service fails
the check. Use immutable image digests for releases and record the scanner/database
date with each release; a previous clean result is not a permanent guarantee.

The release gate covers Console, the derived MariaDB image and the CA distribution
image. A clean result cannot be claimed if any of those scans is unavailable.

The Console runtime starts from the official multi-architecture Alpine 3.24 image
and installs only the headless Java 17 runtime. Its health check uses Alpine's
BusyBox `wget`. The database runtime starts from the official Alpine 3.23 image
with MariaDB 11.4.13 from Alpine's package repository. It copies the matching
official MariaDB entrypoint and healthcheck scripts at build time.

The database derivative preserves the existing MariaDB 11.4.13 initialization
settings and data directory. It uses Alpine's `su-exec` for the privilege
transition without shipping the upstream `gosu` binary or its old Go runtime.
Fresh initialization, password files, the complete backend test suite, and
same-version readback of a disposable volume created by the former official
image were verified. Future database version upgrades or backup-archive restores
need their own compatibility tests and required tools before deployment. Never
delete a volume to make an upgrade pass.

An official image's latest tag can still contain known vulnerabilities. Update
fixable components and report remaining vendor-unfixed advisories explicitly.
Do not suppress them or publish the image set as vulnerability-free while they
remain. Existing CA distribution images are checked independently: removing BC
from Console does not patch or remove cryptographic dependencies inside the CA.
The development example `xipki-ca:management-parity` is not a security-approved
release image.

CA deletion removes the CA remotely before invalidating its Console role
bindings. If the CA response is lost, Console checks whether the CA name still
exists before cleanup. A database failure after confirmed remote deletion can
leave obsolete fingerprint-bound grants; an administrator should remove those
bindings from the account management page after restoring database service.

RSA, EC, DSA, EdDSA and RSA-PSS material use supported JDK implementations.
Algorithms that require an additional provider, such as SM2, are not supported
by the BC-free Console's local cryptographic operations. Legacy `xijsonv1`
profiles remain manageable, but Console issuance requires a supported `xijson`
capability description; an unknown profile fails closed.

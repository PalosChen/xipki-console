# Testing

Commands run on the development host from the repository root unless stated
otherwise. Tests and their dependencies are kept; generated reports, database
contents, private keys and workstation-specific browser scripts are not.

## Build and regression tests

The combined entry point is `sh scripts/build.sh` (or `--docker` to also build
the runtime image). `sh scripts/package.sh` runs the same checks before creating
the host archive. For individual checks, use the commands below.

Use Node.js 22.16+ and JDK 17, with the matching XiPKI artifacts installed:

```sh
cd console-ui
npm ci
npm test
npm run build
cd ..
mvn -f console-server/pom.xml clean package
```

The backend runs unit, domain, crypto, routing and authorization contract tests.
It verifies that the built frontend and its referenced assets are bundled.
By default the MariaDB-dependent tests are skipped.

## Real MariaDB and HTTP integration tests

Create an isolated disposable MariaDB database named `xipki_web_lite_tests` on
loopback. Set these environment variables to your test instance:

```sh
export WEB_LITE_TEST_DB_URL=jdbc:mariadb://127.0.0.1:33319/xipki_web_lite_tests
export WEB_LITE_TEST_DB_USER=root
# Set WEB_LITE_TEST_DB_PASSWORD to that disposable database's root password.
mvn -f console-server/pom.xml -Dlite.db.required=true clean package
```

The HTTP fixture currently uses the `root` test account. The tests check the
database name and loopback URL, retain their fixtures and must never target a
business database. `-Dlite.db.required=true` makes missing database configuration
fail rather than silently skip the integration tests.

After building a database image, verify its password-file initialization,
backend integration tests, and readback of a volume created by the official
MariaDB 11.4.13 image:

```sh
node tests/api/database-image-smoke.mjs
```

The script creates and removes its own containers, named volume and random
password files. Set `DB_IMAGE` and `DB_BASELINE_IMAGE` to test other images.

## Live API checks

System-level API acceptance scripts live in `tests/api/`, separate from the Java
tests in `console-server/src/test/java/` and UI tests in `console-ui/tests/`.
They are invoked manually, are not run by Maven or the build scripts, and are
not included in the server JAR, host archive or Docker image. Node.js is needed
only on the machine executing these checks, not on the running Java server.

These scripts require Node.js 22.16+, an existing test account, a running CA
and a ready console. The examples target the default Compose port:

```sh
export LITE_SMOKE_URL=http://127.0.0.1:8080
export LITE_SMOKE_USER=admin
# Set LITE_SMOKE_PASSWORD to the test account's current password.
node --experimental-strip-types tests/api/ca-availability-smoke.mjs
```

The availability check needs one running and one stopped test CA. It queries
certificate data and verifies that certificate, CRL and republication operations
reject a stopped CA with HTTP 409; rejected attempts can create audit records.

### Intermediate CA prerequisites

The **Parent CA** and **SubCA certificate profile** selectors on Add CA are
populated by `GET /api/ca-enrollments/options`. A parent is listed only when
all of the following are true:

- The signed-in account has `CA_ISSUE_SUBCA` for that CA and profile.
- The parent CA is active, running, unrevoked, has a valid certificate chain,
  and its path length permits another CA.
- An operational `xijson` profile with `certLevel: "SubCA"` is installed and
  associated with that parent. An end-entity or root profile does not qualify.

XiPKI includes a sample SubCA profile at
`/opt/xipki/mgmt-cli/xipki/ca-setup/certprofile/certprofile-subca.json` in the
CA container. Installing the profile alone does not associate it with a CA.
Use a dedicated test root and SubCA profile when testing this workflow; avoid
changing the existing demo CA. After installing and associating the profile,
refresh Add CA and confirm that the parent appears and the dependent profile
selector lists the SubCA profile. If it remains empty, check CA runtime state,
certificate chain/path length, profile health, and the account's scoped role
binding.

`tests/api/runtime-smoke.mjs` is a **mutating** smoke test for a disposable
CA/database. It creates a root CA, profile, requestor, key generator, database
parameter and certificate; it suspends/revokes its certificate and generates a
CRL. It also changes the selected CA's CRL configuration and restarts that CA.
All test data is retained. It requires the existing `rootca` and `tls` templates
and explicit `LITE_SMOKE_ALLOW_MUTATION=yes`. Set `LITE_SMOKE_CA` to the disposable
CA name. If first-login password change is pending, supply
`LITE_SMOKE_NEW_PASSWORD`. Never run it on production or against shared CA data.

## Linux entrypoint checks

On a Docker host, first build the console image. Mount a disposable host
directory writable by UID 10002 at `/test-keys`, then run the real script as
the image's unprivileged user:

```sh
docker run --rm --entrypoint sh \
  -v "$PWD:/workspace:ro" -v /absolute/disposable/key-directory:/test-keys \
  -e TEST_KEY_MOUNT=/test-keys \
  xipki-console:local /workspace/deploy/tests/enrollment-entrypoint.sh
```

This checks persistence validation, missing optional tools, incorrect secret size,
missing secret files and unwritable directories before Java starts. Only the
final Java invocation is replaced by a test executable.

## Manual browser checks

Use an isolated deployment and preserve test data for inspection:

1. Log in, complete the initial password change, switch English/Chinese and refresh.
2. Check dashboard guidance, update your display name and inspect the account menu.
3. Create `console-test-root` with subject `CN=Console Test Root,O=Example`,
   template `rootca`, generated `EC_P256` key, inactive status and `365d` maximum
   validity. Configure its templates/requestor before activation and restart.
4. Confirm the stopped CA remains configurable but cannot be selected for
   certificate/CRL/republication operations. After activation/restart, refresh
   the CA list and confirm it becomes available where authorized.
5. Use a suitable test certificate profile to issue a certificate with subject
   `CN=console-test.example.test`; inspect/download it, place it on hold, restore
   it, then revoke it. Configure CRL generation before generating a CRL.
6. Create a test user and role, bind a specific CA/template, then verify allowed
   operations and rejected operations in a separate browser profile.
7. Confirm audit records contain the operator, target and outcome without secrets.

### Intermediate CA browser and API acceptance

Use unique test names in the current preview environment and keep the created
CA, profile, enrollment operation and certificates for inspection. Run the
authenticated API steps only with an explicitly authorized test account.

1. Create a test root CA with a path length that permits a child, associate a
   `SubCA` profile, activate it, and wait for its
   runtime state to become ready. Grant the test account `CA_ISSUE_SUBCA` for
   that root and SubCA profile, plus `CA_IMPORT` when it will register the child.
2. Open **CA Management > Add CA > Intermediate CA**. Confirm the test root
   appears under **Parent CA** and its SubCA profile appears under **SubCA
   certificate profile**. Switch parents and verify the profile selection is
   cleared and replaced with the new parent's eligible profiles.
3. Select **Online parent in this system**, **Manage this CA in XiPKI**, a
   unique child name and subject, and a generated software key. Prepare the
   enrollment and inspect the CSR and recorded parent fingerprint. Issue the
   intermediate certificate, inspect its issuer and path length, then register
   the CA. Confirm the new CA is inactive and the hierarchy shows the root as
   its parent through the validated certificate chain. For the current `sub-ca`
   profile, enter the subject in `CN=...,O=...,C=...` order; a different RDN
   order can cause XiPKI to issue a certificate whose subject no longer exactly
   matches the CSR, leaving that enrollment in `RESULT_UNKNOWN`.
4. Associate an end-entity profile and a software keypair generator with the
   child if testing server-side key generation. Activate/restart it, and issue
   a certificate through the child. Inspect the leaf, child and root
   chain, download the certificate, then test hold, restore and revocation.
   Generate and inspect a CRL when the child has CRL generation configured.
5. With a restricted account lacking `CA_ISSUE_SUBCA`, confirm the parent and
   profile are absent. With an authorized account, verify an inactive or
   revoked parent, an incompatible profile, and an exhausted parent path length
   cannot be used to start a new intermediate enrollment.
6. Confirm enrollment, issuance, registration and rejection audit records have
   actor, resource and outcome. Ordinary GET/FETCH/HEAD reads should not add
   audit records; certificate downloads and exports should.

Record the account, environment, generated names, operation ID, certificate
serials, result of each step and any skipped step in the test run notes. Do
not record passwords, private keys or session tokens.

### Preview retest, 2026-10-29

The `xipki-console-preview-*` containers were inspected in the current
environment. The web, CA and database containers reported healthy; the web
container publishes `0.0.0.0:28128`. Inspection of the CA configuration found
that `demo-ca` has only `rootca`, `tls`, `tls-c`, and `smime` associated, with
no SubCA profile bound. This accounts for the empty online enrollment
selectors, independently of any account-specific permission restrictions.

Local checks passed: 62 frontend tests (zero skipped), the frontend production
build, and 52 backend tests. Four backend database integration tests were
skipped because their disposable database was not configured. The intermediate
CA browser/API steps above were **not executed**: automatic execution review
rejected using the deployment admin secret to establish an authenticated
session in the preview environment. No CA, enrollment, certificate or other
business data was changed by this retest. Complete the online steps when a
protected authentication path is available and record their actual results here.

### Preview retest, 2026-10-30

The API acceptance run used the current preview stack and a test administrator.
The Web container was temporarily bound to `127.0.0.1:28128` during
authentication and restored to `0.0.0.0:28128` afterward. The Web, CA and
database containers all reported `running` and `healthy` after restoration.
Passwords, session tokens and private keys were not included in the test output.

The existing `test-root-ca` was active, running, chain-verified, and had an
unrestricted CA path length, but no associated profiles. The existing healthy
`sub-ca` profile was associated with it. `GET /api/ca-enrollments/options`
changed from an empty list to `test-root-ca` with `sub-ca`, confirming the
cause of the empty Add CA selectors and their recovery. The existing `demo-ca`
has path length zero and was not modified.

| Check | Result | Retained evidence |
| --- | --- | --- |
| Intermediate enrollment | Pass | `qa-subca-20261030b`, operation `35aab6cc-b1bb-49fa-b559-aab905a7e780` reached `REGISTERED`. |
| Certificate chain and parent relationship | Pass | Child CA parent fingerprint matches `test-root-ca`; chain is verified and the child certificate signature verifies with the root public key. |
| Child activation and leaf issuance | Pass | Child CA is active/started; dedicated `qa-leaf-20261030b` profile and existing `software` keypair generator are associated. Leaf serial `575927d526284ad6810e9e86d92f4f2d58840dc9` verifies against the child, which verifies against the root. |
| Download and audit | Pass | Ordinary GET reads added no audit row; certificate download added a successful GET audit record. Enrollment prepare, issue, register and CRL generation also have successful audit records. |
| Certificate lifecycle and CRL | Pass | The leaf was held, unsuspended, then revoked for `keyCompromise`. CRL number 2 has a valid child CA signature and includes the revoked serial. |

Two initially failed steps were diagnosed without silently retrying an uncertain
issuance. Operation `27f609eb-a160-42ed-b5df-22ff723010a9` remains
`RESULT_UNKNOWN`: its CSR used `C=CN,O=XiPKI Test,CN=...`, while XiPKI issued a
certificate with the same public key and attributes in `CN=...,O=...,C=CN`
order. The issued certificate and chain passed standalone preflight, but the
enrollment's exact subject comparison rejected them. No second issuance was
sent for that operation. The first leaf key-generation request returned HTTP
400 because the new child CA had no keypair generator. A read confirmed zero
leaf certificates before `software` was assigned and the request retried.

Follow-up fix on 2026-10-30: online SubCA preparation and issuance now check
the CSR subject order against the selected XiPKI profile before sending an
issuance request. Both server-side key-generation routes now return an
actionable HTTP 409 when the CA has no assigned keypair generator. The Console
preview Web container was rebuilt at `0.0.0.0:28128`; its ready endpoint and
the CA/database containers were healthy. Regression results: 60 backend tests
with zero failures and four database tests skipped, 62 frontend tests passed,
and the frontend production build passed. No new live issuance was performed
for this fix. The earlier `RESULT_UNKNOWN` operation remains unchanged and
must not be retried without reconciling its already issued certificate.

Security and correctness follow-up on 2026-10-30: the online SubCA check now
rejects fixed-value subject rules before signing and accepts an explicit
`XIPKI_CA_RDN_ORDER` when the CA uses a custom order. Readiness probes have a
five-second bounded cache; failed CA deletion no longer removes Console role
bindings first. The complete backend suite ran against disposable MariaDB:
79 executed, zero failures and zero skips. Frontend tests: 62 passed; the
production build passed. The updated Console, CA and database preview images
passed the vulnerability gate with zero findings. All preview containers were
healthy on port 28128 and the existing CA certificate fingerprint was preserved.

All listed CA/profile/enrollment/certificate/CRL test data is intentionally
retained in the preview environment. The actual browser rendering of the Add CA
and hierarchy pages, restricted-user permission checks, and an isolated
path-length rejection were not exercised in this API run. Local regression
checks passed: 62 frontend tests with zero skipped, frontend production build,
and 52 backend tests; four backend database integration tests were skipped
because the disposable integration database was not configured.

Use the dashboard's complete workflow and the authorization guides for detailed
field behavior. Profile prerequisites and cryptographic inputs depend on the
chosen CA; this checklist does not assume old workstation fixtures are present.

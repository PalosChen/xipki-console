# Users, roles and permissions

Users, roles, grants, sessions and audit records are stored in the Web MariaDB.
No additional identity service is required. Built-in roles are installation
templates; runtime permissions come from the database. Restarting the server
does not overwrite edits made through the console.

## Initial setup

1. Sign in with the administrator account generated during deployment and
   complete the initial password change. Existing databases retain their accounts and passwords.
2. Open Roles and permissions to review the built-in roles, or create or copy
   a role. Select permissions by operation; the editor includes required read permissions automatically.
3. Create an account in User management and assign roles. Temporary passwords
   are shown once and must be changed at first login.
4. Bind CA roles to specific CAs or all CAs. Specific CA grants validate the
   certificate's SHA-256 fingerprint. Review and rebind a grant after recreating
   a CA under the same name or replacing its certificate.
5. For issuance permissions, select allowed certificate profiles within the
   same user grant. An empty profile scope denies issuance. Selecting all CAs
   or all profiles requires explicit confirmation.

Users without grants can access only their own account. Only super administrators
can maintain roles and assign user roles; saving these changes requires password verification.

## Built-in roles

| Role | Default purpose |
| --- | --- |
| `SUPER_ADMIN` | Platform maintenance, role and grant management, including sensitive configuration and permanent deletion |
| `USER_ADMIN` | Create, update, enable or disable ordinary users, reset passwords and revoke sessions; cannot manage privileged accounts or assign roles |
| `CA_ADMIN` | Configuration, associations and lifecycle of authorized CAs, excluding permanent deletion and intermediate CA issuance |
| `SHARED_RESOURCE_ADMIN` | Manage global signer, requestor, publisher, profile and keypair generator definitions |
| `CERT_REQUESTER` | Submit CSRs for direct issuance within authorized CA/profile scopes; view and download certificates issued through the Web for that user |
| `CERT_OPERATOR` | Query certificates of authorized CAs, suspend, restore, revoke, generate CRLs and republish; excludes issuance and permanent deletion |
| `AUDITOR` | Query and export audit records and review authorization context; cannot combine audit and write permissions |
| `VIEWER` | Public CA summaries, status, certificate chains and CRLs; excludes end-entity certificate lists and business configuration |

Platform and CA roles are separate. Shared resources belong to the platform;
CA grants do not imply global resource management permissions. Creating a CA
requires a separate platform permission. If an administrator has edited a
built-in role, its actual permissions are those shown in the console and stored in the database.

## Grant permissions by operation

| Requirement | Role scope | Permission example |
| --- | --- | --- |
| Read signers only | Platform | `SIGNER_READ` |
| Create requestors without updating or deleting them | Platform | `REQUESTOR_CREATE`, with `REQUESTOR_READ` included automatically |
| Read a CA's redacted configuration | CA | `CA_CONFIG_READ` |
| Update a CA's general configuration | CA | `CA_CONFIG_UPDATE`; status, keys and certificates have separate permissions |
| Bind a certificate profile to a CA | CA | `CA_PROFILE_BIND`; global profile management permission is not required |
| Request certificates | CA | `CERT_ISSUE`, with CA and profile scopes in the same grant |
| Manage an external requestor's access | CA | `CA_REQUESTOR_ACCESS_UPDATE`; adding an association also requires `CA_REQUESTOR_BIND` |
| Independent auditing | Platform | `AUDIT_READ`, `AUDIT_EXPORT`, `AUDIT_CONTEXT_READ` |

Global signers, requestors, publishers, profiles and keypair generators each have
separate read, create, update and delete permissions. Instance-level ACLs such as
"update signer-A only" are not supported. Updating a global definition may affect
multiple CAs. Certificate issuance, suspension, restoration, permanent revocation
and deletion are also separate operations.

One effective grant must satisfy the operation, CA identity and profile conditions
together; permissions from separate grants are not combined to expand a scope.
An external requestor's permissions/profiles are XiPKI client authorization,
managed separately from Web user roles and the Web-to-CA mTLS identity.
Web authorization does not restrict direct use of XiPKI Shell or other management clients.

## Role changes and separation of duties

Except for the protected `SUPER_ADMIN`, built-in roles allow changes to their
name, description and permissions, but cannot be disabled or deleted. Up to 100
custom roles are supported. Disabling a custom role retains its user bindings
without granting permissions. Only unassigned custom roles can be deleted;
deleted codes cannot be reused. A role's code and platform/CA type are immutable.

Permission or status changes revoke existing sessions of bound users. Changing
only a name or description does not. Adding an issuance permission does not
automatically add profile scope; removing it does not clear existing profile scope.
Review affected users and scopes before making changes. Refresh and retry after
a concurrent edit conflict instead of overwriting another administrator's changes.

Separation of duties is checked against actual permissions. Ordinary users cannot
combine audit permissions with write or sensitive backup permissions. Editing a
shared role also checks the combined permissions of every bound user. User
administrators cannot reset privileged accounts to acquire business or platform
access; disabled privileged role bindings remain protected. The last enabled
super administrator cannot be disabled or have that role removed.

## Passwords, sessions and audit

Passwords require at least 15 characters and at most 72 UTF-8 bytes; spaces are
allowed. Temporary passwords from account creation or reset must be changed.
Password changes, account status changes, grant changes and session revocation
invalidate existing sessions. Login and password verification are rate-limited
by account and source IP, with shared state across replicas. Users are not
physically deleted, preserving their audit identity.

High-risk operations require recent password verification. Audit records contain
the operator, action, target, outcome and request ID, without passwords, private
keys or other secrets. CSV exports are limited to 5,000 records per request;
narrow the filters when necessary.
Ordinary GET, HEAD, FETCH and OPTIONS requests are not recorded. GET requests
that download certificates, CRLs or CSRs, or export configuration or audit data,
remain audited.

CA remote writes record `ATTEMPT` before execution. Remote operations and Web
database writes do not form a distributed transaction. After a timeout or an
uncertain result, reconcile by request ID and certificate serial number before
retrying; do not automatically retry issuance.

The requester certificate list scans Web ownership records in bounded cursor
pages and applies the filters and result limit after the scan. Large histories
may take longer to search; the page allows cancelling the scan. Certificates
issued directly through CA/Shell, or without Web ownership records, are not
assigned to a requester automatically. Use a certificate operator account for
broader queries.
For API clients, set `paged=true` and repeat `GET /api/cert` with the same filters and the opaque
`cursor` query value from the preceding `X-Next-Cursor` response header until
that header is absent. Each response scans at most five ownership records.
Without `paged=true`, the existing single-response behavior is retained for up
to 1,000 ownership records; larger histories return an explicit error.

See [runtime configuration](configuration.md) for database upgrades and persistence,
and [testing](testing.md) for isolated test requirements.

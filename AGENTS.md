# XiPKI Console Project Rules

This file defines project-level rules for code changes, reviews and
automation. User instructions take precedence. Keep this file focused on
stable engineering constraints; use README.md, README.zh-CN.md, guides/ and
scripts/README.md for user-facing procedures and command details.

## Toolchain and XiPKI compatibility

- Target the official XiPKI upstream master at 7.0.0-SNAPSHOT unless a task
  explicitly names another tested revision.
- Build and run the Java backend with JDK 25 or newer. Maven's compiler release
  must remain 25.
- Keep XiPKI API migrations explicit. Verify renamed packages, wire fields and
  numeric types against the checked-out XiPKI source before changing adapters.
- The management interface is BC-free. Do not add Bouncy Castle directly or
  transitively; the Maven enforcer rule must continue to reject it.

## Java style

Apply these rules to every new or modified Java file:

- Put the Apache License 2.0 header before the package declaration.
- Put an @author entry in the class, interface, record or enum Javadoc.
- Use two spaces per indentation level.
- Keep every line, including comments, at 100 characters or fewer.
- Indent nested blocks according to their actual depth.
- Wrap parameters only when the declaration exceeds the limit. For a short
  method name, continue parameters from the second line; for a long method
  name, put the first parameter on a new line.
- Keep throws after the closing parenthesis when the complete declaration fits
  within the limit; otherwise put it on the next line with the declaration.
- Prefer the surrounding XiPKI style for braces, imports, naming and simple
  accessors. Run the formatter or an equivalent line-length check on changed
  Java files.
- Import project types and use their short names in code. Do not repeat a
  project type's fully qualified name after it has been imported; retain fully
  qualified names only in package/import declarations, string literals or a
  documented namespace disambiguation.

## Alibaba-derived Java rules

The project adopts applicable rules from the official Alibaba Java Coding
Guidelines. The project's two-space indentation and 100-column limit above
override the Alibaba formatter's four-space and 120-column defaults.

- Always use braces for if, else, for, while and do-while statements, including
  single-statement bodies.
- Catch the narrowest exception that can be handled. Do not catch
  RuntimeException, Exception or Throwable as ordinary control flow. At
  boundaries that must translate an unknown failure, preserve the cause and
  return a safe diagnostic.
- Never ignore an exception. Re-throw it, translate it, or record it with
  context and the exception object. Preserve thread interruption when catching
  InterruptedException.
- Use SLF4J Logger fields and placeholder arguments. Do not write to
  System.out, System.err or printStackTrace, and do not build log messages by
  string concatenation.
- Mark every interface or superclass override with @Override.
- When equals is overridden, implement hashCode consistently. Use
  Objects.equals or a constant-first comparison for nullable strings.
- Replace repeated protocol, permission, timeout and size literals with named
  constants. Keep security and wire values in one authoritative definition.
- Give switch statements an explicit default or a documented exhaustive
  enum/switch-expression reason. Keep conditional nesting shallow and extract
  complicated predicates into named boolean variables or methods.
- Parameterize every SQL value. Never concatenate request data into SQL, and
  keep transaction rollback behavior explicit when a write fails.
- Name tests after the class or behavior under test and keep them deterministic,
  isolated and free of production credentials or business data.

These rules apply immediately to new and modified code. Existing violations
should be removed when the surrounding file or module is changed, without
creating unrelated repository-wide formatting churn.

## Frontend TypeScript and React rules

Apply these rules to every new or modified file under console-ui/:

- Keep TypeScript strictness enabled. Do not add any, @ts-ignore or
  @ts-expect-error to bypass type errors. Use a domain type, unknown with
  narrowing, or a typed API response instead.
- Keep API calls in the api/ client modules. Components must use typed request
  and response models and must not duplicate authentication, CSRF, timeout or
  error-normalization logic.
- Use function components and hooks consistently. Every effect that creates a
  timer, listener, request or object URL must clean it up on unmount and
  invalidate stale asynchronous results.
- Use stable domain keys for rendered lists. Do not use an array index as a key
  for mutable or reorderable data.
- Put all user-visible text, validation messages and notifications in the
  existing i18n catalogs. Do not add production UI text directly to JSX.
- Use semantic HTML and accessible Ant Design controls. Interactive elements
  need an accessible name, keyboard operation and visible state feedback.
- Keep raw HTML insertion, direct DOM mutation and browser storage access behind
  a reviewed helper. Never render untrusted HTML.
- Report handled request failures through the existing notification and
  translation paths. Do not leave console logging in production code.
- Keep components focused. Extract repeated forms, tables, request states and
  authorization checks instead of creating large page components.
- Keep CSS class names scoped to their component or page and preserve the
  existing responsive layout. Avoid fixed dimensions that clip translated text.

Frontend verification requires npm test and npm run build. A frontend change
is not complete when TypeScript builds only; its state transitions, permission
boundaries, stale-response handling and user-visible error paths need focused
tests where they are affected.

## Security and dependencies

- Treat dependency and image vulnerability reports as release blockers at
  critical and high severity. Upgrade to a supported fixed version when one is
  available, then rerun the scanner.
- Use small, mainstream, actively maintained base images with pinned digests
  where the deployment workflow supports pinning. Keep runtime images minimal
  and install only required packages.
- Keep Console cryptography on JDK providers and the existing BC-free adapter.
  Algorithms requiring an additional provider must fail closed and be
  documented.
- Keep secrets, private keys, CSRs and certificate material out of logs and
  audit fields. Audit ordinary GET, FETCH and HEAD reads only when the request
  downloads or exports data.
- Validate untrusted JSON, paths, identifiers, numeric limits and remote
  responses at the boundary. Remote writes must not be retried automatically.

## Backend and UI behavior

- Keep Certificate Management as a first-level navigation group. Use distinct
  visual treatment for non-clickable group headings and clickable destinations.
- Keep CA hierarchy and CA enrollments as separate navigable views. Render the
  hierarchy as a tree and preserve scoped authorization on every operation.
- When CA, certificate or profile context changes, clear stale data and reject
  late responses so an action cannot target an old resource.
- Intermediate CA creation must populate Parent CA and SubCA profile options
  from current, authorized XiPKI data. Empty or invalid options must block
  submission.
- Certificate search controls must use bounded inputs, date/time controls for
  time values, explicit sort order and a real pagination component. Details
  should remain readable at narrow widths.
- Keep the dashboard's Get started flow open by default and ensure every
  diagram or audit step is fully visible at supported viewport sizes.

## Deployment and scripts

- Keep deployment assets under deploy/. The Console image is separate from the
  CA distribution image.
- The current XiPKI upstream master does not provide the historical
  docker/ca/Dockerfile. CA_IMAGE must be supplied separately and satisfy the
  management-client and /ca/mgmt provisioning contract.
- Bind the local Compose web service to 0.0.0.0 by default for LAN access,
  while retaining an explicit loopback override.
- Shell scripts declared as POSIX sh must remain portable to macOS and minimal
  Linux hosts. Check required tools before stopping, rebuilding or restarting
  services.
- Service checks must distinguish a running container from a ready application.
  Retry expected connection failures until a bounded timeout, then report
  diagnostics.

## Verification and git hygiene

Before claiming a change is complete:

1. Run the relevant frontend tests and production build.
2. Run the backend clean package and tests. Use the disposable MariaDB
   configuration to cover integration tests when database paths are changed.
3. Run sh -n for changed POSIX scripts, git diff --check, and a dependency or
   image scan when dependencies or images changed.
4. Review the final diff for semantic changes, generated files, secrets and
   unrelated edits.

Keep commits small and focused. Stage only files required for the task; do not
commit build output, local secrets, database data, temporary images or
workstation-specific files. Push the feature branch only after the verification
commands pass.

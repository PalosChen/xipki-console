#!/bin/sh
# Release/development host: Linux/macOS, POSIX sh. Requires npm, Trivy and Docker.
# Known vulnerability findings block release. No vulnerability suppression file is used.
set -eu
script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
console_root=$(CDPATH= cd -- "$script_dir/.." && pwd)
for dependency in npm trivy docker; do
  command -v "$dependency" >/dev/null 2>&1 || { echo "Required tool is missing: $dependency" >&2; exit 1; }
done
: "${WEB_IMAGE:?Set WEB_IMAGE to the built Console image}"
: "${DB_IMAGE:?Set DB_IMAGE to the built database image}"
: "${CA_IMAGE:?Set CA_IMAGE to the actual CA distribution image}"
docker info >/dev/null 2>&1 || { echo 'Docker is unavailable' >&2; exit 1; }
failed=0
echo 'Checking packaged Console runtime libraries'
for library in "$console_root"/console-server/target/lib/*.jar; do
  test -f "$library" || { echo 'Console runtime libraries are missing; run the build first' >&2; exit 1; }
  case "$(basename "$library")" in
    bc*.jar) echo "Bouncy Castle is not permitted in the Console runtime: $library" >&2; failed=1 ;;
  esac
done
echo 'Auditing all frontend dependencies, including build tools'
if ! npm --prefix "$console_root/console-ui" audit --audit-level=low; then failed=1; fi
for image in "$WEB_IMAGE" "$DB_IMAGE" "$CA_IMAGE"; do
  echo "Scanning image for known vulnerabilities: $image"
  if ! trivy image --scanners vuln --exit-code 1 --ignorefile /dev/null --ignore-unfixed=false \
    --severity UNKNOWN,LOW,MEDIUM,HIGH,CRITICAL \
    --db-repository ghcr.io/aquasecurity/trivy-db:2 \
    --java-db-repository ghcr.io/aquasecurity/trivy-java-db:1 "$image"; then
    failed=1
  fi
done
if test "$failed" != 0; then
  echo 'Security check failed: known vulnerabilities or scan errors remain. Do not publish this image set as vulnerability-free.' >&2
  exit 1
fi
echo 'Security checks passed: no known vulnerabilities were reported by npm or Trivy.'

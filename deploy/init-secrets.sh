#!/bin/sh
# Linux/macOS Docker host, POSIX sh. Docker and base OS utilities; no host Java/Node/OpenSSL.
set -eu
umask 077
for dependency in docker mktemp mkdir chmod ln rm rmdir wc tr; do
  command -v "$dependency" >/dev/null 2>&1 || { echo "Required tool is missing: $dependency" >&2; exit 1; }
done
docker info >/dev/null 2>&1 || { echo 'Docker is unavailable' >&2; exit 1; }
cd "$(dirname "$0")"
test -n "${CA_IMAGE:-}" || { echo 'Set CA_IMAGE to the release image before initialization' >&2; exit 1; }
# Check the required image/tool before creating any private files.
docker run --rm --entrypoint sh "$CA_IMAGE" -ec 'command -v openssl >/dev/null' >/dev/null
if test -L secrets || { test -e secrets && test ! -d secrets; }; then
  echo 'secrets exists but is not a directory' >&2
  exit 1
fi
mkdir -p secrets
chmod 0755 secrets
staging=$(mktemp -d ./secrets/.init.XXXXXX)
trap 'rm -f "$staging/password"; rmdir "$staging"' EXIT HUP INT TERM
for secret in admin-password web-db-password web-db-root-password; do
  if test -L "secrets/$secret" || { test -e "secrets/$secret" && { test ! -f "secrets/$secret" || test ! -s "secrets/$secret"; }; }; then
    echo "Invalid or empty secret file: $secret; preserved without changes" >&2
    exit 1
  fi
  if test ! -e "secrets/$secret"; then
    docker run --rm --entrypoint sh "$CA_IMAGE" -ec 'openssl rand -base64 36' > "$staging/password"
    test -s "$staging/password" || { echo 'Secret generation failed' >&2; exit 1; }
    ln "$staging/password" "secrets/$secret"
    rm "$staging/password"
  fi
done
if test -L secrets/enrollment-key || { test -e secrets/enrollment-key && test ! -f secrets/enrollment-key; }; then
  echo 'Invalid enrollment encryption secret; preserved without changes' >&2; exit 1
fi
if test ! -e secrets/enrollment-key; then
  docker run --rm --entrypoint sh "$CA_IMAGE" -ec 'openssl rand 32' > "$staging/password"
  test "$(wc -c < "$staging/password" | tr -d ' ')" = 32 || { echo 'Encryption secret generation failed' >&2; exit 1; }
  ln "$staging/password" secrets/enrollment-key
  rm "$staging/password"
fi
test "$(wc -c < secrets/enrollment-key | tr -d ' ')" = 32 || { echo 'Enrollment encryption secret must contain exactly 32 bytes; preserved without changes' >&2; exit 1; }
# On Linux only the Web UID (or a host administrator) can read the files.
docker run --rm --user 0:0 --entrypoint sh -v "$PWD/secrets:/secrets" "$CA_IMAGE" -ec \
  'chown 10002:10002 /secrets/admin-password /secrets/web-db-password /secrets/web-db-root-password /secrets/enrollment-key; chmod 0600 /secrets/admin-password /secrets/web-db-password /secrets/web-db-root-password /secrets/enrollment-key'
echo 'Created Web and database secrets (admin password, Web DB password and MariaDB root password). Store them in a password manager.'

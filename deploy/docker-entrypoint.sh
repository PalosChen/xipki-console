#!/bin/sh
set -eu

: "${WEB_DB_URL:=jdbc:mariadb://web-db:3306/xipki_web}"
: "${WEB_DB_USER:=xipki_web}"
: "${SERVER_PORT:=8080}"
export WEB_DB_URL WEB_DB_USER SERVER_PORT

# This script runs inside the Linux container. Hosted keys must survive replacement.
if test -n "${WEB_ENROLLMENT_KEY_FILE:-}${WEB_ENROLLMENT_KEY_DIR:-}"; then
  command -v wc >/dev/null 2>&1 && command -v tr >/dev/null 2>&1 && command -v awk >/dev/null 2>&1 || {
    echo 'Enrollment startup validation requires wc, tr and awk' >&2; exit 1;
  }
  test -n "${WEB_ENROLLMENT_KEY_FILE:-}" && test -n "${WEB_ENROLLMENT_KEY_DIR:-}" \
    && test -r "$WEB_ENROLLMENT_KEY_FILE" && test -d "$WEB_ENROLLMENT_KEY_DIR" \
    && test -w "$WEB_ENROLLMENT_KEY_DIR" || { echo 'Enrollment secret and persistent key directory are required' >&2; exit 1; }
  test "$(wc -c < "$WEB_ENROLLMENT_KEY_FILE" | tr -d ' ')" = 32 || { echo 'Enrollment encryption secret must contain exactly 32 bytes' >&2; exit 1; }
  case "$WEB_ENROLLMENT_KEY_DIR" in /*) ;; *) echo 'Enrollment key directory requires an absolute persistent mount' >&2; exit 1;; esac
  enrollment_directory=$(cd "$WEB_ENROLLMENT_KEY_DIR" && pwd -P)
  test -r /proc/self/mountinfo && awk -v directory="$enrollment_directory" '
    (directory == $5 || index(directory, $5 "/") == 1) && length($5) > longest {
      longest=length($5); valid=0
      for(i=7;i<=NF;i++) if($i == "-" && $5 != "/" && $(i+1) != "tmpfs" && $(i+1) != "ramfs")valid=1
    }
    END {exit !valid}
  ' /proc/self/mountinfo || { echo 'Enrollment key directory requires an external persistent mount (not the container layer or tmpfs)' >&2; exit 1; }
fi

exec java -Dsun.net.client.defaultConnectTimeout=3000 -Dsun.net.client.defaultReadTimeout=3000 ${JAVA_OPTS:-} -cp 'app.jar:lib/*' org.xipki.mgmt.web.Main

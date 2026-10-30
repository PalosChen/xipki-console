#!/bin/sh
# Execute inside a disposable Linux container as an unprivileged user.
# Uses the real container entrypoint; only the final Java process is a boundary double.
set -eu
script=${TEST_ENTRYPOINT:-/workspace/deploy/docker-entrypoint.sh}
fixture=$(mktemp -d /tmp/xipki-enrollment-entrypoint.XXXXXX)
persistent=$(mktemp -d "${TEST_KEY_MOUNT:?Mount a disposable host directory}/entrypoint.XXXXXX")
trap 'rm -f "$fixture/java" "$fixture/secret" "$fixture/output" "$fixture/missing-tools/java"; rmdir "$fixture/missing-tools" "$fixture/keys" "$fixture" "$persistent"' EXIT HUP INT TERM
mkdir "$fixture/keys" "$fixture/missing-tools"
printf '#!/bin/sh\nprintf "JAVA_STARTED\\n"\n' > "$fixture/java"
chmod 700 "$fixture/java"
ln -s "$fixture/java" "$fixture/missing-tools/java"
dd if=/dev/zero of="$fixture/secret" bs=32 count=1 2>/dev/null
export WEB_ADMIN_PASSWORD=fixture-only WEB_ENROLLMENT_KEY_FILE="$fixture/secret" WEB_ENROLLMENT_KEY_DIR="$fixture/keys"
if PATH="$fixture:/usr/bin:/bin" /bin/sh "$script" > "$fixture/output" 2>&1; then echo 'Container writable layer was accepted as persistent storage' >&2; exit 1; fi
grep -q 'persistent mount' "$fixture/output"
export WEB_ENROLLMENT_KEY_DIR="$persistent"
PATH="$fixture:/usr/bin:/bin" /bin/sh "$script" > "$fixture/output" 2>&1
grep -q JAVA_STARTED "$fixture/output"
if PATH="$fixture/missing-tools" /bin/sh "$script" > "$fixture/output" 2>&1; then echo 'Missing wc/tr was accepted' >&2; exit 1; fi
grep -q 'requires wc, tr and awk' "$fixture/output"
dd if=/dev/zero of="$fixture/secret" bs=31 count=1 2>/dev/null
if PATH="$fixture:/usr/bin:/bin" /bin/sh "$script" > "$fixture/output" 2>&1; then echo 'Invalid secret was accepted' >&2; exit 1; fi
grep -q 'exactly 32 bytes' "$fixture/output"
dd if=/dev/zero of="$fixture/secret" bs=32 count=1 2>/dev/null
export WEB_ENROLLMENT_KEY_DIR="$fixture/keys"
chmod 500 "$fixture/keys"
if PATH="$fixture:/usr/bin:/bin" /bin/sh "$script" > "$fixture/output" 2>&1; then echo 'Unwritable volume was accepted' >&2; exit 1; fi
grep -q 'persistent key directory' "$fixture/output"
chmod 700 "$fixture/keys"
export WEB_ENROLLMENT_KEY_DIR="$persistent"
WEB_ENROLLMENT_KEY_FILE="$fixture/not-present"
export WEB_ENROLLMENT_KEY_FILE
if PATH="$fixture:/usr/bin:/bin" /bin/sh "$script" > "$fixture/output" 2>&1; then echo 'Missing secret was accepted' >&2; exit 1; fi
grep -q 'persistent key directory' "$fixture/output"
echo 'PASS: Linux entrypoint accepts a persistent mount and rejects container layer storage, missing tools, invalid/missing secret and unwritable volume before Java'

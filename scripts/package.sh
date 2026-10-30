#!/bin/sh
# Development host: Linux/macOS, POSIX sh. Build, then archive the host distribution.
set -eu
script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
console_root=$(CDPATH= cd -- "$script_dir/.." && pwd)
for dependency in tar gzip mktemp mkdir mv rm; do
  command -v "$dependency" >/dev/null 2>&1 || { echo "Required tool is missing: $dependency" >&2; exit 1; }
done
sh "$script_dir/build.sh" "$@"
mkdir -p "$console_root/dist"
package_file=$(mktemp "$console_root/dist/.package.XXXXXX")
trap 'rm -f "$package_file"' 0
trap 'exit 1' HUP INT TERM
tar -czf "$package_file" \
  -C "$console_root/console-server/target" xipki-console-server-1.0.0-SNAPSHOT.jar lib \
  -C "$console_root" README.md README.zh-CN.md guides scripts/README.md LICENSE
mv "$package_file" "$console_root/dist/xipki-console-1.0.0-SNAPSHOT.tar.gz"
echo 'Package ready: dist/xipki-console-1.0.0-SNAPSHOT.tar.gz'

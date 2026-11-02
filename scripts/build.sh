#!/bin/sh
# Development host: Linux/macOS, POSIX sh; requires JDK 25+, Maven and Node 22.16+.
set -eu
script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
console_root=$(CDPATH= cd -- "$script_dir/.." && pwd)
build_image=false
offline=false
for option in "$@"; do
  case "$option" in
    --docker) build_image=true ;;
    --offline) offline=true ;;
    *) echo 'Usage: sh scripts/build.sh [--docker] [--offline]' >&2; exit 2 ;;
  esac
done
for dependency in node npm mvn awk; do
  command -v "$dependency" >/dev/null 2>&1 || { echo "Required tool is missing: $dependency" >&2; exit 1; }
done
if test -n "${JAVA_HOME:-}"; then
  java_command=$JAVA_HOME/bin/java
  javac_command=$JAVA_HOME/bin/javac
else
  java_command=java
  javac_command=javac
fi
command -v "$java_command" >/dev/null 2>&1 && command -v "$javac_command" >/dev/null 2>&1 || {
  echo 'A JDK is required; set JAVA_HOME to JDK 25 or newer' >&2; exit 1;
}
node -e 'const [major,minor]=process.versions.node.split(".").map(Number); if(major<22 || (major===22 && minor<16)){console.error("Node.js 22.16+ is required");process.exit(1)}'
"$javac_command" -version 2>&1 | awk '
  {split($2,version,"."); if(version[1] >= 25) valid=1}
  END {if(!valid){print "JDK 25+ is required";exit 1}}
'
if test "$build_image" = true; then
  command -v docker >/dev/null 2>&1 || { echo 'Docker is required for --docker' >&2; exit 1; }
  docker info >/dev/null 2>&1 || { echo 'Docker is unavailable' >&2; exit 1; }
fi
if test -n "${XIPKI_SOURCE_DIR:-}"; then
  test -f "$XIPKI_SOURCE_DIR/pom.xml" || { echo 'XIPKI_SOURCE_DIR must identify the matching XiPKI source checkout' >&2; exit 1; }
  echo 'Installing XiPKI util module dependencies from XIPKI_SOURCE_DIR'
  if test "$offline" = true; then
    mvn -o -f "$XIPKI_SOURCE_DIR/pom.xml" -pl util -am -DskipTests install
  else
    mvn -f "$XIPKI_SOURCE_DIR/pom.xml" -pl util -am -DskipTests install
  fi
fi
cd "$console_root/console-ui"
if test "$offline" = true; then
  npm ci --offline --no-audit --no-fund
else
  npm ci --no-audit --no-fund
fi
npm test
npm run build
cd "$console_root"
if test "$offline" = true; then
  mvn -o -f console-server/pom.xml clean package
else
  mvn -f console-server/pom.xml clean package
fi
if test "$build_image" = true; then
  docker build -t "${DB_IMAGE:-xipki-console-db:local}" -f deploy/mariadb.Dockerfile .
  docker build -t "${WEB_IMAGE:-xipki-console:local}" -f deploy/Dockerfile .
fi
echo 'Build complete: console-server/target/ (application JAR and lib/)'

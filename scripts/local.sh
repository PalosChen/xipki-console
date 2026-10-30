#!/bin/sh
# Docker host: Linux/macOS, POSIX sh, Docker Compose v2 with --wait-timeout.
# Uses exported settings; never evaluates .env files as shell code.
set -eu
script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
console_root=$(CDPATH= cd -- "$script_dir/.." && pwd)
action=${1:-up}
test "$#" -le 1 || { echo 'Usage: sh scripts/local.sh [up|stop|status|logs]' >&2; exit 2; }
case "$action" in
  up|stop|status|logs) ;;
  *) echo 'Usage: sh scripts/local.sh [up|stop|status|logs]' >&2; exit 2 ;;
esac
command -v docker >/dev/null 2>&1 || { echo 'Docker is required' >&2; exit 1; }
docker info >/dev/null 2>&1 || { echo 'Docker is unavailable' >&2; exit 1; }
docker compose version >/dev/null
: "${CA_IMAGE:=xipki-ca:security-update}"
: "${WEB_IMAGE:=xipki-console:local}"
: "${COMPOSE_PROJECT_NAME:=xipki-console-local}"
export CA_IMAGE WEB_IMAGE COMPOSE_PROJECT_NAME
cd "$console_root"
compose() {
  docker compose --env-file /dev/null -p "$COMPOSE_PROJECT_NAME" -f "$console_root/deploy/compose.yml" "$@"
}
case "$action" in
  up)
    for dependency in grep dirname mktemp mkdir chmod ln rm rmdir wc tr; do
      command -v "$dependency" >/dev/null 2>&1 || { echo "Required tool is missing: $dependency" >&2; exit 1; }
    done
    wait_seconds=${WAIT_SECONDS:-300}
    case "$wait_seconds" in ''|*[!0-9]*|0) echo 'WAIT_SECONDS must be a positive number of seconds' >&2; exit 1;; esac
    docker compose up --help | grep -q -- '--wait-timeout' || {
      echo 'Upgrade Docker Compose v2: --wait-timeout is required' >&2; exit 1;
    }
    docker image inspect "$CA_IMAGE" >/dev/null 2>&1 || {
      echo "Missing CA image: $CA_IMAGE; build or download the matching XiPKI Docker distribution first" >&2; exit 1;
    }
    docker image inspect "$WEB_IMAGE" >/dev/null 2>&1 || {
      echo "Missing console image: $WEB_IMAGE; run sh scripts/build.sh --docker first" >&2; exit 1;
    }
    compose config --quiet
    sh "$console_root/deploy/init-secrets.sh"
    echo 'Starting local CA, MariaDB and Console; waiting for application health'
    if ! compose up -d --wait --wait-timeout "$wait_seconds"; then
      compose ps -a
      echo 'Startup failed or timed out; data preserved. Run sh scripts/local.sh logs for diagnostics.' >&2
      exit 1
    fi
    for service in web-db ca web; do
      container_id=$(compose ps -a -q "$service")
      test -n "$container_id" || { echo "Missing container: $service" >&2; exit 1; }
      state=$(docker inspect --format '{{.State.Status}} {{.State.Restarting}} {{if .State.Health}}{{.State.Health.Status}}{{end}}' "$container_id")
      test "$state" = 'running false healthy' || { echo "$service is not ready: $state" >&2; exit 1; }
    done
    echo "Console listening on ${WEB_BIND_ADDRESS:-0.0.0.0}:${WEB_PORT:-8080}"
    echo "Open http://<host-LAN-IP>:${WEB_PORT:-8080} from another device"
    echo "Initial user: ${WEB_ADMIN_USERNAME:-admin}; initial password file: deploy/secrets/admin-password"
    echo 'Existing account passwords are preserved. First login requires a password change.'
    ;;
  stop) compose stop ;;
  status) compose ps -a ;;
  logs) compose logs --tail=100 -f ;;
esac

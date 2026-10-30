#!/bin/sh
# Linux Docker host. Requires /proc/uptime, awk, sleep and Compose v2.
set -eu
for dependency in docker awk sleep; do command -v "$dependency" >/dev/null 2>&1 || exit 1; done
test -r /proc/uptime || { echo 'Run on Linux; on macOS use docker compose ps to inspect health' >&2; exit 1; }
docker compose version >/dev/null
cd "$(dirname "$0")"
deadline=$(( $(awk '{print int($1)}' /proc/uptime) + ${WAIT_SECONDS:-300} ))
next_report=0
while :; do
  ready=true
  for service in web-db ca web; do
    id=$(docker compose ps -a -q "$service")
    test -n "$id" || { ready=false; continue; }
    status=$(docker inspect --format '{{.State.Status}} {{.State.Restarting}}' "$id")
    case $status in
      'exited false'|'dead false') echo "$service stopped; inspect docker compose logs $service" >&2; exit 1 ;;
      'running false') ;;
      *) ready=false; continue ;;
    esac
    health=$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{end}}' "$id")
    test "$health" = healthy || ready=false
  done
  test "$ready" = false || { echo 'MariaDB, CA and Web are ready'; exit 0; }
  now=$(awk '{print int($1)}' /proc/uptime)
  test "$now" -lt "$deadline" || { docker compose ps -a; echo 'Readiness timed out; data preserved' >&2; exit 1; }
  if test "$now" -ge "$next_report"; then echo 'Waiting for MariaDB, CA and Web application readiness'; next_report=$((now + 10)); fi
  sleep 2
done

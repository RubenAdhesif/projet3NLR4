#!/usr/bin/env bash
# Build and start all 4 services (logs in logs/). Ctrl+C stops everything.
#
# Usage:
#   ./run.sh                       # build + start
#   SKIP_BUILD=1 ./run.sh          # start only (skip mvn package)
#   T1=60 ./run.sh                 # override lamp extinction delay (default: 10s)
set -e
cd "$(dirname "$0")"

# ── Build ──────────────────────────────────────────────────────────────────────
if [ "${SKIP_BUILD:-0}" != "1" ]; then
  echo "Building all modules..."
  mvn -q -DskipTests package
  echo "Build OK."
fi

mkdir -p logs

pids=()
start() {
  local jar="$1" name="$2"
  java -jar "$jar" > "logs/$name.log" 2>&1 &
  pids+=($!)
  echo "  started $name (pid $!, log: logs/$name.log)"
}

trap 'echo; echo "Stopping all services..."; kill "${pids[@]}" 2>/dev/null; wait; echo "Done."' INT TERM EXIT

# ── Start Gateway first, then Things ──────────────────────────────────────────
echo ""
echo "Starting services..."
start gateway/target/gateway.jar gateway

# Wait for the gateway to be ready before starting the Things
# so their first registration attempt succeeds immediately.
echo -n "  Waiting for gateway to be ready..."
for i in $(seq 1 20); do
  if curl -s --max-time 1 http://localhost:8080/ > /dev/null 2>&1; then
    echo " ready."
    break
  fi
  sleep 0.5
done

start thing-thermostat/target/thing-thermostat.jar thermostat
start thing-lamp/target/thing-lamp.jar             lamp
start thing-motion/target/thing-motion.jar         motion

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  Smart Lab on the Web — all services started"
echo "  Dashboard  : http://localhost:8080/           (token: operator-secret)"
echo "  Thermostat : http://localhost:8081/properties"
echo "  Lamp       : http://localhost:8082/properties"
echo "  Motion     : http://localhost:8083/properties"
echo "  Run tests  : bash check-routes.sh"
echo "  Ctrl+C to stop all services"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
wait

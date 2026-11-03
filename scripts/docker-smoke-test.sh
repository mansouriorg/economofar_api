COMPOSE_DIR=$(cd "$(dirname "$0")/.." && pwd)
BASE_URL="${BASE_URL:-http://localhost:8080}"
WAIT_SECONDS="${WAIT_SECONDS:-180}"
CONTAINER="${CONTAINER:-economofar-api}"
DEMO_EMAIL="demo@economofar.app"
DEMO_PASSWORD="Demo2026Pass"

fail() {
    echo "SMOKE TEST FAILED: $1"
    (cd "$COMPOSE_DIR" && docker compose logs --tail 40 api 2>&1)
    exit 1
}

status_of() {
    curl -s -o /dev/null -m 15 -w "%{http_code}" "$@"
}

echo "Waiting for the API and its demo data (at most $WAIT_SECONDS seconds)"

waited=0

while true; do
    state=$(docker inspect -f '{{.State.Status}}' "$CONTAINER" 2>/dev/null)

    if [ "$state" = "running" ] && (cd "$COMPOSE_DIR" && docker compose logs api 2>&1 | grep -q "Demo data committed"); then
        break
    fi

    if [ "$state" = "created" ] || [ "$state" = "exited" ] || [ "$state" = "dead" ] || [ "$state" = "restarting" ] || [ -z "$state" ]; then
        fail "the container $CONTAINER is not running (state: ${state:-missing}). If the start failed because port 8080 is used, stop the API that you started with mvn"
    fi

    if [ "$waited" -ge "$WAIT_SECONDS" ]; then
        restarts=$(docker inspect -f '{{.RestartCount}}' "$CONTAINER" 2>/dev/null)
        fail "the demo data was not ready after $WAIT_SECONDS seconds (state: $state, restarts: ${restarts:-unknown}). Is the demo profile on? Did the API crash and restart?"
    fi

    sleep 3
    waited=$((waited + 3))
done

echo "ok   the API started and the demo data is there ($waited seconds)"

answer=$(curl -s -m 20 -X POST "$BASE_URL/api/auth/authenticate" -H "Content-Type: application/json" -d "{\"email\":\"$DEMO_EMAIL\",\"password\":\"$DEMO_PASSWORD\"}")
token=$(printf '%s' "$answer" | sed -n 's/.*"token"[ ]*:[ ]*"\([^"]*\)".*/\1/p')

if [ -z "$token" ]; then
    fail "the sign-in of the demo account gave no token (answer: $answer). If you changed the password of the demo account, run docker compose down -v first"
fi

echo "ok   sign-in of the demo account"

for endpoint in /api/analytics/year /api/analytics/repurchase /api/profile; do
    code=$(status_of "$BASE_URL$endpoint" -H "Authorization: Bearer $token")

    if [ "$code" = "200" ]; then
        echo "ok   GET $endpoint with the token: 200"
    else
        fail "GET $endpoint with the token answered $code instead of 200"
    fi
done

anonymous=$(status_of "$BASE_URL/api/analytics/year")

if [ "$anonymous" = "401" ] || [ "$anonymous" = "403" ]; then
    echo "ok   GET /api/analytics/year without a token: $anonymous"
else
    fail "a request without a token answered $anonymous instead of 401 or 403"
fi

if docker exec "$CONTAINER" sh -c 'touch /app/uploads/.write-test && rm /app/uploads/.write-test'; then
    echo "ok   the uploads folder of the container can be written"
else
    fail "the container cannot write in /app/uploads"
fi

user_id=$(docker exec "$CONTAINER" id -u 2>/dev/null)

if [ "$user_id" = "0" ]; then
    fail "the API runs as root in the container"
fi

echo "ok   the API runs as a user without rights (id $user_id)"
if docker exec "$CONTAINER" timeout 5 bash -c 'exec 3<>/dev/tcp/host.docker.internal/11434' 2>/dev/null; then
    echo "ok   Ollama on your computer is reachable from the container (receipts can be scanned)"
else
    echo "info Ollama is not reachable from the container: start Ollama on your computer to scan receipts"
fi

echo "SMOKE TEST OK"

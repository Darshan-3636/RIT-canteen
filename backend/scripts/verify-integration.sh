#!/usr/bin/env bash
set -euo pipefail

# Phase C only: tests the existing Phase B surface. It does not merge or mount modules.
cd "$(dirname "${BASH_SOURCE[0]}")/.."

fail() { printf 'Integration verification failed: %s\n' "$1" >&2; exit 1; }
for tool in curl jq; do command -v "$tool" >/dev/null || fail "missing $tool"; done
: "${DATABASE_URL:?Set DATABASE_URL from a private local .env}"
: "${DATABASE_USER:?Set DATABASE_USER}"
: "${DATABASE_PASSWORD:?Set DATABASE_PASSWORD}"
: "${RIT_TEST_DATABASE_URL:?Set a separate disposable RIT_TEST_DATABASE_URL}"
: "${RIT_TEST_DATABASE_USER:?Set RIT_TEST_DATABASE_USER}"
: "${RIT_TEST_DATABASE_PASSWORD:?Set RIT_TEST_DATABASE_PASSWORD}"
[[ "${RIT_ENV:-}" == local && "${DEMO_SEED_ENABLED:-}" == true ]] || fail "local demo seed mode is required for persona checks"
[[ "${HTTP_HOST:-127.0.0.1}" == 127.0.0.1 ]] || fail "HTTP_HOST must be 127.0.0.1"
[[ "${RIT_INTEGRATION_DISPOSABLE_APP_DB:-}" == YES ]] || fail "confirm disposable app DB with RIT_INTEGRATION_DISPOSABLE_APP_DB=YES"
[[ "${RIT_INTEGRATION_DISPOSABLE_TEST_DB:-}" == YES ]] || fail "confirm disposable test DB with RIT_INTEGRATION_DISPOSABLE_TEST_DB=YES"
[[ "$DATABASE_URL" != "$RIT_TEST_DATABASE_URL" ]] || fail "app and test DB URLs must differ"
[[ "$DATABASE_URL" =~ ^jdbc:postgresql://(127\.0\.0\.1|localhost):[0-9]+/rit_canteen_dev$ ]] || fail "app DB must be loopback rit_canteen_dev"
[[ "$RIT_TEST_DATABASE_URL" =~ ^jdbc:postgresql://(127\.0\.0\.1|localhost):[0-9]+/rit_canteen_test$ ]] || fail "test DB must be loopback rit_canteen_test"

if [[ "${1:-}" != --smoke-only ]]; then
    ./gradlew clean build installDist --no-daemon --max-workers=1
else
    ./gradlew installDist --no-daemon --max-workers=1
fi

base="http://127.0.0.1:${HTTP_PORT:-8080}"
if curl --silent --max-time 2 "$base/health" >/dev/null; then fail "HTTP port is already in use"; fi
server_log="$(mktemp)"
server_pid=""
cleanup() {
    if [[ -n "$server_pid" ]]; then
        kill "$server_pid" 2>/dev/null || true
        wait "$server_pid" 2>/dev/null || true
    fi
    rm -f -- "$server_log"
}
trap cleanup EXIT
"$PWD/build/install/rit-canteen-backend/bin/rit-canteen-backend" >"$server_log" 2>&1 &
server_pid=$!

ready=false
for _ in {1..30}; do
    if curl --silent --fail --max-time 2 "$base/health" >/dev/null; then ready=true; break; fi
    if ! kill -0 "$server_pid" 2>/dev/null; then fail "server exited before health check"; fi
    sleep 1
done
[[ "$ready" == true ]] || fail "server did not become healthy within 30 seconds"

http_status=""
http_body=""
request() {
    local method="$1" path="$2" body="${3:-}" token="${4:-}"
    local args=(--silent --show-error --max-time 15 -X "$method" -w $'\n%{http_code}')
    [[ -z "$body" ]] || args+=(-H 'Content-Type: application/json' --data "$body")
    [[ -z "$token" ]] || args+=(-H "Authorization: Bearer $token")
    local response
    response="$(curl "${args[@]}" "$base$path")" || fail "HTTP request failed for $method $path"
    http_status="${response##*$'\n'}"
    http_body="${response%$'\n'*}"
}
expect_status() { [[ "$http_status" == "$1" ]] || fail "$2: expected $1, got $http_status"; }
expect_json() { jq -e "$1" >/dev/null <<<"$http_body" || fail "$2: unexpected JSON shape"; }

request GET /health; expect_status 200 health; expect_json '.status == "ok"' health
request GET /ready; expect_status 200 readiness; expect_json '.status == "ready"' readiness

email="phase-c-$(date +%s)-$$@example.test"
password='LocalIntegrationPass01'
register_json="$(jq -nc --arg email "$email" --arg password "$password" \
    '{fullName:"Integration Tester",email:$email,password:$password,affiliation:"STUDENT",department:"CSE"}')"
request POST /api/v1/auth/register "$register_json"
expect_status 201 registration
expect_json '.user.platformAdmin == false and (.user.memberships | length) == 0 and (has("accessToken") | not)' registration

login_json="$(jq -nc --arg email "$email" --arg password "$password" '{email:$email,password:$password}')"
request POST /api/v1/auth/login "$login_json"
expect_status 200 login
expect_json '(.accessToken | length) >= 40 and (.expiresAt | endswith("Z"))' login
token="$(jq -r '.accessToken' <<<"$http_body")"
request GET /api/v1/auth/me '' "$token"; expect_status 200 auth-me
expect_json '.user.platformAdmin == false and (has("accessToken") | not)' auth-me
request GET /api/v1/me/profile '' "$token"; expect_status 200 profile-get
expect_json '.fullName == "Integration Tester" and .department == "CSE"' profile-get
request PATCH /api/v1/me/profile '{"fullName":"Updated Integration Tester","department":null}' "$token"
expect_status 200 profile-patch
expect_json '.fullName == "Updated Integration Tester" and .department == null' profile-patch
request POST /api/v1/auth/logout '' "$token"; expect_status 204 logout
[[ -z "$http_body" ]] || fail "logout must have an empty body"
request GET /api/v1/auth/me '' "$token"; expect_status 401 revoked-token
expect_json '.error.code == "UNAUTHENTICATED" and .error.details == null' revoked-token
unset token

verify_persona() {
    local label="$1" email="$2" password="$3" role="$4" seller="$5"
    local body
    body="$(jq -nc --arg email "$email" --arg password "$password" '{email:$email,password:$password}')"
    request POST /api/v1/auth/login "$body"; expect_status 200 "$label login"
    local persona_token
    persona_token="$(jq -r '.accessToken' <<<"$http_body")"
    request GET /api/v1/auth/me '' "$persona_token"; expect_status 200 "$label me"
    if [[ "$role" == ADMIN ]]; then
        expect_json '.user.platformAdmin == true and (.user.memberships | length) == 0' "$label authority"
    elif [[ "$role" == CUSTOMER ]]; then
        expect_json '.user.platformAdmin == false and (.user.memberships | length) == 0' "$label authority"
    else
        jq -e --arg role "$role" --arg seller "$seller" \
            '.user.platformAdmin == false and (.user.memberships | length) == 1 and .user.memberships[0].role == $role and .user.memberships[0].sellerId == $seller and (.user.memberships[0].capabilities | length) > 0' \
            >/dev/null <<<"$http_body" || fail "$label authority: wrong role, seller or capabilities"
    fi
    request POST /api/v1/auth/logout '' "$persona_token"; expect_status 204 "$label logout"
}

verify_persona student-arya student.arya@demo.ritcanteen.test 'RitDemo!Student01' CUSTOMER ''
verify_persona student-kiran student.kiran@demo.ritcanteen.test 'RitDemo!Student02' CUSTOMER ''
verify_persona faculty faculty.maya@demo.ritcanteen.test 'RitDemo!Faculty01' CUSTOMER ''
verify_persona main-owner owner.main@demo.ritcanteen.test 'RitDemo!OwnerMain01' OWNER main-canteen
verify_persona north-owner owner.secondary@demo.ritcanteen.test 'RitDemo!OwnerOther01' OWNER north-counter
verify_persona manager manager.main@demo.ritcanteen.test 'RitDemo!Manager01' MANAGER main-canteen
verify_persona staff staff.main@demo.ritcanteen.test 'RitDemo!Staff01' STAFF main-canteen
verify_persona platform-admin admin.platform@demo.ritcanteen.test 'RitDemo!Admin01' ADMIN ''

if [[ "${1:-}" == --smoke-only ]]; then
    printf 'Integration smoke passed: existing auth/profile HTTP and all eight demo personas. Full tests were not rerun.\n'
else
    printf 'Integration verification passed: clean build/tests and existing auth/profile HTTP plus all eight demo personas.\n'
fi

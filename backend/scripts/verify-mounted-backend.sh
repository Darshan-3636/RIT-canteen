#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."

fail() { printf 'Mounted backend smoke failed: %s\n' "$1" >&2; exit 1; }
for command in curl jq; do command -v "$command" >/dev/null || fail "missing $command"; done
: "${DATABASE_URL:?Set a private disposable app DATABASE_URL}"
: "${DATABASE_USER:?Set DATABASE_USER}"
: "${DATABASE_PASSWORD:?Set DATABASE_PASSWORD}"
[[ "${RIT_ENV:-}" == local && "${DEMO_SEED_ENABLED:-}" == true ]] || fail 'local demo seed mode required'
[[ "${RIT_INTEGRATION_DISPOSABLE_APP_DB:-}" == YES ]] || fail 'confirm disposable app DB'
[[ "${HTTP_HOST:-127.0.0.1}" == 127.0.0.1 ]] || fail 'loopback HTTP only'
[[ "$DATABASE_URL" =~ ^jdbc:postgresql://(127\.0\.0\.1|localhost):[0-9]+/rit_canteen_dev$ ]] || fail 'use loopback rit_canteen_dev only'

./gradlew installDist --no-daemon --max-workers=1 >/dev/null
base="http://127.0.0.1:${HTTP_PORT:-8080}"
if curl --silent --max-time 2 "$base/health" >/dev/null; then fail 'HTTP port already in use'; fi
server_log="$(mktemp)"
server_pid=''
stop_server() {
    if [[ -n "$server_pid" ]]; then kill "$server_pid" 2>/dev/null || true; wait "$server_pid" 2>/dev/null || true; server_pid=''; fi
}
cleanup() { stop_server; rm -f -- "$server_log"; }
trap cleanup EXIT
start_server() {
    "$PWD/build/install/rit-canteen-backend/bin/rit-canteen-backend" >"$server_log" 2>&1 &
    server_pid=$!
    local ready=false
    for _ in {1..40}; do
        if curl --silent --fail --max-time 2 "$base/ready" >/dev/null; then ready=true; break; fi
        if ! kill -0 "$server_pid" 2>/dev/null; then fail 'server exited before readiness'; fi
        sleep 1
    done
    [[ "$ready" == true ]] || fail 'server did not become ready'
}
status=''; body=''
request() {
    local method="$1" path="$2" payload="${3:-}" token="${4:-}" key="${5:-}"
    local args=(--silent --show-error --max-time 20 -X "$method" -w $'\n%{http_code}')
    [[ -z "$payload" ]] || args+=(-H 'Content-Type: application/json' --data "$payload")
    [[ -z "$token" ]] || args+=(-H "Authorization: Bearer $token")
    [[ -z "$key" ]] || args+=(-H "Idempotency-Key: $key")
    local response
    response="$(curl "${args[@]}" "$base$path")" || fail "HTTP $method $path"
    status="${response##*$'\n'}"; body="${response%$'\n'*}"
}
expect() { [[ "$status" == "$1" ]] || fail "$2: expected $1, received $status"; }
json() { jq -e "$1" >/dev/null <<<"$body" || fail "$2: unexpected JSON"; }
login() {
    local email="$1" password="$2"
    request POST /api/v1/auth/login "$(jq -nc --arg e "$email" --arg p "$password" '{email:$e,password:$p}')"
    expect 200 "login $email"; jq -r '.accessToken' <<<"$body"
}

start_server
request GET /health; expect 200 health; json '.status=="ok"' health
request GET /ready; expect 200 ready; json '.status=="ready"' ready
# A previous interrupted disposable smoke may have left this deliberate seller edit in place.
request GET /api/v1/sellers/main-canteen/menu/plain-dosa; expect 200 initial-plain-dosa
if jq -e '.availability=="UNAVAILABLE"' >/dev/null <<<"$body"; then
    recovery_owner="$(login owner.main@demo.ritcanteen.test 'RitDemo!OwnerMain01')"
    request PATCH /api/v1/sellers/main-canteen/menu/items/plain-dosa/availability '{"availability":"AVAILABLE"}' "$recovery_owner"
    expect 200 recover-disposable-catalog
fi
declare -A menus
request GET /api/v1/sellers; expect 200 sellers; json '(.sellers|length)==4' sellers
for seller in main-canteen north-counter coffee-corner campus-pantry; do
    request GET "/api/v1/sellers/$seller/menu"; expect 200 "$seller menu"
    json '(.items|length)==18' "$seller count"
    menus["$seller"]="$body"
done
catalog_rows=0
while IFS=$'\t' read -r item seller category name description paise kind dietary availability published visibility image; do
    [[ -n "$item" ]] || continue
    jq -e --arg id "$item" --arg seller "$seller" --arg category "$category" --arg name "$name" \
        --arg description "$description" --argjson paise "$paise" --arg kind "$kind" --arg dietary "$dietary" \
        --arg availability "$availability" --arg image "$image" \
        'any(.items[]; .id==$id and .sellerId==$seller and .categoryId==$category and .name==$name and
            .description==$description and .pricePaise==$paise and .kind==$kind and .dietary==$dietary and
            .availability==$availability and .published==true and .imageKey==$image)' \
        >/dev/null <<<"${menus[$seller]}" || fail "catalog mismatch for $item"
    catalog_rows=$((catalog_rows+1))
done <src/main/resources/demo/catalog-items-v2.tsv
[[ "$catalog_rows" == 72 ]] || fail "catalog manifest has $catalog_rows rows"
request GET '/api/v1/sellers/campus-pantry/menu?categoryId=drinks'; expect 200 pantry-drinks
json '([.items[].id]|index("pantry-juice")!=null and index("pantry-water")!=null)' pantry-drinks
request GET '/api/v1/sellers/north-counter/menu?query=samosa'; expect 200 search
json '([.items[].id]|index("veg-samosa")!=null)' search
request GET '/api/v1/sellers/coffee-corner/menu/bakery-paneer-puff'; expect 200 detail
json '.imageKey=="bakery-paneer-puff" and .pricePaise==4500' detail

customer="$(login student.arya@demo.ritcanteen.test 'RitDemo!Student01')"
other_customer="$(login student.kiran@demo.ritcanteen.test 'RitDemo!Student02')"
main_owner="$(login owner.main@demo.ritcanteen.test 'RitDemo!OwnerMain01')"
north_owner="$(login owner.secondary@demo.ritcanteen.test 'RitDemo!OwnerOther01')"
manager="$(login manager.main@demo.ritcanteen.test 'RitDemo!Manager01')"
staff="$(login staff.main@demo.ritcanteen.test 'RitDemo!Staff01')"
admin="$(login admin.platform@demo.ritcanteen.test 'RitDemo!Admin01')"
request POST /api/v1/auth/register '{broken-json'; expect 400 malformed-json
request POST /api/v1/auth/register '{"fullName":"Wrong role","email":"nobody@example.test","password":"Password12345","affiliation":"STUDENT","role":"OWNER"}'
expect 400 privileged-unknown-field

request PATCH /api/v1/sellers/main-canteen/menu/items/plain-dosa/availability '{"availability":"UNAVAILABLE"}' "$main_owner"
expect 200 owner-availability; json '.availability=="UNAVAILABLE"' owner-availability
request GET /api/v1/sellers/main-canteen/menu/plain-dosa; expect 200 customer-visible-edit
json '.availability=="UNAVAILABLE"' customer-visible-edit
request PATCH /api/v1/sellers/main-canteen/menu/items/plain-dosa/availability '{"availability":"AVAILABLE"}' "$staff"
expect 403 staff-edit-denied
request PATCH /api/v1/sellers/main-canteen/menu/items/plain-dosa/availability '{"availability":"AVAILABLE"}' "$north_owner"
expect 403 cross-seller-edit-denied

request POST /api/v1/me/individual-demo-orders '{"lines":[{"itemId":"masala-dosa","quantity":1},{"itemId":"idli","quantity":2}]}' "$customer"
expect 201 individual-demo; json '.demoOnly==true and .paymentVerified==false and .status=="DEMO_SAVED"' individual-demo
individual_id="$(jq -r '.id' <<<"$body")"
request GET /api/v1/me/individual-demo-orders '' "$other_customer"; expect 200 individual-privacy
jq -e --arg id "$individual_id" '([.orders[].id]|index($id)==null)' >/dev/null <<<"$body" || fail individual-privacy

pickup="$(date -u -d '+72 hours' '+%Y-%m-%dT%H:%M:%SZ')"
bulk_body="$(jq -nc --arg p "$pickup" '{sellerId:"north-counter",lines:[{itemId:"north-lunch",quantity:8}],pickupAt:$p}')"
request PUT /api/v1/sellers/north-counter/hours '{"hours":[]}' "$north_owner"; expect 204 reset-disposable-demo-hours
request POST /api/v1/bulk/requests "$bulk_body" "$customer" "lead-unknown-hours-$(date +%s)"
expect 422 hours-unknown
hours='{"hours":[{"dayOfWeek":1,"opensAt":"00:00","closesAt":"23:59"},{"dayOfWeek":2,"opensAt":"00:00","closesAt":"23:59"},{"dayOfWeek":3,"opensAt":"00:00","closesAt":"23:59"},{"dayOfWeek":4,"opensAt":"00:00","closesAt":"23:59"},{"dayOfWeek":5,"opensAt":"00:00","closesAt":"23:59"},{"dayOfWeek":6,"opensAt":"00:00","closesAt":"23:59"},{"dayOfWeek":7,"opensAt":"00:00","closesAt":"23:59"}]}'
request PUT /api/v1/sellers/north-counter/hours "$hours" "$north_owner"; expect 204 demo-hours
bulk_key="lead-valid-$(date +%s)-$$"
request POST /api/v1/bulk/requests "$bulk_body" "$customer" "$bulk_key"
expect 201 bulk-create; json '.status=="SUBMITTED" and .subtotalPaise==72000 and .version==1' bulk-create
bulk_id="$(jq -r '.id' <<<"$body")"
request POST /api/v1/bulk/requests "$bulk_body" "$customer" "$bulk_key"
expect 201 idempotent-replay
jq -e --arg id "$bulk_id" '.id==$id' >/dev/null <<<"$body" || fail idempotent-replay
request POST /api/v1/bulk/requests "$(jq -nc --arg p "$pickup" '{sellerId:"north-counter",lines:[{itemId:"north-lunch",quantity:9}],pickupAt:$p}')" "$customer" "$bulk_key"
expect 409 idempotency-payload-conflict
request GET "/api/v1/me/bulk/requests/$bulk_id" '' "$other_customer"; expect 404 customer-isolation
request GET "/api/v1/sellers/main-canteen/bulk/requests/$bulk_id" '' "$main_owner"; expect 404 seller-isolation
request GET "/api/v1/sellers/north-counter/bulk/requests/$bulk_id" '' "$north_owner"; expect 200 seller-inbox
request POST /api/v1/bulk/requests "$(jq -nc --arg p "$pickup" '{sellerId:"north-counter",lines:[{itemId:"north-lunch",quantity:7}],pickupAt:$p}')" "$customer" "lead-too-few-$(date +%s)-$$"
expect 422 minimum
request POST /api/v1/bulk/requests "$(jq -nc --arg p "$pickup" '{sellerId:"north-counter",lines:[{itemId:"masala-dosa",quantity:8}],pickupAt:$p}')" "$customer" "lead-wrong-seller-$(date +%s)-$$"
expect 422 mixed-seller
request POST "/api/v1/sellers/north-counter/bulk/requests/$bulk_id/counteroffers" "$(jq -nc --arg p "$pickup" '{expectedVersion:1,lines:[{itemId:"north-snack",quantity:8}],pickupAt:$p}')" "$north_owner"
expect 200 counteroffer; json '.version==2 and .status=="COUNTEROFFER"' counteroffer
request POST "/api/v1/bulk/requests/$bulk_id/customer-approval" '{"expectedVersion":1}' "$customer"; expect 409 stale-revision
request POST "/api/v1/sellers/north-counter/bulk/requests/$bulk_id/seller-approval" '{"expectedVersion":2}' "$staff"; expect 403 staff-approval
request POST "/api/v1/bulk/requests/$bulk_id/customer-approval" '{"expectedVersion":2}' "$customer"; expect 200 customer-approval
request POST "/api/v1/sellers/north-counter/bulk/requests/$bulk_id/seller-approval" '{"expectedVersion":2}' "$north_owner"
expect 200 seller-approval; json '.status=="AGREED_AWAITING_PAYMENT"' agreed
request PATCH /api/v1/sellers/north-counter/bulk-policy '{"specialPickupEnabled":true}' "$north_owner"
expect 200 special-policy
special_body="$(jq -nc --arg p "$pickup" '{sellerId:"north-counter",lines:[{itemId:"north-snack",quantity:8}],pickupAt:$p,specialPickupReason:"Explicit demo group request"}')"
request POST /api/v1/bulk/requests "$special_body" "$customer" "lead-special-$(date +%s)-$$"
expect 201 special-create; special_id="$(jq -r '.id' <<<"$body")"
request POST "/api/v1/bulk/requests/$special_id/customer-approval" '{"expectedVersion":1}' "$customer"; expect 200 special-customer-approval
request POST "/api/v1/sellers/north-counter/bulk/requests/$special_id/seller-approval" '{"expectedVersion":1}' "$north_owner"; expect 200 special-seller-approval
request POST "/api/v1/sellers/north-counter/bulk/requests/$special_id/special-pickup-decision" '{"expectedVersion":1,"approved":true}' "$north_owner"
expect 200 special-decision; json '.status=="AGREED_AWAITING_PAYMENT" and .specialPickupApproved==true' special-decision

request PUT /api/v1/me/favorites/item/pantry-juice '' "$customer"; expect 200 favorite
json '.sellerId=="campus-pantry"' favorite
request POST /api/v1/me/bulk-templates "$(jq -nc --arg name "Demo club lunch $(date +%s)-$$" '{sellerId:"north-counter",name:$name,lines:[{itemId:"north-lunch",quantity:8}]}')" "$customer"
expect 201 template; template_id="$(jq -r '.id' <<<"$body")"
request POST /api/v1/me/support/tickets "$(jq -nc --arg ref "$bulk_id" --arg key "lead-ticket-$(date +%s)-$$" '{category:"FOOD",subject:"Demo food concern",message:"Demo ticket message",sellerId:"north-counter",requestReference:$ref,clientRequestId:$key}')" "$customer"
expect 201 ticket; ticket_id="$(jq -r '.ticket.id' <<<"$body")"
request GET "/api/v1/sellers/main-canteen/support/tickets/$ticket_id" '' "$main_owner"; expect 404 cross-seller-ticket
request POST "/api/v1/sellers/north-counter/support/tickets/$ticket_id/messages" '{"body":"Seller demo response"}' "$north_owner"
expect 201 seller-reply
request GET /api/v1/me/notifications '' "$customer"; expect 200 in-app-inbox
json 'any(.items[]; .eventType=="SUPPORT_REPLY" and .deliveryChannel=="IN_APP_ONLY")' in-app-inbox
request GET "/api/v1/admin/support/tickets/$ticket_id" '' "$admin"; expect 200 admin-ticket
request PATCH "/api/v1/admin/support/tickets/$ticket_id" '{"status":"RESOLVED"}' "$admin"
expect 200 admin-ticket-resolved
request GET /api/v1/admin/overview '' "$customer"; expect 403 non-admin
request GET /api/v1/admin/overview '' "$admin"; expect 200 admin-overview
request GET /api/v1/admin/users '' "$admin"; expect 200 admin-users
request GET "/api/v1/sellers/north-counter/dashboard-summary" '' "$north_owner"; expect 200 seller-analytics
json '.demoRequestCounts.total >= 1 and .verifiedRevenuePaise==null and .demoRequestCountsStatus=="DEMO_UNVERIFIED"' seller-analytics
request GET /api/v1/admin/analytics/summary '' "$admin"; expect 200 admin-analytics
json '.demoRequestCounts.total >= 1 and .verifiedRevenuePaise==null' admin-analytics
request GET '/api/v1/sellers/north-counter/reports/export' '' "$north_owner"; expect 501 unsupported-export
request POST /api/v1/checkout/payment '{"success":true}' "$customer"; expect 404 no-payment-bypass

stop_server
start_server
request GET /api/v1/auth/me '' "$customer"; expect 200 persisted-session
request GET "/api/v1/me/bulk/requests/$bulk_id" '' "$customer"; expect 200 persisted-quote
json '.status=="AGREED_AWAITING_PAYMENT" and .version==2' persisted-quote
request GET /api/v1/me/individual-demo-orders '' "$customer"; expect 200 persisted-individual
jq -e --arg id "$individual_id" 'any(.orders[]; .id==$id)' >/dev/null <<<"$body" || fail persisted-individual
request GET /api/v1/me/bulk-templates '' "$customer"; expect 200 persisted-template
jq -e --arg id "$template_id" 'any(.items[]; .id==$id)' >/dev/null <<<"$body" || fail persisted-template
request GET "/api/v1/me/support/tickets/$ticket_id" '' "$customer"; expect 200 persisted-ticket
request GET /api/v1/sellers/campus-pantry/menu '' ''; expect 200 persisted-catalog
json '(.items|length)==18' persisted-catalog
request GET /api/v1/sellers/main-canteen/menu/plain-dosa; expect 200 persisted-seller-edit
json '.availability=="UNAVAILABLE"' persisted-seller-edit
request PATCH /api/v1/sellers/main-canteen/menu/items/plain-dosa/availability '{"availability":"AVAILABLE"}' "$main_owner"
expect 200 owner-restore

printf 'Mounted backend smoke passed: 72 catalog rows, role/tenant checks, quote, engagement/admin, honest unsupported routes and restart persistence.\n'

#!/bin/bash
# TEST 9: End-to-end order THROUGH the broker (the real 2PC coordinator)
# Unlike tests 2-4 (which drive the suppliers directly), this places an order via the broker's
# own POST /orders endpoint, so the broker's AtomicOrderService runs the reserve->confirm 2PC
# across the ticket + food + drink suppliers. The broker form is CSRF-protected, so we first
# GET a concert page to obtain a session cookie + _csrf token, then POST with both.
#
# Side effect: on success this consumes 1 unit each of T-001, F-001, D-001 (a real, confirmed sale).
# Run from: anywhere with network access to the broker (and the broker to its suppliers).

source "$(dirname "$0")/config.sh"

echo "========================================"
echo " TEST 9: End-to-end order through the broker"
echo "========================================"

COOKIES=$(mktemp)
FORM=$(mktemp)
trap 'rm -f "$COOKIES" "$FORM"' EXIT

info "Fetching a concert page for a session + CSRF token..."
GET_STATUS=$(curl -s -o "$FORM" -w "%{http_code}" -c "$COOKIES" "$BROKER/concerts/T-001")
if [ "$GET_STATUS" != "200" ]; then
    fail "Could not load /concerts/T-001 (HTTP $GET_STATUS) — is the broker (and ticket supplier) up?"
    exit 1
fi

CSRF=$(python3 -c "
import re
html = open('$FORM').read()
m = re.search(r'name=\"_csrf\"[^>]*value=\"([^\"]+)\"', html) or re.search(r'value=\"([^\"]+)\"[^>]*name=\"_csrf\"', html)
print(m.group(1) if m else '')
")
if [ -z "$CSRF" ]; then
    fail "No CSRF token found on the concert page — cannot POST the order"
    exit 1
fi
pass "Got session + CSRF token"

echo ""
info "Placing a cross-supplier order (1 ticket + 1 food + 1 drink) via POST /orders..."
HEADERS=$(curl -s -D - -o /dev/null -b "$COOKIES" -c "$COOKIES" \
    -X POST "$BROKER/orders" \
    --data-urlencode "_csrf=$CSRF" \
    --data-urlencode "ticketProductId=T-001" \
    --data-urlencode "ticketQty=1" \
    --data-urlencode "foodProductId=F-001" \
    --data-urlencode "foodQty=1" \
    --data-urlencode "drinkProductId=D-001" \
    --data-urlencode "drinkQty=1" \
    --data-urlencode "deliveryAddress=Diestsestraat 1, Leuven" \
    --data-urlencode "cardholderName=Alice Smith" \
    --data-urlencode "cardLast4=4242")

POST_CODE=$(echo "$HEADERS" | grep -iE '^HTTP/' | tail -1 | awk '{print $2}')
LOCATION=$(echo "$HEADERS" | grep -i '^location:' | tail -1 | tr -d '\r' | awk '{print $2}')

if [ "$POST_CODE" != "302" ] && [ "$POST_CODE" != "303" ]; then
    fail "Expected a redirect after placing the order, got HTTP $POST_CODE (CSRF rejected -> 403?)"
    exit 1
fi
if [ -z "$LOCATION" ]; then
    fail "No redirect Location header — cannot find the placed order"
    exit 1
fi
pass "Order placed → redirected to $LOCATION"

# Location may be absolute (http://host/orders/id) or relative (/orders/id)
case "$LOCATION" in
    http*) ORDER_URL="$LOCATION" ;;
    *)     ORDER_URL="$BROKER$LOCATION" ;;
esac

echo ""
info "Fetching the order page to read its final 2PC status..."
ORDER_PAGE=$(curl -s -b "$COOKIES" "$ORDER_URL")

if echo "$ORDER_PAGE" | grep -q "SUCCEEDED"; then
    pass "Order reached SUCCEEDED — broker 2PC committed across all suppliers"
else
    STATUS_SHOWN=$(echo "$ORDER_PAGE" | grep -oE 'CREATED|RESERVING|RESERVED|CONFIRMING|SUCCEEDED|FAILED' | head -1)
    fail "Order did not succeed (status: ${STATUS_SHOWN:-unknown})"
fi

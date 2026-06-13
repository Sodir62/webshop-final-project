#!/bin/bash
# TEST 1: Connectivity
# Checks all 3 services are reachable and responding.
# The broker sits behind Auth0 (returns 302 redirect) — that is counted as UP.
# Supplier endpoints require a Bearer token; this script fetches one from Auth0.
# Run from: anywhere with network access to the VMs.

source "$(dirname "$0")/config.sh"

echo "========================================"
echo " TEST 1: Connectivity Check"
echo "========================================"

require_token

echo ""
# Broker redirects unauthenticated requests to Auth0 login (302). That proves it is
# running and the security layer is active. HTTP 200 is also accepted in case a test
# session cookie is already present in a browser-driven run.
BROKER_STATUS=$(curl -s -o /dev/null -w "%{http_code}" --connect-timeout 5 "$BROKER")
if [ "$BROKER_STATUS" = "200" ] || [ "$BROKER_STATUS" = "302" ]; then
    pass "Broker is UP ($BROKER) → HTTP $BROKER_STATUS (302 = Auth0 gate active)"
else
    fail "Broker is DOWN or unreachable ($BROKER) → HTTP $BROKER_STATUS"
fi

TICKET_STATUS=$(curl -s -o /dev/null -w "%{http_code}" --connect-timeout 5 \
    -H "Authorization: Bearer $TOKEN" "$TICKET/products")
if [ "$TICKET_STATUS" = "200" ]; then
    pass "Ticket Supplier is UP ($TICKET) → HTTP $TICKET_STATUS"
else
    fail "Ticket Supplier DOWN or rejected token ($TICKET) → HTTP $TICKET_STATUS"
fi

FOOD_STATUS=$(curl -s -o /dev/null -w "%{http_code}" --connect-timeout 5 \
    -H "Authorization: Bearer $TOKEN" "$FOOD/products")
if [ "$FOOD_STATUS" = "200" ]; then
    pass "Food/Drink Supplier is UP ($FOOD) → HTTP $FOOD_STATUS"
else
    fail "Food/Drink Supplier DOWN or rejected token ($FOOD) → HTTP $FOOD_STATUS"
fi

echo ""
info "Ticket catalog:"
curl -s -H "Authorization: Bearer $TOKEN" "$TICKET/products" | python3 -m json.tool 2>/dev/null

echo ""
info "Food & Drink catalog:"
curl -s -H "Authorization: Bearer $TOKEN" "$FOOD/products" | python3 -m json.tool 2>/dev/null

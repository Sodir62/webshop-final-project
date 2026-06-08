#!/bin/bash
# TEST 3: Out of Stock
# Metallica (T-002) only has 2 tickets. Reserve them all, then try again.
# Expected: first reservation succeeds, second returns 409 CONFLICT.
# Run from: anywhere with network access to the supplier VMs.

source "$(dirname "$0")/config.sh"

echo "========================================"
echo " TEST 3: Out of Stock"
echo "========================================"

info "Checking current Metallica (T-002) stock..."
curl -s "$TICKET/products" | python3 -c "
import sys, json
products = json.load(sys.stdin)
for p in products:
    if p['id'] == 'T-002':
        print(f\"  T-002: {p['name']} — stock: {p['stock']}\")
" 2>/dev/null

echo ""
info "Reserving 2 tickets (all available stock)..."
RES1=$(curl -s -X POST "$TICKET/reservations" \
    -H "Content-Type: application/json" \
    -d '{"productId":"T-002","quantity":2}')
RES1_ID=$(echo "$RES1" | grep -o '"reservationId":"[^"]*"' | cut -d'"' -f4)

if [ -n "$RES1_ID" ]; then
    pass "First reservation succeeded → $RES1_ID (stock now 0)"
else
    fail "First reservation failed: $RES1"
    exit 1
fi

echo ""
info "Trying to reserve 1 more ticket (should fail — out of stock)..."
STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$TICKET/reservations" \
    -H "Content-Type: application/json" \
    -d '{"productId":"T-002","quantity":1}')

if [ "$STATUS" = "409" ]; then
    pass "Correctly rejected with HTTP 409 CONFLICT — out of stock"
else
    fail "Expected 409, got HTTP $STATUS"
fi

echo ""
info "Cleaning up — cancelling reservation $RES1_ID (restoring stock)..."
curl -s -X DELETE "$TICKET/reservations/$RES1_ID"
pass "Stock restored"

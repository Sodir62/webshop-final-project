#!/bin/bash
# TEST 3: Out of Stock
# Metallica (T-002) only has 2 tickets. Reserve them all, then try again.
# Expected: first reservation succeeds, second returns 409 CONFLICT.
# Run from: anywhere with network access to the supplier VMs.

source "$(dirname "$0")/config.sh"

echo "========================================"
echo " TEST 3: Out of Stock"
echo "========================================"

require_token

echo ""
info "Reading current Metallica (T-002) stock..."
T2_STOCK=$(curl -s -H "Authorization: Bearer $TOKEN" "$TICKET/products" | python3 -c "
import sys,json
for p in json.load(sys.stdin):
    if p['id']=='T-002': print(p['stock'])
" 2>/dev/null)
info "T-002 stock is: '$T2_STOCK'"

if [ -n "$T2_STOCK" ] && [ "$T2_STOCK" -gt 0 ] 2>/dev/null; then
    # Stock still available — exhaust it first, then verify rejection
    info "Stock is $T2_STOCK — reserving all to exhaust it..."
    RES1=$(curl -s -X POST "$TICKET/reservations" \
        -H "Authorization: Bearer $TOKEN" \
        -H "Content-Type: application/json" \
        -d "{\"productId\":\"T-002\",\"quantity\":$T2_STOCK}")
    RES1_ID=$(echo "$RES1" | python3 -c "import sys,json; print(json.load(sys.stdin).get('reservationId',''))" 2>/dev/null)
    if [ -n "$RES1_ID" ]; then
        pass "Exhausted stock → $RES1_ID (stock now 0)"
    else
        fail "Could not exhaust stock: $RES1"; exit 1
    fi
else
    info "T-002 already sold out — skipping exhaust step, testing rejection directly"
    RES1_ID=""
fi

echo ""
info "Trying to reserve 1 Metallica ticket (should fail — out of stock)..."
STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$TICKET/reservations" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d '{"productId":"T-002","quantity":1}')

if [ "$STATUS" = "409" ]; then
    pass "Correctly rejected with HTTP 409 CONFLICT — out of stock"
else
    fail "Expected 409, got HTTP $STATUS"
fi

if [ -n "$RES1_ID" ]; then
    echo ""
    info "Cleaning up — cancelling reservation $RES1_ID (restoring stock)..."
    CLEAN=$(curl -s -o /dev/null -w "%{http_code}" -X DELETE \
        -H "Authorization: Bearer $TOKEN" "$TICKET/reservations/$RES1_ID")
    [ "$CLEAN" = "204" ] && pass "Stock restored" || fail "Could not restore stock (HTTP $CLEAN)"
fi

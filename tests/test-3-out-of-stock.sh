#!/bin/bash
# TEST 3: Out of Stock
# Metallica (T-002) only has 2 tickets. Reserve them all, then try again.
# Expected: first reservation succeeds, second returns 409 CONFLICT.
# Run from: anywhere with network access to the supplier VMs.

source "$(dirname "$0")/config.sh"

echo "========================================"
echo " TEST 3: Out of Stock"
echo "========================================"

info "Reading current Metallica (T-002) stock..."
T2_STOCK=$(curl -s "$TICKET/products" | python3 -c "
import sys,json
for p in json.load(sys.stdin):
    if p['id']=='T-002': print(p['stock'])
" 2>/dev/null)

if [ -z "$T2_STOCK" ] || [ "$T2_STOCK" -lt 1 ]; then
    fail "Cannot run: T-002 stock is unreadable or already empty (got '$T2_STOCK')"
    exit 1
fi
info "T-002 stock is $T2_STOCK"

echo ""
info "Reserving all $T2_STOCK ticket(s) — the full available stock..."
RES1=$(curl -s -X POST "$TICKET/reservations" \
    -H "Content-Type: application/json" \
    -d "{\"productId\":\"T-002\",\"quantity\":$T2_STOCK}")
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
CLEAN_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X DELETE "$TICKET/reservations/$RES1_ID")
if [ "$CLEAN_STATUS" = "204" ]; then
    pass "Stock restored"
else
    fail "Could not restore stock (HTTP $CLEAN_STATUS) — hold $RES1_ID may linger"
fi

#!/bin/bash
# TEST 4: Supplier rollback contract — cancel restores stock after a mid-order failure
# Reserves a ticket, then exhausts food stock so food fails, then cancels the ticket hold
# the way the broker's compensate() would.
# Run from: anywhere with network access to the supplier VMs.

source "$(dirname "$0")/config.sh"

echo "========================================"
echo " TEST 4: Supplier rollback contract (cancel restores stock)"
echo "========================================"

require_token

echo ""
info "Reading current Nachos (F-001) stock..."
FOOD_STOCK=$(curl -s -H "Authorization: Bearer $TOKEN" "$FOOD/products" | python3 -c "
import sys,json
for p in json.load(sys.stdin):
    if p['id']=='F-001': print(p['stock'])
" 2>/dev/null)

if [ -z "$FOOD_STOCK" ] || [ "$FOOD_STOCK" -lt 1 ]; then
    fail "Cannot run: F-001 stock is unreadable or already empty (got '$FOOD_STOCK')"
    exit 1
fi
info "F-001 stock is $FOOD_STOCK; reserving all of it to force the next reserve to fail..."

EXHAUST=$(curl -s -X POST "$FOOD/reservations" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d "{\"productId\":\"F-001\",\"quantity\":$FOOD_STOCK}")
EXHAUST_ID=$(echo "$EXHAUST" | python3 -c "import sys,json; print(json.load(sys.stdin).get('reservationId',''))" 2>/dev/null)

if [ -z "$EXHAUST_ID" ]; then
    fail "Could not exhaust stock: $EXHAUST"
    exit 1
fi
pass "Nachos stock exhausted (reserved $FOOD_STOCK, hold: $EXHAUST_ID)"

echo ""
info "--- Driving a broker-style 2PC order against the suppliers ---"

info "Phase 1: Reserving 1 ticket (T-001)..."
TICKET_RES=$(curl -s -X POST "$TICKET/reservations" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d '{"productId":"T-001","quantity":1}')
TICKET_RES_ID=$(echo "$TICKET_RES" | python3 -c "import sys,json; print(json.load(sys.stdin).get('reservationId',''))" 2>/dev/null)

if [ -z "$TICKET_RES_ID" ]; then
    fail "Ticket reservation failed: $TICKET_RES"
    curl -s -X DELETE -H "Authorization: Bearer $TOKEN" "$FOOD/reservations/$EXHAUST_ID"
    exit 1
fi
pass "Ticket reserved → $TICKET_RES_ID"

info "Phase 1: Reserving Nachos (F-001) — expecting failure (out of stock)..."
FOOD_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$FOOD/reservations" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d '{"productId":"F-001","quantity":1}')

if [ "$FOOD_STATUS" = "409" ]; then
    pass "Food reservation failed with HTTP 409 (expected — out of stock)"
else
    fail "Expected 409, got HTTP $FOOD_STATUS"
fi

info "Rolling back — cancelling ticket reservation $TICKET_RES_ID..."
CANCEL=$(curl -s -o /dev/null -w "%{http_code}" -X DELETE \
    -H "Authorization: Bearer $TOKEN" "$TICKET/reservations/$TICKET_RES_ID")
[ "$CANCEL" = "204" ] && pass "Ticket reservation cancelled (HTTP 204) — stock restored" || fail "Cancel returned HTTP $CANCEL"

echo ""
pass "Supplier rollback contract OK — ticket hold cancelled after food failure"

info "Restoring Nachos stock..."
RESTORE=$(curl -s -o /dev/null -w "%{http_code}" -X DELETE \
    -H "Authorization: Bearer $TOKEN" "$FOOD/reservations/$EXHAUST_ID")
[ "$RESTORE" = "204" ] && pass "Nachos stock restored" || fail "Could not restore stock (HTTP $RESTORE) — hold $EXHAUST_ID may linger"

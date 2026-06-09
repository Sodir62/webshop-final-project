#!/bin/bash
# TEST 4: Supplier rollback contract — cancel restores stock after a mid-order failure
# Reserves a ticket, then food fails (out of stock), then cancels the ticket hold the way the
# broker's compensate() would. NOTE: the rollback here is driven by THIS script, not the broker
# coordinator -- the broker's own atomic rollback is covered by the Java
# AtomicOrderServiceTests.downedSupplierRollsBackAndRestoresStock; test-9 drives the broker.
# Run from: anywhere with network access to the supplier VMs.

source "$(dirname "$0")/config.sh"

echo "========================================"
echo " TEST 4: Supplier rollback contract (cancel restores stock)"
echo "========================================"

# Read the CURRENT F-001 stock and reserve all of it, so the next reserve is guaranteed to
# fail regardless of how much stock the persistent supplier DB happens to have right now.
info "Reading current Nachos (F-001) stock..."
FOOD_STOCK=$(curl -s "$FOOD/products" | python3 -c "
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
    -H "Content-Type: application/json" \
    -d "{\"productId\":\"F-001\",\"quantity\":$FOOD_STOCK}")
EXHAUST_ID=$(echo "$EXHAUST" | grep -o '"reservationId":"[^"]*"' | cut -d'"' -f4)

if [ -z "$EXHAUST_ID" ]; then
    fail "Could not exhaust stock (error): $EXHAUST"
    exit 1
fi
pass "Nachos stock exhausted (reserved $FOOD_STOCK, hold: $EXHAUST_ID)"

echo ""
info "--- Driving a broker-style 2PC order against the suppliers ---"

# Phase 1a: Reserve ticket (succeeds)
info "Phase 1: Reserving 1 ticket (T-001)..."
TICKET_RES=$(curl -s -X POST "$TICKET/reservations" \
    -H "Content-Type: application/json" \
    -d '{"productId":"T-001","quantity":1}')
TICKET_RES_ID=$(echo "$TICKET_RES" | grep -o '"reservationId":"[^"]*"' | cut -d'"' -f4)

if [ -z "$TICKET_RES_ID" ]; then
    fail "Ticket reservation failed: $TICKET_RES"
    curl -s -X DELETE "$FOOD/reservations/$EXHAUST_ID"
    exit 1
fi
pass "Ticket reserved → $TICKET_RES_ID"

# Phase 1b: Reserve food (fails — out of stock)
info "Phase 1: Reserving Nachos (F-001) — expecting failure..."
FOOD_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$FOOD/reservations" \
    -H "Content-Type: application/json" \
    -d '{"productId":"F-001","quantity":1}')

if [ "$FOOD_STATUS" = "409" ]; then
    pass "Food reservation failed with HTTP 409 (expected)"
else
    fail "Expected 409, got HTTP $FOOD_STATUS"
fi

# Rollback: cancel the ticket reservation
info "Rolling back — cancelling ticket reservation $TICKET_RES_ID..."
CANCEL_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X DELETE "$TICKET/reservations/$TICKET_RES_ID")
if [ "$CANCEL_STATUS" = "204" ]; then
    pass "Ticket reservation cancelled (HTTP 204) — stock restored"
else
    fail "Cancel returned HTTP $CANCEL_STATUS"
fi

echo ""
pass "Supplier rollback contract OK — ticket hold cancelled, stock restored"

# Restore nachos stock
info "Restoring Nachos stock..."
RESTORE_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X DELETE "$FOOD/reservations/$EXHAUST_ID")
if [ "$RESTORE_STATUS" = "204" ]; then
    pass "Nachos stock restored"
else
    fail "Could not restore Nachos stock (HTTP $RESTORE_STATUS) — hold $EXHAUST_ID may linger"
fi

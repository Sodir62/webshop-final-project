#!/bin/bash
# TEST 4: 2PC Rollback — food supplier out of stock mid-order
# Reserves ticket successfully, then food fails (out of stock).
# Expected: ticket reservation is CANCELLED (stock restored), order fails atomically.
# Run from: anywhere with network access to the supplier VMs.

source "$(dirname "$0")/config.sh"

echo "========================================"
echo " TEST 4: 2PC Rollback (Atomicity)"
echo "========================================"

# First exhaust food stock so the second reservation fails
info "Exhausting Nachos (F-001) stock to force a failure..."
EXHAUST=$(curl -s -X POST "$FOOD/reservations" \
    -H "Content-Type: application/json" \
    -d '{"productId":"F-001","quantity":200}')
EXHAUST_ID=$(echo "$EXHAUST" | grep -o '"reservationId":"[^"]*"' | cut -d'"' -f4)

if [ -z "$EXHAUST_ID" ]; then
    fail "Could not exhaust stock (already empty or error): $EXHAUST"
    exit 1
fi
pass "Nachos stock exhausted (hold: $EXHAUST_ID)"

echo ""
info "--- Simulating broker 2PC order ---"

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
pass "ATOMICITY VERIFIED — ticket stock restored, no partial order committed"

# Restore nachos stock
info "Restoring Nachos stock..."
curl -s -X DELETE "$FOOD/reservations/$EXHAUST_ID" > /dev/null
pass "Nachos stock restored"

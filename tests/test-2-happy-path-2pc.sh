#!/bin/bash
# TEST 2: Happy Path — Full 2PC (reserve → confirm on both suppliers)
# Simulates exactly what the broker does when an order succeeds.
# Run from: anywhere with network access to the supplier VMs.

source "$(dirname "$0")/config.sh"

echo "========================================"
echo " TEST 2: Happy Path — Full 2PC"
echo "========================================"

# --- PHASE 1: RESERVE ---
info "Phase 1: Reserving 1 ticket (T-001: Coldplay)..."
TICKET_RES=$(curl -s -X POST "$TICKET/reservations" \
    -H "Content-Type: application/json" \
    -d '{"productId":"T-001","quantity":1}')
echo "$TICKET_RES"
TICKET_RES_ID=$(echo "$TICKET_RES" | grep -o '"reservationId":"[^"]*"' | cut -d'"' -f4)

if [ -z "$TICKET_RES_ID" ]; then
    fail "Ticket reservation failed — aborting"
    exit 1
fi
pass "Ticket reserved → reservationId: $TICKET_RES_ID"

info "Phase 1: Reserving 1 Nachos (F-001)..."
FOOD_RES=$(curl -s -X POST "$FOOD/reservations" \
    -H "Content-Type: application/json" \
    -d '{"productId":"F-001","quantity":1}')
echo "$FOOD_RES"
FOOD_RES_ID=$(echo "$FOOD_RES" | grep -o '"reservationId":"[^"]*"' | cut -d'"' -f4)

if [ -z "$FOOD_RES_ID" ]; then
    fail "Food reservation failed — cancelling ticket reservation..."
    curl -s -X DELETE "$TICKET/reservations/$TICKET_RES_ID"
    info "Ticket reservation $TICKET_RES_ID cancelled (rollback)"
    exit 1
fi
pass "Food reserved → reservationId: $FOOD_RES_ID"

echo ""
info "Both holds placed. Commit decision point reached."

# --- PHASE 2: CONFIRM ---
info "Phase 2: Confirming ticket reservation..."
STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$TICKET/reservations/$TICKET_RES_ID/confirm")
if [ "$STATUS" = "200" ]; then
    pass "Ticket confirmed (HTTP $STATUS)"
else
    fail "Ticket confirm failed (HTTP $STATUS)"
fi

info "Phase 2: Confirming food reservation..."
STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$FOOD/reservations/$FOOD_RES_ID/confirm")
if [ "$STATUS" = "200" ]; then
    pass "Food confirmed (HTTP $STATUS)"
else
    fail "Food confirm failed (HTTP $STATUS)"
fi

echo ""
pass "ORDER SUCCEEDED — 2PC completed successfully"
echo "  Ticket reservationId : $TICKET_RES_ID"
echo "  Food reservationId   : $FOOD_RES_ID"

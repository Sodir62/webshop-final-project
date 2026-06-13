#!/bin/bash
# TEST 2: Supplier reserve→confirm contract (the 2PC commit primitives)
# Drives the supplier endpoints directly with a Bearer token, the way the broker's
# AtomicOrderService does on a successful order.
# Run from: anywhere with network access to the supplier VMs.

source "$(dirname "$0")/config.sh"

echo "========================================"
echo " TEST 2: Supplier reserve→confirm contract"
echo "========================================"

require_token

echo ""
# --- PHASE 1: RESERVE ---
info "Phase 1: Reserving 1 ticket (T-001: Coldplay)..."
TICKET_RES=$(curl -s -X POST "$TICKET/reservations" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d '{"productId":"T-001","quantity":1}')
echo "$TICKET_RES"
TICKET_RES_ID=$(echo "$TICKET_RES" | python3 -c "import sys,json; print(json.load(sys.stdin).get('reservationId',''))" 2>/dev/null)

if [ -z "$TICKET_RES_ID" ]; then
    fail "Ticket reservation failed — aborting"
    exit 1
fi
pass "Ticket reserved → reservationId: $TICKET_RES_ID"

info "Phase 1: Reserving 1 Nachos (F-001)..."
FOOD_RES=$(curl -s -X POST "$FOOD/reservations" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d '{"productId":"F-001","quantity":1}')
echo "$FOOD_RES"
FOOD_RES_ID=$(echo "$FOOD_RES" | python3 -c "import sys,json; print(json.load(sys.stdin).get('reservationId',''))" 2>/dev/null)

if [ -z "$FOOD_RES_ID" ]; then
    fail "Food reservation failed — cancelling ticket reservation..."
    curl -s -X DELETE -H "Authorization: Bearer $TOKEN" "$TICKET/reservations/$TICKET_RES_ID"
    info "Ticket reservation $TICKET_RES_ID cancelled (rollback)"
    exit 1
fi
pass "Food reserved → reservationId: $FOOD_RES_ID"

echo ""
info "Both holds placed. Commit decision point reached."

# --- PHASE 2: CONFIRM ---
info "Phase 2: Confirming ticket reservation..."
STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST \
    -H "Authorization: Bearer $TOKEN" "$TICKET/reservations/$TICKET_RES_ID/confirm")
[ "$STATUS" = "200" ] && pass "Ticket confirmed (HTTP $STATUS)" || fail "Ticket confirm failed (HTTP $STATUS)"

info "Phase 2: Confirming food reservation..."
STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST \
    -H "Authorization: Bearer $TOKEN" "$FOOD/reservations/$FOOD_RES_ID/confirm")
[ "$STATUS" = "200" ] && pass "Food confirmed (HTTP $STATUS)" || fail "Food confirm failed (HTTP $STATUS)"

echo ""
pass "Supplier reserve→confirm contract OK — both holds confirmed"
echo "  Ticket reservationId : $TICKET_RES_ID"
echo "  Food reservationId   : $FOOD_RES_ID"

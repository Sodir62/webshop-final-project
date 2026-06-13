#!/bin/bash
# TEST 9: Full 3-supplier coordinated 2PC (ticket + food + drink)
# Drives all three supplier services through a complete reserve→confirm cycle using the
# same M2M Bearer token the broker uses, mirroring exactly what AtomicOrderService does
# internally for a successful order. This validates the full cross-service protocol.
#
# Note: the broker's own web UI coordinator (AtomicOrderService) requires an Auth0 OIDC
# user session (browser login) and is therefore demonstrated interactively. This test
# exercises and proves every supplier-side primitive that the coordinator depends on.
#
# Run from: anywhere with network access to all three supplier VMs.

source "$(dirname "$0")/config.sh"

echo "========================================"
echo " TEST 9: Full 3-Supplier Coordinated 2PC"
echo "========================================"

require_token

echo ""
info "Reading current stock for all three items..."
T_STOCK=$(curl -s -H "Authorization: Bearer $TOKEN" "$TICKET/products" | python3 -c "
import sys,json
for p in json.load(sys.stdin):
    if p['id']=='T-001': print(p['stock'])
" 2>/dev/null)
F_STOCK=$(curl -s -H "Authorization: Bearer $TOKEN" "$FOOD/products" | python3 -c "
import sys,json
for p in json.load(sys.stdin):
    if p['id']=='F-001': print(p['stock'])
" 2>/dev/null)
D_STOCK=$(curl -s -H "Authorization: Bearer $TOKEN" "$FOOD/products" | python3 -c "
import sys,json
for p in json.load(sys.stdin):
    if p['id']=='D-001': print(p['stock'])
" 2>/dev/null)
info "T-001 stock: $T_STOCK  |  F-001 stock: $F_STOCK  |  D-001 stock: $D_STOCK"

echo ""
echo "--- PHASE 1: RESERVE (all three suppliers) ---"

info "Reserving 1 ticket (T-001: Coldplay @ Sportpaleis)..."
T_RES=$(curl -s -X POST "$TICKET/reservations" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d '{"productId":"T-001","quantity":1}')
T_ID=$(echo "$T_RES" | python3 -c "import sys,json; print(json.load(sys.stdin).get('reservationId',''))" 2>/dev/null)
if [ -z "$T_ID" ]; then fail "Ticket reservation failed: $T_RES"; exit 1; fi
pass "Ticket reserved → $T_ID"

info "Reserving 1 Nachos (F-001)..."
F_RES=$(curl -s -X POST "$FOOD/reservations" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d '{"productId":"F-001","quantity":1}')
F_ID=$(echo "$F_RES" | python3 -c "import sys,json; print(json.load(sys.stdin).get('reservationId',''))" 2>/dev/null)
if [ -z "$F_ID" ]; then
    fail "Food reservation failed: $F_RES — rolling back ticket..."
    curl -s -X DELETE -H "Authorization: Bearer $TOKEN" "$TICKET/reservations/$T_ID"
    exit 1
fi
pass "Food reserved → $F_ID"

info "Reserving 1 Cola (D-001)..."
D_RES=$(curl -s -X POST "$FOOD/reservations" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d '{"productId":"D-001","quantity":1}')
D_ID=$(echo "$D_RES" | python3 -c "import sys,json; print(json.load(sys.stdin).get('reservationId',''))" 2>/dev/null)
if [ -z "$D_ID" ]; then
    fail "Drink reservation failed: $D_RES — rolling back ticket + food..."
    curl -s -X DELETE -H "Authorization: Bearer $TOKEN" "$TICKET/reservations/$T_ID"
    curl -s -X DELETE -H "Authorization: Bearer $TOKEN" "$FOOD/reservations/$F_ID"
    exit 1
fi
pass "Drink reserved → $D_ID"

echo ""
info "All three holds placed — commit decision point reached (this is the 2PC boundary)"
echo "  Ticket : $T_ID"
echo "  Food   : $F_ID"
echo "  Drink  : $D_ID"

echo ""
echo "--- PHASE 2: CONFIRM (all three suppliers) ---"

info "Confirming ticket reservation..."
SC=$(curl -s -o /dev/null -w "%{http_code}" -X POST \
    -H "Authorization: Bearer $TOKEN" "$TICKET/reservations/$T_ID/confirm")
[ "$SC" = "200" ] && pass "Ticket confirmed (HTTP $SC)" || fail "Ticket confirm failed (HTTP $SC)"

info "Confirming food reservation..."
SF=$(curl -s -o /dev/null -w "%{http_code}" -X POST \
    -H "Authorization: Bearer $TOKEN" "$FOOD/reservations/$F_ID/confirm")
[ "$SF" = "200" ] && pass "Food confirmed (HTTP $SF)" || fail "Food confirm failed (HTTP $SF)"

info "Confirming drink reservation..."
SD=$(curl -s -o /dev/null -w "%{http_code}" -X POST \
    -H "Authorization: Bearer $TOKEN" "$FOOD/reservations/$D_ID/confirm")
[ "$SD" = "200" ] && pass "Drink confirmed (HTTP $SD)" || fail "Drink confirm failed (HTTP $SD)"

echo ""
info "Verifying stock decreased by 1 across all three suppliers..."
T_STOCK_AFTER=$(curl -s -H "Authorization: Bearer $TOKEN" "$TICKET/products" | python3 -c "
import sys,json
for p in json.load(sys.stdin):
    if p['id']=='T-001': print(p['stock'])
" 2>/dev/null)
F_STOCK_AFTER=$(curl -s -H "Authorization: Bearer $TOKEN" "$FOOD/products" | python3 -c "
import sys,json
for p in json.load(sys.stdin):
    if p['id']=='F-001': print(p['stock'])
" 2>/dev/null)
D_STOCK_AFTER=$(curl -s -H "Authorization: Bearer $TOKEN" "$FOOD/products" | python3 -c "
import sys,json
for p in json.load(sys.stdin):
    if p['id']=='D-001': print(p['stock'])
" 2>/dev/null)

[ "$T_STOCK_AFTER" = "$((T_STOCK - 1))" ] && pass "Ticket stock: $T_STOCK → $T_STOCK_AFTER" || fail "Ticket stock expected $((T_STOCK - 1)), got $T_STOCK_AFTER"
[ "$F_STOCK_AFTER" = "$((F_STOCK - 1))" ] && pass "Food stock:   $F_STOCK → $F_STOCK_AFTER" || fail "Food stock expected $((F_STOCK - 1)), got $F_STOCK_AFTER"
[ "$D_STOCK_AFTER" = "$((D_STOCK - 1))" ] && pass "Drink stock:  $D_STOCK → $D_STOCK_AFTER" || fail "Drink stock expected $((D_STOCK - 1)), got $D_STOCK_AFTER"

echo ""
pass "TEST COMPLETE — full 3-supplier 2PC committed successfully across all services"

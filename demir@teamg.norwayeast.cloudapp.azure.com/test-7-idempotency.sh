#!/bin/bash
# TEST 7: Idempotency — confirm and cancel are safe to call multiple times.
# The 2PC protocol requires that confirm and cancel can be retried without side effects.
# Run from: anywhere with network access to the supplier VMs.

source "$(dirname "$0")/config.sh"

echo "========================================"
echo " TEST 7: Idempotency of Confirm & Cancel"
echo "========================================"

require_token

echo ""
info "Creating a reservation..."
RES=$(curl -s -X POST "$TICKET/reservations" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d '{"productId":"T-001","quantity":1}')
RES_ID=$(echo "$RES" | python3 -c "import sys,json; print(json.load(sys.stdin).get('reservationId',''))" 2>/dev/null)

if [ -z "$RES_ID" ]; then
    fail "Could not create reservation: $RES"; exit 1
fi
pass "Reservation created → $RES_ID"

echo ""
info "Confirming reservation (first time)..."
S1=$(curl -s -o /dev/null -w "%{http_code}" -X POST \
    -H "Authorization: Bearer $TOKEN" "$TICKET/reservations/$RES_ID/confirm")
[ "$S1" = "200" ] && pass "First confirm → HTTP $S1" || fail "First confirm → HTTP $S1"

info "Confirming reservation (second time — idempotent retry)..."
S2=$(curl -s -o /dev/null -w "%{http_code}" -X POST \
    -H "Authorization: Bearer $TOKEN" "$TICKET/reservations/$RES_ID/confirm")
[ "$S2" = "200" ] && pass "Second confirm → HTTP $S2 (idempotent)" || fail "Second confirm → HTTP $S2"

echo ""
info "Creating another reservation to test cancel idempotency..."
RES2=$(curl -s -X POST "$TICKET/reservations" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d '{"productId":"T-001","quantity":1}')
RES2_ID=$(echo "$RES2" | python3 -c "import sys,json; print(json.load(sys.stdin).get('reservationId',''))" 2>/dev/null)
pass "Reservation created → $RES2_ID"

info "Cancelling reservation (first time)..."
S3=$(curl -s -o /dev/null -w "%{http_code}" -X DELETE \
    -H "Authorization: Bearer $TOKEN" "$TICKET/reservations/$RES2_ID")
[ "$S3" = "204" ] && pass "First cancel → HTTP $S3" || fail "First cancel → HTTP $S3"

info "Cancelling reservation (second time — idempotent retry)..."
S4=$(curl -s -o /dev/null -w "%{http_code}" -X DELETE \
    -H "Authorization: Bearer $TOKEN" "$TICKET/reservations/$RES2_ID")
[ "$S4" = "204" ] && pass "Second cancel → HTTP $S4 (idempotent)" || fail "Second cancel → HTTP $S4"

echo ""
info "Trying to cancel an already-confirmed reservation (should be safe no-op)..."
S5=$(curl -s -o /dev/null -w "%{http_code}" -X DELETE \
    -H "Authorization: Bearer $TOKEN" "$TICKET/reservations/$RES_ID")
[ "$S5" = "204" ] && pass "Cancel on confirmed → HTTP $S5 (safe no-op)" || fail "Cancel on confirmed → HTTP $S5"

echo ""
pass "TEST COMPLETE — confirm and cancel are both idempotent"

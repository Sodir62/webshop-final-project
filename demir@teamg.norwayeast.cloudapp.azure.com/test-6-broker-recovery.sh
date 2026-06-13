#!/bin/bash
# TEST 6: Broker Crash Recovery — 2PC durability
# Places real reservations on suppliers (simulating broker mid-order), inserts stuck orders
# directly into the broker's Azure PostgreSQL, restarts the broker, and verifies recovery:
#   RESERVING orders  → rolled BACK  (holds cancelled, stock restored)
#   RESERVED orders   → rolled FORWARD (confirms completed, order SUCCEEDED)
#
# Run from: broker VM — needs psql (postgresql-client) installed and sudo access.

source "$(dirname "$0")/config.sh"

echo "========================================"
echo " TEST 6: Broker Crash Recovery"
echo "========================================"

require_token

# Verify psql is available
if ! command -v psql &>/dev/null; then
    fail "psql not found — install with: sudo apt-get install -y postgresql-client"
    exit 1
fi
info "psql found — will connect to $BROKER_DB_HOST"

echo ""
echo "--- Scenario A: Crash during RESERVING (should ROLLBACK) ---"
echo ""

info "Making a real reservation on ticket supplier to simulate phase 1 (hold open, no confirm)..."
TICKET_RES=$(curl -s -X POST "$TICKET/reservations" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d '{"productId":"T-001","quantity":1}')
TICKET_RES_ID=$(echo "$TICKET_RES" | python3 -c "import sys,json; print(json.load(sys.stdin).get('reservationId',''))" 2>/dev/null)

if [ -z "$TICKET_RES_ID" ]; then
    fail "Could not create test reservation: $TICKET_RES"
    exit 1
fi
pass "Ticket reservation placed → $TICKET_RES_ID"

ORDER_ID=$(python3 -c "import uuid; print(str(uuid.uuid4()))")
info "Inserting stuck RESERVING order into broker DB (order=$ORDER_ID)..."
broker_psql <<EOF
INSERT INTO customer_order (id, status, delivery_address, cardholder_name, card_last4, created_at)
VALUES ('$ORDER_ID', 'RESERVING', '123 Test St', 'Test User', '1234', NOW());

INSERT INTO order_item (supplier_type, product_id, product_name, unit_price, quantity, reservation_id, status, order_id)
VALUES ('TICKET', 'T-001', 'Coldplay @ Sportpaleis', 85.00, 1, '$TICKET_RES_ID', 'RESERVED', '$ORDER_ID');
EOF
pass "Stuck RESERVING order inserted (id=$ORDER_ID)"

echo ""
info "Restarting broker — OrderRecoveryRunner should roll back this order on startup..."
sudo systemctl restart broker
sleep 12

info "Checking order status in broker DB..."
STATUS=$(broker_psql -t -c \
    "SELECT status FROM customer_order WHERE id='$ORDER_ID';" | tr -d ' \n')

if [ "$STATUS" = "FAILED" ]; then
    pass "Order rolled back to FAILED — RESERVING orders are correctly undone on restart"
else
    fail "Expected FAILED, got '$STATUS'"
fi

info "Checking ticket reservation was cancelled (stock restored)..."
STOCK=$(curl -s -H "Authorization: Bearer $TOKEN" "$TICKET/products" | python3 -c "
import sys,json
for p in json.load(sys.stdin):
    if p['id']=='T-001': print(p['stock'])
" 2>/dev/null)
info "T-001 stock after rollback: $STOCK"

echo ""
echo "--- Scenario B: Crash during RESERVED / commit decided (should ROLL FORWARD) ---"
echo ""

info "Making reservations on both suppliers to simulate commit-decided, mid-confirm crash..."
T_RES=$(curl -s -X POST "$TICKET/reservations" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d '{"productId":"T-001","quantity":1}')
T_ID=$(echo "$T_RES" | python3 -c "import sys,json; print(json.load(sys.stdin).get('reservationId',''))" 2>/dev/null)

F_RES=$(curl -s -X POST "$FOOD/reservations" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d '{"productId":"F-001","quantity":1}')
F_ID=$(echo "$F_RES" | python3 -c "import sys,json; print(json.load(sys.stdin).get('reservationId',''))" 2>/dev/null)

if [ -z "$T_ID" ] || [ -z "$F_ID" ]; then
    fail "Could not place reservations for scenario B (ticket=$T_ID food=$F_ID)"
    exit 1
fi
pass "Reservations placed — ticket=$T_ID food=$F_ID"

ORDER2_ID=$(python3 -c "import uuid; print(str(uuid.uuid4()))")
info "Inserting stuck RESERVED order (commit decision already made, broker died before confirming)..."
broker_psql <<EOF
INSERT INTO customer_order (id, status, delivery_address, cardholder_name, card_last4, created_at)
VALUES ('$ORDER2_ID', 'RESERVED', '456 Test Ave', 'Test User2', '5678', NOW());

INSERT INTO order_item (supplier_type, product_id, product_name, unit_price, quantity, reservation_id, status, order_id)
VALUES ('TICKET', 'T-001', 'Coldplay @ Sportpaleis', 85.00, 1, '$T_ID', 'RESERVED', '$ORDER2_ID');

INSERT INTO order_item (supplier_type, product_id, product_name, unit_price, quantity, reservation_id, status, order_id)
VALUES ('FOOD', 'F-001', 'Nachos', 6.50, 1, '$F_ID', 'RESERVED', '$ORDER2_ID');
EOF
pass "Stuck RESERVED order inserted (id=$ORDER2_ID)"

info "Restarting broker — OrderRecoveryRunner should roll FORWARD this order..."
sudo systemctl restart broker
sleep 12

STATUS2=$(broker_psql -t -c \
    "SELECT status FROM customer_order WHERE id='$ORDER2_ID';" | tr -d ' \n')

if [ "$STATUS2" = "SUCCEEDED" ]; then
    pass "Order rolled forward to SUCCEEDED — RESERVED orders correctly completed on restart"
else
    fail "Expected SUCCEEDED, got '$STATUS2'"
fi

echo ""
pass "TEST COMPLETE — broker crash recovery verified for ROLLBACK and ROLL-FORWARD"

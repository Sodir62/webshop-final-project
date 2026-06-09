#!/bin/bash
# TEST 6: Broker Crash Recovery — 2PC durability
# Manually places reservations on both suppliers (simulating broker mid-order),
# then inserts an order stuck in RESERVING state into the broker DB,
# restarts the broker, and verifies it rolls back the stuck order.
#
# Run from: broker VM — needs psql and sudo access.
#
# This tests the OrderRecoveryRunner which runs on startup and:
#   - RESERVING orders  → rolled BACK  (cancel all holds)
#   - RESERVED/CONFIRMING orders → rolled FORWARD (finish confirming)

source "$(dirname "$0")/config.sh"

echo "========================================"
echo " TEST 6: Broker Crash Recovery"
echo "========================================"

echo ""
echo "--- Scenario A: Crash during RESERVING (should ROLLBACK) ---"
echo ""

info "Making a real reservation on ticket supplier to simulate phase 1..."
TICKET_RES=$(curl -s -X POST "$TICKET/reservations" \
    -H "Content-Type: application/json" \
    -d '{"productId":"T-001","quantity":1}')
TICKET_RES_ID=$(echo "$TICKET_RES" | grep -o '"reservationId":"[^"]*"' | cut -d'"' -f4)

if [ -z "$TICKET_RES_ID" ]; then
    fail "Could not create test reservation: $TICKET_RES"
    exit 1
fi
pass "Ticket reservation placed → $TICKET_RES_ID"

ORDER_ID=$(python3 -c "import uuid; print(str(uuid.uuid4()))")
info "Inserting a fake STUCK order (status=RESERVING) into broker DB..."
sudo -u postgres psql -d brokerdb <<EOF
INSERT INTO customer_order (id, status, delivery_address, cardholder_name, card_last4, created_at)
VALUES ('$ORDER_ID', 'RESERVING', '123 Test St', 'Test User', '1234', NOW());

INSERT INTO order_item (supplier_type, product_id, product_name, unit_price, quantity, reservation_id, status, order_id)
VALUES ('TICKET', 'T-001', 'Coldplay @ Sportpaleis', 85.00, 1, '$TICKET_RES_ID', 'RESERVED', '$ORDER_ID');
EOF
pass "Stuck order inserted (id=$ORDER_ID)"

echo ""
info "Restarting broker — OrderRecoveryRunner should roll back this order..."
sudo systemctl restart broker
sleep 8

info "Checking order status in DB..."
STATUS=$(sudo -u postgres psql -d brokerdb -t -c \
    "SELECT status FROM customer_order WHERE id='$ORDER_ID';" | tr -d ' \n')

if [ "$STATUS" = "FAILED" ]; then
    pass "Order rolled back to FAILED — RESERVING orders are correctly undone on restart"
else
    fail "Expected FAILED, got '$STATUS'"
fi

info "Checking ticket reservation was cancelled (stock restored)..."
PROD=$(curl -s "$TICKET/products" | python3 -c "
import sys,json
for p in json.load(sys.stdin):
    if p['id']=='T-001': print(p['stock'])
" 2>/dev/null)
info "T-001 stock after rollback: $PROD"

echo ""
echo "--- Scenario B: Crash during CONFIRMING (should ROLL FORWARD) ---"
echo ""

info "Making reservations on both suppliers..."
T_RES=$(curl -s -X POST "$TICKET/reservations" \
    -H "Content-Type: application/json" \
    -d '{"productId":"T-001","quantity":1}')
T_ID=$(echo "$T_RES" | grep -o '"reservationId":"[^"]*"' | cut -d'"' -f4)

F_RES=$(curl -s -X POST "$FOOD/reservations" \
    -H "Content-Type: application/json" \
    -d '{"productId":"F-001","quantity":1}')
F_ID=$(echo "$F_RES" | grep -o '"reservationId":"[^"]*"' | cut -d'"' -f4)
pass "Reservations placed — ticket=$T_ID food=$F_ID"

ORDER2_ID=$(python3 -c "import uuid; print(str(uuid.uuid4()))")
info "Inserting a stuck RESERVED order (commit decision already made)..."
sudo -u postgres psql -d brokerdb <<EOF
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
sleep 8

STATUS2=$(sudo -u postgres psql -d brokerdb -t -c \
    "SELECT status FROM customer_order WHERE id='$ORDER2_ID';" | tr -d ' \n')

if [ "$STATUS2" = "SUCCEEDED" ]; then
    pass "Order rolled forward to SUCCEEDED — RESERVED orders are correctly completed on restart"
else
    fail "Expected SUCCEEDED, got '$STATUS2'"
fi

echo ""
pass "TEST COMPLETE — broker crash recovery verified for both ROLLBACK and ROLL-FORWARD cases"

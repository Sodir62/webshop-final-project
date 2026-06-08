#!/bin/bash
# TEST 5: Supplier Crash — broker degrades gracefully
# Stops the ticket supplier, verifies broker catalog still loads (shows empty tickets).
# Then restarts it and verifies recovery.
#
# Run from: dimitris's VM (ticket supplier VM) — needs sudo access.
# The broker homepage is checked remotely.

source "$(dirname "$0")/config.sh"

echo "========================================"
echo " TEST 5: Supplier Crash — Graceful Degradation"
echo "========================================"

info "Verifying all services are up before test..."
BROKER_STATUS=$(curl -s -o /dev/null -w "%{http_code}" --connect-timeout 5 "$BROKER")
TICKET_STATUS=$(curl -s -o /dev/null -w "%{http_code}" --connect-timeout 5 "$TICKET/products")
if [ "$BROKER_STATUS" != "200" ] || [ "$TICKET_STATUS" != "200" ]; then
    fail "Pre-condition failed: broker=$BROKER_STATUS ticket=$TICKET_STATUS"
    exit 1
fi
pass "All services up"

echo ""
info "Stopping ticket supplier service..."
sudo systemctl stop ticket
sleep 2

TICKET_DOWN=$(curl -s -o /dev/null -w "%{http_code}" --connect-timeout 3 "$TICKET/products")
if [ "$TICKET_DOWN" != "200" ]; then
    pass "Ticket supplier is DOWN (HTTP $TICKET_DOWN)"
else
    fail "Ticket supplier still responding — stop may have failed"
fi

echo ""
info "Checking broker homepage — should still load (food visible, tickets empty)..."
BROKER_AFTER=$(curl -s -o /dev/null -w "%{http_code}" --connect-timeout 5 "$BROKER")
if [ "$BROKER_AFTER" = "200" ]; then
    pass "Broker homepage still returns HTTP 200 — did not crash"
else
    fail "Broker returned HTTP $BROKER_AFTER — broker crashed due to supplier failure!"
fi

echo ""
info "Restarting ticket supplier..."
sudo systemctl start ticket
sleep 5

TICKET_UP=$(curl -s -o /dev/null -w "%{http_code}" --connect-timeout 5 "$TICKET/products")
if [ "$TICKET_UP" = "200" ]; then
    pass "Ticket supplier recovered — catalog available again"
else
    fail "Ticket supplier did not come back (HTTP $TICKET_UP)"
fi

BROKER_RECOVERED=$(curl -s -o /dev/null -w "%{http_code}" --connect-timeout 5 "$BROKER")
if [ "$BROKER_RECOVERED" = "200" ]; then
    pass "Broker still healthy after supplier recovery"
fi

echo ""
pass "TEST COMPLETE — broker survived supplier crash without crashing"

#!/bin/bash
# TEST 5: Supplier Crash — broker degrades gracefully
# Stops the ticket supplier, verifies broker is still reachable (returns 302 Auth0 redirect,
# not a crash), then restarts it and verifies recovery.
# Run from: dimitris's VM (ticket supplier VM) — needs sudo access.

source "$(dirname "$0")/config.sh"

echo "========================================"
echo " TEST 5: Supplier Crash — Graceful Degradation"
echo "========================================"

require_token

echo ""
info "Verifying all services are up before test..."
BROKER_STATUS=$(curl -s -o /dev/null -w "%{http_code}" --connect-timeout 5 "$BROKER")
TICKET_STATUS=$(curl -s -o /dev/null -w "%{http_code}" --connect-timeout 5 \
    -H "Authorization: Bearer $TOKEN" "$TICKET/products")

if [[ "$BROKER_STATUS" != "200" && "$BROKER_STATUS" != "302" ]]; then
    fail "Pre-condition failed: broker=$BROKER_STATUS (expected 200 or 302)"
    exit 1
fi
if [ "$TICKET_STATUS" != "200" ]; then
    fail "Pre-condition failed: ticket supplier=$TICKET_STATUS (expected 200)"
    exit 1
fi
pass "All services up (broker: $BROKER_STATUS, ticket: $TICKET_STATUS)"

echo ""
info "Stopping ticket supplier service..."
sudo systemctl stop ticket
sleep 2

TICKET_DOWN=$(curl -s -o /dev/null -w "%{http_code}" --connect-timeout 3 \
    -H "Authorization: Bearer $TOKEN" "$TICKET/products")
if [ "$TICKET_DOWN" != "200" ]; then
    pass "Ticket supplier is DOWN (HTTP $TICKET_DOWN)"
else
    fail "Ticket supplier still responding — stop may have failed"
fi

echo ""
info "Checking broker — should still be reachable (catalog degrades, not crashes)..."
BROKER_AFTER=$(curl -s -o /dev/null -w "%{http_code}" --connect-timeout 5 "$BROKER")
if [ "$BROKER_AFTER" = "200" ] || [ "$BROKER_AFTER" = "302" ]; then
    pass "Broker still responds HTTP $BROKER_AFTER — did not crash when supplier went down"
else
    fail "Broker returned HTTP $BROKER_AFTER — broker may have crashed!"
fi

echo ""
info "Restarting ticket supplier..."
sudo systemctl start ticket
sleep 8

TICKET_UP=$(curl -s -o /dev/null -w "%{http_code}" --connect-timeout 5 \
    -H "Authorization: Bearer $TOKEN" "$TICKET/products")
if [ "$TICKET_UP" = "200" ]; then
    pass "Ticket supplier recovered — catalog available again"
else
    fail "Ticket supplier did not come back (HTTP $TICKET_UP)"
fi

BROKER_RECOVERED=$(curl -s -o /dev/null -w "%{http_code}" --connect-timeout 5 "$BROKER")
if [ "$BROKER_RECOVERED" = "200" ] || [ "$BROKER_RECOVERED" = "302" ]; then
    pass "Broker still healthy after supplier recovery (HTTP $BROKER_RECOVERED)"
fi

echo ""
pass "TEST COMPLETE — broker survived supplier crash without crashing"

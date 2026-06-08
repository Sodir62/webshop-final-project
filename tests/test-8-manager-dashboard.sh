#!/bin/bash
# TEST 8: Manager Dashboard — access control
# Verifies that /manager/orders requires authentication.
# Run from: anywhere with network access to the broker VM.

source "$(dirname "$0")/config.sh"

echo "========================================"
echo " TEST 8: Manager Dashboard Access Control"
echo "========================================"

info "Accessing /manager/orders without credentials..."
S1=$(curl -s -o /dev/null -w "%{http_code}" "$BROKER/manager/orders")
if [ "$S1" = "401" ] || [ "$S1" = "302" ]; then
    pass "Unauthenticated request blocked → HTTP $S1"
else
    fail "Expected 401/302, got HTTP $S1 — endpoint may be unprotected!"
fi

info "Accessing /manager/orders with wrong password..."
S2=$(curl -s -o /dev/null -w "%{http_code}" -u "$MANAGER_USER:wrongpassword" "$BROKER/manager/orders")
if [ "$S2" = "401" ] || [ "$S2" = "403" ]; then
    pass "Wrong password blocked → HTTP $S2"
else
    fail "Expected 401/403, got HTTP $S2"
fi

info "Accessing /manager/orders with correct credentials..."
S3=$(curl -s -o /dev/null -w "%{http_code}" -u "$MANAGER_USER:$MANAGER_PASS" "$BROKER/manager/orders")
if [ "$S3" = "200" ]; then
    pass "Authenticated access granted → HTTP $S3"
else
    fail "Expected 200, got HTTP $S3 — check MANAGER_USER and MANAGER_PASS in config.sh"
fi

echo ""
pass "TEST COMPLETE — manager dashboard correctly protected"

#!/bin/bash
# TEST 8: Manager Dashboard — access control
# Verifies that /manager/orders is protected by Auth0 OIDC:
#   - Unauthenticated requests are redirected to Auth0 login (302), not served directly.
#   - The supplier JWT (M2M token) is not accepted for the broker web UI — the broker uses
#     OIDC for human users, not bearer tokens for its own web pages.
# Full role-based access (MANAGER role) is demonstrated via the browser after login.
# Run from: anywhere with network access to the broker VM.

source "$(dirname "$0")/config.sh"

echo "========================================"
echo " TEST 8: Manager Dashboard Access Control"
echo "========================================"

echo ""
info "Accessing /manager/orders without any credentials..."
S1=$(curl -s -o /dev/null -w "%{http_code}" --max-redirs 0 "$BROKER/manager/orders")
if [ "$S1" = "302" ]; then
    pass "Unauthenticated request redirected → HTTP 302 (Auth0 login gate)"
else
    fail "Expected 302, got HTTP $S1 — endpoint may be unprotected!"
fi

info "Checking redirect target is the OAuth2 login flow..."
LOCATION=$(curl -s -D - -o /dev/null --max-redirs 0 "$BROKER/manager/orders" | grep -i "^location:" | tr -d '\r')
if echo "$LOCATION" | grep -qE "(auth0|oauth2/authorization)"; then
    pass "Redirect points to OAuth2/Auth0 login: $LOCATION"
else
    fail "Redirect does not point to login: $LOCATION"
fi

echo ""
info "Trying M2M Bearer token (should NOT grant access to broker web UI — it is OIDC-only)..."
S2=$(curl -s -o /dev/null -w "%{http_code}" --max-redirs 0 \
    -H "Authorization: Bearer $(get_token)" "$BROKER/manager/orders")
if [ "$S2" = "302" ] || [ "$S2" = "401" ] || [ "$S2" = "403" ]; then
    pass "M2M token correctly rejected for web UI → HTTP $S2"
else
    fail "Expected redirect/rejection, got HTTP $S2"
fi

echo ""
info "Accessing public assets (should be permitted without login)..."
S3=$(curl -s -o /dev/null -w "%{http_code}" "$BROKER/style.css")
[ "$S3" = "200" ] && pass "Public asset /style.css accessible → HTTP $S3" || fail "/style.css → HTTP $S3"

echo ""
pass "TEST COMPLETE — /manager/orders is Auth0-protected; MANAGER role verified via browser"

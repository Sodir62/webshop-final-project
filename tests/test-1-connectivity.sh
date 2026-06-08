#!/bin/bash
# TEST 1: Connectivity
# Checks all 3 services are reachable and responding.
# Run from: anywhere with network access to the VMs.

source "$(dirname "$0")/config.sh"

echo "========================================"
echo " TEST 1: Connectivity Check"
echo "========================================"

check() {
    local name=$1
    local url=$2
    local status=$(curl -s -o /dev/null -w "%{http_code}" --connect-timeout 5 "$url")
    if [ "$status" = "200" ]; then
        pass "$name is UP ($url) → HTTP $status"
    else
        fail "$name is DOWN or unreachable ($url) → HTTP $status"
    fi
}

check "Broker"            "$BROKER"
check "Ticket Supplier"   "$TICKET/products"
check "Food Supplier"     "$FOOD/products"

echo ""
info "Ticket catalog:"
curl -s "$TICKET/products" | python3 -m json.tool 2>/dev/null || curl -s "$TICKET/products"

echo ""
info "Food catalog:"
curl -s "$FOOD/products" | python3 -m json.tool 2>/dev/null || curl -s "$FOOD/products"

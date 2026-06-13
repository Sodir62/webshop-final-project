#!/bin/bash
# Shared configuration — sourced by all test scripts.

BROKER_IP="20.251.203.131"
TICKET_IP="172.161.52.246"
FOOD_IP="74.248.144.81"

BROKER="http://$BROKER_IP:8080"
TICKET="http://$TICKET_IP:8082"
FOOD="http://$FOOD_IP:8081"

# Auth0 M2M credentials (same client_credentials grant the broker uses for supplier calls)
AUTH0_DOMAIN="dev-teamg-ticketmaster.eu.auth0.com"
AUTH0_M2M_CLIENT_ID="So5PYXrM5XL3YAvL3ljX0ybkusx1lOGx"
AUTH0_M2M_CLIENT_SECRET="tOsV2ieHu00F8MxaB-FSMOSCgwNJku8ffplODzeT7nyxvaE8iT36GdmEJDRo1w2W"
AUTH0_AUDIENCE="https://webshop-api"

# Broker DB (Azure PostgreSQL) — used by test-6 to inspect order state directly
BROKER_DB_HOST="broker-server.postgres.database.azure.com"
BROKER_DB_NAME="brokerdb"
BROKER_DB_USER="brokeradmin"
BROKER_DB_PASS="Demirhan12345."

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

pass() { echo -e "${GREEN}[PASS]${NC} $1"; }
fail() { echo -e "${RED}[FAIL]${NC} $1"; }
info() { echo -e "${YELLOW}[INFO]${NC} $1"; }

# Fetches (and process-caches) an Auth0 M2M access token.
# Usage: TOKEN=$(get_token) or: require_token
_CACHED_TOKEN=""
get_token() {
    if [ -n "$_CACHED_TOKEN" ]; then
        echo "$_CACHED_TOKEN"
        return 0
    fi
    _CACHED_TOKEN=$(curl -s -X POST "https://$AUTH0_DOMAIN/oauth/token" \
        -H "Content-Type: application/json" \
        -d "{
            \"client_id\":     \"$AUTH0_M2M_CLIENT_ID\",
            \"client_secret\": \"$AUTH0_M2M_CLIENT_SECRET\",
            \"audience\":      \"$AUTH0_AUDIENCE\",
            \"grant_type\":    \"client_credentials\"
        }" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('access_token',''))" 2>/dev/null)
    if [ -z "$_CACHED_TOKEN" ]; then
        return 1
    fi
    echo "$_CACHED_TOKEN"
}

require_token() {
    info "Fetching M2M token from Auth0..."
    TOKEN=$(get_token)
    if [ -z "$TOKEN" ]; then
        fail "Could not get Auth0 M2M token — check credentials in config.sh"
        exit 1
    fi
    pass "M2M token obtained"
}

# Runs a SQL statement against the broker's Azure PostgreSQL.
# Usage: broker_psql -c "SELECT ..."
broker_psql() {
    PGPASSWORD="$BROKER_DB_PASS" psql \
        "host=$BROKER_DB_HOST port=5432 dbname=$BROKER_DB_NAME user=$BROKER_DB_USER sslmode=require" \
        "$@"
}

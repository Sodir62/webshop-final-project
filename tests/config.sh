#!/bin/bash
# Shared configuration — sourced by all test scripts.
# Update these IPs before running any test.

BROKER_IP="20.251.203.131"
TICKET_IP="172.161.52.246"
FOOD_IP="74.248.144.81"

BROKER="http://$BROKER_IP:8080"
TICKET="http://$TICKET_IP:8082"
FOOD="http://$FOOD_IP:8081"

MANAGER_USER="admin"
# Must match the broker's app.manager.password (committed default "admin"). If the deployment
# overrides it via the MANAGER_PASSWORD env var, set the same value here.
MANAGER_PASS="admin"

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

pass() { echo -e "${GREEN}[PASS]${NC} $1"; }
fail() { echo -e "${RED}[FAIL]${NC} $1"; }
info() { echo -e "${YELLOW}[INFO]${NC} $1"; }

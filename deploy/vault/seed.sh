#!/bin/sh
# Writes the analyzer's secrets to Vault KV v2. Spring Cloud Vault maps each key to a property.
set -e
vault kv put secret/analyzer-service \
  spring.datasource.password=local-dev-only \
  sentinel.mcp.token=local-mcp-token
echo "Seeded secret/analyzer-service"

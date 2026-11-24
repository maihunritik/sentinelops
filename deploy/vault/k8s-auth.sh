#!/bin/sh
# One-time Vault setup so analyzer pods can log in with their Kubernetes service account
# and read only their own secrets. Run with a Vault admin token.
set -e
vault auth enable kubernetes || true
vault write auth/kubernetes/config kubernetes_host="https://kubernetes.default.svc"

vault policy write analyzer-service - <<'POLICY'
path "secret/data/analyzer-service" {
  capabilities = ["read"]
}
POLICY

vault write auth/kubernetes/role/analyzer-service \
  bound_service_account_names=analyzer-service \
  bound_service_account_namespaces=sentinelops \
  policies=analyzer-service \
  ttl=1h

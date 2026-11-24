# payments service

Service-specific guide for the payments service (checkout, refunds, payment gateway).

## Known failure modes
- **Payment gateway timeouts**: the external gateway degrades during peak hours. Symptoms are latency regression on POST /payments followed by 504 errors.
- **Database deadlocks on refunds**: concurrent refunds on the same order. Errors are 500 with "deadlock detected" in logs.
- **Expired gateway credentials**: credentials are rotated in Vault; a pod that missed the rotation returns 502 on every call. Restart the pod to re-read the secret.

## Escalation
Page the payments on-call if the error rate stays above 50% for more than 5 minutes.

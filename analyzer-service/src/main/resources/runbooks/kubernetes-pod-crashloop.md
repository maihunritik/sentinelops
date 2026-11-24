# Kubernetes pod crash loop

Use when pods restart repeatedly (CrashLoopBackOff) and the service returns errors.

## Triage
1. `kubectl get pods -l app=<service>` and `kubectl describe pod <pod>` for the last state and exit code.
2. Exit code 137 means OOMKilled: compare memory limits with actual heap usage.
3. `kubectl logs <pod> --previous` for the stack trace of the crashed container.
4. Failing readiness probes after start usually mean a dependency (Vault, database, Kafka) is unreachable.

## Mitigate
- Raise the memory limit or lower -XX:MaxRAMPercentage if OOMKilled.
- Roll back the last deployment if the crash started after a release.

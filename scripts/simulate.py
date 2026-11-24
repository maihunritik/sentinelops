#!/usr/bin/env python3
"""Sends realistic traffic to the ingest service, then injects an outage so you can watch
SentinelOps open, and later resolve, an incident.

    python3 scripts/simulate.py                       # default: payments fails at ~30s
    python3 scripts/simulate.py --fault latency --service search
"""
import argparse
import datetime as dt
import json
import random
import time
import urllib.request
import uuid

SERVICES = {"payments": "POST /payments", "search": "GET /search", "orders": "GET /orders/{id}"}


def event(service, status, latency):
    return {
        "eventId": str(uuid.uuid4()),
        "service": service,
        "endpoint": SERVICES[service],
        "statusCode": status,
        "latencyMs": int(latency),
        "timestamp": dt.datetime.now(dt.timezone.utc).isoformat(),
    }


def post(url, events):
    req = urllib.request.Request(url, data=json.dumps(events).encode(), method="POST",
                                 headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=5) as resp:
        return json.load(resp)


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--url", default="http://localhost:8081/api/v1/telemetry/batch")
    p.add_argument("--service", default="payments", choices=SERVICES)
    p.add_argument("--fault", default="errors", choices=["errors", "latency"])
    p.add_argument("--fault-start", type=int, default=30, help="seconds of healthy traffic first")
    p.add_argument("--fault-length", type=int, default=60)
    p.add_argument("--duration", type=int, default=180)
    p.add_argument("--rps", type=int, default=20, help="requests per second per service")
    args = p.parse_args()

    start = time.time()
    while (elapsed := time.time() - start) < args.duration:
        faulty = args.fault_start <= elapsed < args.fault_start + args.fault_length
        batch = []
        for service in SERVICES:
            for _ in range(args.rps):
                status, latency = 200, random.gauss(90, 20)
                if faulty and service == args.service:
                    if args.fault == "errors" and random.random() < 0.45:
                        status = random.choice([500, 502, 503])
                    if args.fault == "latency":
                        latency = random.gauss(900, 150)
                batch.append(event(service, status, max(1, latency)))
        result = post(args.url, batch)
        state = f"FAULT ({args.fault} on {args.service})" if faulty else "healthy"
        print(f"t={elapsed:5.1f}s  sent={len(batch)}  accepted={result['accepted']}  {state}")
        time.sleep(1)


if __name__ == "__main__":
    main()

package io.sentinelops.analyzer.runbook;

/** A remediation guide loaded from {@code classpath:runbooks/*.md}. */
public record Runbook(String id, String title, String content) {
}

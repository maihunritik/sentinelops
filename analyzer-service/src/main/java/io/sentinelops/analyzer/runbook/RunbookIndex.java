package io.sentinelops.analyzer.runbook;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Small in-process BM25 index over the runbook library. This is the retrieval half of RAG:
 * the MCP tools hand the matching runbook text to the calling agent, which does the
 * generation. Swapping this for a vector store only changes this class.
 */
@Component
public class RunbookIndex {

    private static final double K1 = 1.2;
    private static final double B = 0.75;
    private static final Set<String> STOP = Set.of("the", "a", "an", "and", "or", "of", "to", "in", "on", "for",
            "is", "are", "be", "with", "if", "it", "this", "that", "as", "at", "by", "from", "check");

    private record Doc(Runbook runbook, Map<String, Integer> tf, int length) {
    }

    private final List<Doc> docs = new ArrayList<>();
    private final Map<String, Integer> df = new HashMap<>();
    private final double avgLength;

    public RunbookIndex() {
        this(loadClasspath());
    }

    public RunbookIndex(List<Runbook> runbooks) {
        long total = 0;
        for (Runbook r : runbooks) {
            Map<String, Integer> tf = new HashMap<>();
            List<String> tokens = tokenize(r.title() + " " + r.title() + " " + r.content());
            tokens.forEach(t -> tf.merge(t, 1, Integer::sum));
            tf.keySet().forEach(t -> df.merge(t, 1, Integer::sum));
            docs.add(new Doc(r, tf, tokens.size()));
            total += tokens.size();
        }
        avgLength = docs.isEmpty() ? 1 : (double) total / docs.size();
    }

    public List<RunbookMatch> search(String query, int limit) {
        List<String> terms = tokenize(query).stream().distinct().toList();
        int n = docs.size();
        return docs.stream()
                .map(d -> {
                    double score = 0;
                    for (String t : terms) {
                        int f = d.tf().getOrDefault(t, 0);
                        if (f == 0) {
                            continue;
                        }
                        int docFreq = df.getOrDefault(t, 0);
                        double idf = Math.log(1 + (n - docFreq + 0.5) / (docFreq + 0.5));
                        score += idf * (f * (K1 + 1)) / (f + K1 * (1 - B + B * d.length() / avgLength));
                    }
                    return new RunbookMatch(d.runbook().id(), d.runbook().title(), Math.round(score * 100) / 100.0,
                            excerpt(d.runbook().content()));
                })
                .filter(m -> m.score() > 0)
                .sorted(Comparator.comparingDouble(RunbookMatch::score).reversed())
                .limit(Math.max(1, limit))
                .toList();
    }

    public java.util.Optional<Runbook> get(String id) {
        return docs.stream().map(Doc::runbook).filter(r -> r.id().equals(id)).findFirst();
    }

    static List<String> tokenize(String text) {
        return Arrays.stream(text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+"))
                .filter(t -> t.length() > 1 && !STOP.contains(t))
                .toList();
    }

    private static String excerpt(String content) {
        String body = content.lines().filter(l -> !l.isBlank() && !l.startsWith("#")).findFirst().orElse("");
        return body.length() > 200 ? body.substring(0, 197) + "..." : body;
    }

    private static List<Runbook> loadClasspath() {
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver().getResources("classpath*:runbooks/*.md");
            List<Runbook> out = new ArrayList<>();
            for (Resource r : resources) {
                String content = r.getContentAsString(StandardCharsets.UTF_8);
                String id = r.getFilename().replaceFirst("\\.md$", "");
                String title = content.lines().filter(l -> l.startsWith("# ")).findFirst()
                        .map(l -> l.substring(2).trim()).orElse(id);
                out.add(new Runbook(id, title, content));
            }
            return out;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

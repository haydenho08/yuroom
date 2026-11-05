package dev.yuroom.ingestion.source;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * A small robots.txt evaluator following RFC 9309:
 * <ul>
 *   <li>rules are grouped under one or more {@code User-agent} lines;</li>
 *   <li>a crawler uses the group naming its product token, else the {@code *} group;</li>
 *   <li>the longest matching {@code Allow}/{@code Disallow} path wins; ties go to Allow;</li>
 *   <li>{@code *} matches any characters and a trailing {@code $} anchors the end.</li>
 * </ul>
 */
public final class RobotsTxt {

    private record Rule(boolean allow, String pattern, Pattern regex) {
    }

    private final Map<String, List<Rule>> groups;
    private final Boolean blanket; // non-null: allow-all or disallow-all regardless of rules

    private RobotsTxt(Map<String, List<Rule>> groups, Boolean blanket) {
        this.groups = groups;
        this.blanket = blanket;
    }

    public static RobotsTxt allowAll() {
        return new RobotsTxt(Map.of(), Boolean.TRUE);
    }

    public static RobotsTxt disallowAll() {
        return new RobotsTxt(Map.of(), Boolean.FALSE);
    }

    public static RobotsTxt parse(String text) {
        Map<String, List<Rule>> groups = new HashMap<>();
        List<String> currentAgents = new ArrayList<>();
        boolean previousLineWasAgent = false;

        for (String rawLine : text.split("\\R")) {
            String line = stripComment(rawLine).trim();
            int colon = line.indexOf(':');
            if (colon < 0) {
                continue;
            }
            String key = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            String value = line.substring(colon + 1).trim();

            switch (key) {
                case "user-agent" -> {
                    if (!previousLineWasAgent) {
                        currentAgents = new ArrayList<>(); // a new group starts
                    }
                    String agent = value.toLowerCase(Locale.ROOT);
                    currentAgents.add(agent);
                    groups.computeIfAbsent(agent, a -> new ArrayList<>());
                    previousLineWasAgent = true;
                }
                case "allow", "disallow" -> {
                    previousLineWasAgent = false;
                    if (value.isEmpty()) {
                        continue; // "Disallow:" with no path means "nothing is disallowed"
                    }
                    Rule rule = new Rule(key.equals("allow"), value, toRegex(value));
                    for (String agent : currentAgents) {
                        groups.get(agent).add(rule);
                    }
                }
                default -> previousLineWasAgent = false; // sitemap, crawl-delay, etc.
            }
        }
        return new RobotsTxt(groups, null);
    }

    /**
     * @param userAgent full User-Agent string; its first word is the product token
     *                  ("YURoom student project" matches groups named "YURoom")
     * @param path      path plus query, e.g. "/timetable.csv?term=FW"
     */
    public boolean isAllowed(String userAgent, String path) {
        if (blanket != null) {
            return blanket;
        }
        String token = productToken(userAgent);
        List<Rule> rules = groups.containsKey(token) ? groups.get(token) : groups.get("*");
        if (rules == null) {
            return true; // no group applies to us
        }
        Rule best = null;
        for (Rule rule : rules) {
            if (rule.regex.matcher(path).lookingAt()) {
                boolean longer = best == null || rule.pattern.length() > best.pattern.length();
                boolean tieAllow = best != null && rule.pattern.length() == best.pattern.length() && rule.allow;
                if (longer || tieAllow) {
                    best = rule;
                }
            }
        }
        return best == null || best.allow;
    }

    static String productToken(String userAgent) {
        String first = userAgent.trim().split("[\\s/]", 2)[0];
        return first.toLowerCase(Locale.ROOT);
    }

    private static String stripComment(String line) {
        int hash = line.indexOf('#');
        return hash < 0 ? line : line.substring(0, hash);
    }

    private static Pattern toRegex(String pattern) {
        boolean anchored = pattern.endsWith("$");
        String body = anchored ? pattern.substring(0, pattern.length() - 1) : pattern;
        StringBuilder regex = new StringBuilder();
        for (String part : body.split("\\*", -1)) {
            if (!regex.isEmpty()) {
                regex.append(".*");
            }
            regex.append(Pattern.quote(part));
        }
        if (anchored) {
            regex.append('$');
        }
        return Pattern.compile(regex.toString());
    }
}

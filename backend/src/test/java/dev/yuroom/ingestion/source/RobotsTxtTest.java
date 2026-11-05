package dev.yuroom.ingestion.source;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RobotsTxtTest {

    private static final String UA = "YURoom student project";

    @Test
    void emptyDisallowMeansEverythingAllowed() {
        RobotsTxt robots = RobotsTxt.parse("User-agent: *\nDisallow:\n");
        assertThat(robots.isAllowed(UA, "/anything")).isTrue();
    }

    @Test
    void disallowRootBlocksEverything() {
        RobotsTxt robots = RobotsTxt.parse("User-agent: *\nDisallow: /\n");
        assertThat(robots.isAllowed(UA, "/")).isFalse();
        assertThat(robots.isAllowed(UA, "/timetable.csv")).isFalse();
    }

    @Test
    void longestMatchWinsAndTiesGoToAllow() {
        RobotsTxt robots = RobotsTxt.parse("""
                User-agent: *
                Disallow: /data/
                Allow: /data/public/
                Disallow: /tie
                Allow: /tie
                """);
        assertThat(robots.isAllowed(UA, "/data/secret.csv")).isFalse();
        assertThat(robots.isAllowed(UA, "/data/public/timetable.csv")).isTrue();
        assertThat(robots.isAllowed(UA, "/tie")).isTrue();
        assertThat(robots.isAllowed(UA, "/other")).isTrue();
    }

    @Test
    void specificAgentGroupOverridesStar() {
        RobotsTxt robots = RobotsTxt.parse("""
                User-agent: *
                Disallow: /

                User-agent: YURoom
                Allow: /exports/
                Disallow: /
                """);
        assertThat(robots.isAllowed(UA, "/exports/timetable.csv")).isTrue();
        assertThat(robots.isAllowed(UA, "/private")).isFalse();
        assertThat(robots.isAllowed("SomeOtherBot/1.0", "/exports/timetable.csv")).isFalse();
    }

    @Test
    void consecutiveUserAgentLinesShareOneGroup() {
        RobotsTxt robots = RobotsTxt.parse("""
                User-agent: googlebot
                User-agent: yuroom
                Disallow: /secret
                """);
        assertThat(robots.isAllowed(UA, "/secret")).isFalse();
        assertThat(robots.isAllowed(UA, "/public")).isTrue();
    }

    @Test
    void supportsWildcardsAndEndAnchor() {
        RobotsTxt robots = RobotsTxt.parse("""
                User-agent: *
                Disallow: /*.php$
                Disallow: /search*q=
                """);
        assertThat(robots.isAllowed(UA, "/index.php")).isFalse();
        assertThat(robots.isAllowed(UA, "/index.php?x=1")).isTrue();
        assertThat(robots.isAllowed(UA, "/search?q=eecs")).isFalse();
    }

    @Test
    void ignoresCommentsCaseAndUnknownDirectives() {
        RobotsTxt robots = RobotsTxt.parse("""
                # comment
                USER-AGENT: *   # trailing comment
                Crawl-delay: 10
                DISALLOW: /admin
                Sitemap: https://example.org/sitemap.xml
                """);
        assertThat(robots.isAllowed(UA, "/admin/x")).isFalse();
        assertThat(robots.isAllowed(UA, "/")).isTrue();
    }

    @Test
    void noApplicableGroupMeansAllowed() {
        RobotsTxt robots = RobotsTxt.parse("User-agent: googlebot\nDisallow: /\n");
        assertThat(robots.isAllowed(UA, "/")).isTrue();
    }

    @Test
    void productTokenIsFirstWordWithoutVersion() {
        assertThat(RobotsTxt.productToken("YURoom student project")).isEqualTo("yuroom");
        assertThat(RobotsTxt.productToken("YURoom/1.0 (+https://example.org)")).isEqualTo("yuroom");
    }
}

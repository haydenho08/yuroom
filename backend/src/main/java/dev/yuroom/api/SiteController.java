package dev.yuroom.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET /api/info: facts about this deployment that the UI must be honest about. While YURoom runs on
 * synthetic sample data (no permission for York's real timetable yet), {@code demoData} is true and
 * every page says so, so nobody walks across campus to a room based on made-up classes.
 */
@RestController
class SiteController {

    record SiteInfo(boolean demoData) {
    }

    private final boolean demoData;

    SiteController(@Value("${yuroom.demo-data:false}") boolean demoData) {
        this.demoData = demoData;
    }

    @GetMapping("/api/info")
    SiteInfo info() {
        return new SiteInfo(demoData);
    }
}

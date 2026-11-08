package dev.yuroom.web;

import java.io.IOException;
import java.time.Duration;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * In production the React build is packaged inside the jar (classpath:/static), so one process
 * serves both the UI and the API from one origin: no CORS, one thing to deploy.
 *
 * <ul>
 *   <li>{@code /assets/**}: Vite puts a content hash in every file name, so these never change and
 *       can be cached for a year ({@code immutable}).</li>
 *   <li>Everything else: {@code no-cache}, so a new deploy is picked up on the next visit.</li>
 *   <li>Client-side routes like {@code /rooms/42} have no file; they get {@code index.html} and
 *       React Router takes over. API and actuator paths never fall back, so an unknown API URL is
 *       still a JSON 404 rather than a web page.</li>
 * </ul>
 * Spring Boot's own static mapping is switched off ({@code spring.web.resources.add-mappings=false})
 * so this is the only place static files are served from.
 */
@Configuration
class SpaWebConfig implements WebMvcConfigurer {

    static final String STATIC = "classpath:/static/";

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/assets/**")
                .addResourceLocations(STATIC + "assets/")
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable());
        registry.addResourceHandler("/**")
                .addResourceLocations(STATIC)
                .setCacheControl(CacheControl.noCache())
                .resourceChain(false)
                .addResolver(new SpaFallbackResolver());
    }

    static final class SpaFallbackResolver extends PathResourceResolver {
        @Override
        protected Resource getResource(String resourcePath, Resource location) throws IOException {
            if (!resourcePath.isEmpty()) {
                Resource file = super.getResource(resourcePath, location);
                if (file != null) {
                    return file;
                }
            }
            if (!isClientRoute(resourcePath)) {
                return null;
            }
            Resource index = location.createRelative("index.html");
            return index.exists() && index.isReadable() ? index : null;
        }

        /** "rooms/42" is a page; "api/x", "actuator/x" and "missing.js" are not. */
        static boolean isClientRoute(String path) {
            if (path.equals("api") || path.startsWith("api/") || path.startsWith("actuator")) {
                return false;
            }
            String last = path.substring(path.lastIndexOf('/') + 1);
            return !last.contains(".");
        }
    }
}

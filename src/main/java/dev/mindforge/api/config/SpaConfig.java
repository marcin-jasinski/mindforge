package dev.mindforge.api.config;

import java.io.IOException;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/** Serves the Angular build from {@code classpath:/static/}; a client-side route falls through to {@code index.html}. */
@Configuration
public class SpaConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
            .addResourceLocations("classpath:/static/")
            .resourceChain(true)
            .addResolver(new PathResourceResolver() {
                @Override
                protected Resource getResource(String resourcePath, Resource location) throws IOException {
                    Resource requested = location.createRelative(resourcePath);
                    if (requested.exists() && requested.isReadable()) {
                        return requested;
                    }
                    String lastSegment = resourcePath.substring(resourcePath.lastIndexOf('/') + 1);
                    if (resourcePath.startsWith("api/") || lastSegment.contains(".")) {
                        return null;
                    }
                    Resource index = location.createRelative("index.html");
                    return index.exists() ? index : null;
                }
            });
    }
}

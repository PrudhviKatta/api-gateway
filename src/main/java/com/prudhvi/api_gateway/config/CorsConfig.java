package com.prudhvi.api_gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configures CORS so the React frontend can call the gateway API.
 *
 * In dev, the Vite proxy makes API calls appear same-origin to the browser,
 * so CORS is not actually needed — but it's harmless and required in production
 * where the frontend is served from a separate domain (e.g. Railway).
 *
 * The allowed origin is read from an environment variable so Railway can inject
 * the production frontend URL without requiring a code change or rebuild.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    // Set CORS_ALLOWED_ORIGIN env var in production to the frontend Railway URL.
    // Falls back to localhost:5173 for local development.
    @Value("${cors.allowed-origin:http://localhost:5173}")
    private String allowedOrigin;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(allowedOrigin)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS");
    }
}

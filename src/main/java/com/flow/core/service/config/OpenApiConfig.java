package com.flow.core.service.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI/Swagger configuration for API documentation.
 */
@Configuration
public class OpenApiConfig {

    @Value("${server.port:8080}")
    private int serverPort;

    @Bean
    public OpenAPI flowCoreServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Flow Core Service API")
                        .description("Central service for flow graph ingestion, merging, and querying. " +
                                "Receives static graphs from Flow Adapter and runtime events from Flow Runtime Plugin.")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Flow Team")
                                .email("flow-team@example.com"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:" + serverPort)
                                .description("Local Development Server")
                ));
    }
}


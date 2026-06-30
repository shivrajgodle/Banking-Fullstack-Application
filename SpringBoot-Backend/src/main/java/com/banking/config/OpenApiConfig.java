package com.banking.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Configures the OpenAPI 3 / Swagger documentation that is auto-generated
 * for the REST API.
 *
 * The resulting interactive UI is available at:
 *   http://localhost:8080/swagger-ui/index.html
 *
 * Key setup:
 *  - Registers a "bearerAuth" security scheme so every protected endpoint
 *    shows a lock icon and the "Authorize" button in the UI lets testers
 *    paste their JWT and have it automatically included in all requests.
 *  - Lists both a local dev server and the production server URL so the
 *    UI can target either environment.
 *  - addSecurityItem applies the bearerAuth scheme globally, meaning all
 *    endpoints require it unless explicitly marked as public.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Banking Backend API")
                        .description("""
                                Production-grade Banking Software REST API.
                                
                                **Features:**
                                - JWT Authentication with Refresh Tokens
                                - Multi-type Bank Accounts (Savings, Current, NRI)
                                - Fund Transfers with OTP Verification
                                - Loan Management with EMI Calculation
                                - Fixed Deposits with Compound Interest
                                - Beneficiary Management
                                - Audit Logging
                                
                                **Authentication:** Use `/auth/login` to get a Bearer token, then click 'Authorize'.
                                
                                **Default Admin:** admin@banking.com / Admin@123
                                """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Banking Support")
                                .email("support@banking.com"))
                        .license(new License().name("MIT")))
                // Servers let the Swagger UI "Try it out" feature target the right host
                .servers(List.of(
                        new Server().url("http://localhost:8080/api/v1").description("Local Development"),
                        new Server().url("https://api.banking.com/api/v1").description("Production")
                ))
                // Make every endpoint require a Bearer JWT by default
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .components(new Components()
                        // Define the "bearerAuth" scheme that the lock icons reference
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .name("bearerAuth")
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Enter JWT token from /auth/login response")));
    }
}

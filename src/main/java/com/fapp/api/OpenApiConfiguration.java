package com.fapp.api;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class OpenApiConfiguration {

    @Bean
    OpenAPI openAPI() {
        return new OpenAPI()
                .info(apiInfo())
                .components(components())
                .addSecurityItem(new SecurityRequirement().addList("basicAuth"));
    }

    private Info apiInfo() {
        return new Info()
                .title("FAPP API")
                .version("0.0.1")
                .description("Financial Analysis & Planning Platform REST API")
                .contact(new Contact()
                        .name("FAPP Project")
                        .url("https://github.com/evgg12/FAPP"));
    }

    private Components components() {
        return new Components()
                .addSecuritySchemes("basicAuth",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("basic")
                                .description("HTTP Basic authentication with email and password"));
    }
}

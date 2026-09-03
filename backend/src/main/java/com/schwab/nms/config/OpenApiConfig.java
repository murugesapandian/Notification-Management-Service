package com.schwab.nms.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI notificationManagementOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Notification Management Service")
                .description("Receives alert requests and delivers notifications through configurable channels.")
                .version("v1")
                .contact(new Contact().name("Notification Platform Team")));
    }
}

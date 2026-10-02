package org.openapitools.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.security.SecurityScheme;

@Configuration
public class SpringDocConfiguration {

    @Bean(name = "org.openapitools.configuration.SpringDocConfiguration.apiInfo")
    OpenAPI apiInfo() {
        return new OpenAPI()
                .info(
                        new Info()
                                .title("OpenAPI user management")
                                .description("Small API to test OpenAPI usage")
                                .contact(
                                        new Contact()
                                                .email("team@openapitools.org")
                                )
                                .license(
                                        new License()
                                                .name("All rights reserved")
                                                .url("http://apache.org/licenses/LICENSE-2.0.html")
                                )
                                .version("1.0.0")
                )
        ;
    }
}
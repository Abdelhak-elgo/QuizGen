package ma.quizgen.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI quizGenOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("QuizGen API")
                .description("API REST pour la génération automatique de quiz pédagogiques. " +
                    "Authentification via Keycloak — obtenez un token sur " +
                    "http://localhost:8180/realms/quizgen/protocol/openid-connect/token")
                .version("1.0.0"))
            .components(new Components()
                .addSecuritySchemes("bearerAuth",
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Token JWT Keycloak (Resource Owner Password Grant ou Authorization Code)")));
    }
}

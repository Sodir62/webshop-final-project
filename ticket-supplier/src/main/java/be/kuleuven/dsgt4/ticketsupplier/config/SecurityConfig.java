package be.kuleuven.dsgt4.ticketsupplier.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

/*
   B2B API security. Under the 'auth0' profile every request must carry the broker's M2M
   JWT (issuer + audience validated; settings in application-auth0.properties). The default
   profile keeps the API open as it was before Auth0 -- with spring-security now on the
   classpath, Boot would otherwise lock everything behind a generated password. CSRF is
   off in both chains: this is a token/JSON API for the broker, not a browser form target.
*/
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    @Profile("auth0")
    SecurityFilterChain jwtFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}))
                .csrf(csrf -> csrf.disable());
        return http.build();
    }

    @Bean
    @Profile("!auth0")
    SecurityFilterChain openFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .csrf(csrf -> csrf.disable());
        return http.build();
    }

    // Fetches the issuer's metadata over HTTP when the bean is created, so it must only
    // exist when auth0 is on (otherwise startup needs a reachable Auth0 tenant).
    @Bean
    @Profile("auth0")
    JwtDecoder jwtDecoder(
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuerUri,
            @Value("${auth0.api.audience}") String audience) {
        NimbusJwtDecoder decoder = JwtDecoders.fromIssuerLocation(issuerUri);
        OAuth2TokenValidator<Jwt> audienceValidator = token ->
                token.getAudience().contains(audience)
                        ? OAuth2TokenValidatorResult.success()
                        : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_audience", "Missing required audience", null));
        OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer(issuerUri);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(withIssuer, audienceValidator));
        return decoder;
    }
}

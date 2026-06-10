package be.kuleuven.dsgt4.broker.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

import static org.springframework.security.config.Customizer.withDefaults;

/*
   Basic-level security (the default; the 'auth0' profile swaps in Auth0SecurityConfig).
   Only /manager/** is protected; customers shop with no login. The manager authenticates
   with HTTP Basic against a single in-memory MANAGER user whose credentials come from
   application.properties (not hardcoded). CSRF stays enabled -- the Thymeleaf order form
   (th:action) gets a token automatically, so the browser flow is unaffected.
*/
@Configuration
@EnableWebSecurity
@Profile("!auth0")
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/manager/**").hasRole("MANAGER")
                        .anyRequest().permitAll())
                .httpBasic(withDefaults());
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    InMemoryUserDetailsManager userDetailsService(
            PasswordEncoder encoder,
            @Value("${app.manager.username}") String username,
            @Value("${app.manager.password}") String password) {
        UserDetails manager = User.builder()
                .username(username)
                .password(encoder.encode(password))
                .roles("MANAGER")
                .build();
        return new InMemoryUserDetailsManager(manager);
    }
}

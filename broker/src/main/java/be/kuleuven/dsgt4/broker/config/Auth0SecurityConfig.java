package be.kuleuven.dsgt4.broker.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.springframework.security.config.Customizer.withDefaults;

/*
   Level-2 security ('auth0' profile): customers and managers log in through Auth0 (OIDC).
   Roles come from a custom claim on the Auth0 token; MANAGER still guards /manager/**.
   Its settings live in application-auth0.properties (env-var driven, nothing committed).
   The default profile keeps the basic-level HTTP Basic setup in SecurityConfig instead.
*/
@Configuration
@EnableWebSecurity
@Profile("auth0")
public class Auth0SecurityConfig {

    @Value("${spring.security.oauth2.client.provider.auth0.issuer-uri}")
    private String issuerUri;

    @Value("${spring.security.oauth2.client.registration.auth0.client-id}")
    private String clientId;

    @Value("${auth0.claims.namespace}")
    private String claimsNamespace;

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/style.css", "/favicon.svg", "/error").permitAll()
                        .requestMatchers("/manager/**").hasRole("MANAGER")
                        .anyRequest().authenticated())
                .oauth2Login(withDefaults())
                .logout(logout -> logout
                        .logoutSuccessHandler(auth0LogoutHandler()));
        return http.build();
    }

    @Bean
    public OAuth2UserService<OidcUserRequest, OidcUser> oidcUserService() {
        final OidcUserService delegate = new OidcUserService();
        return userRequest -> {
            OidcUser oidcUser = delegate.loadUser(userRequest);
            Set<GrantedAuthority> authorities = new HashSet<>(oidcUser.getAuthorities());
            @SuppressWarnings("unchecked")
            List<String> roles = (List<String>) oidcUser.getClaims().get(claimsNamespace + "/roles");
            if (roles != null) {
                for (String role : roles) {
                    authorities.add(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()));
                }
            }
            return new DefaultOidcUser(authorities, oidcUser.getIdToken(), oidcUser.getUserInfo());
        };
    }

    private LogoutSuccessHandler auth0LogoutHandler() {
        return (request, response, authentication) -> {
            String returnTo = request.getScheme() + "://" + request.getServerName()
                    + ":" + request.getServerPort() + "/";
            String logoutUrl = issuerUri + "v2/logout?client_id=" + clientId
                    + "&returnTo=" + URLEncoder.encode(returnTo, StandardCharsets.UTF_8);
            response.sendRedirect(logoutUrl);
        };
    }
}

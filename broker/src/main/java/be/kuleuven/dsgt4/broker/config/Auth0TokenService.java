package be.kuleuven.dsgt4.broker.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.Map;

@Component
public class Auth0TokenService {

    private static final Logger log = LoggerFactory.getLogger(Auth0TokenService.class);

    private final RestClient tokenClient;
    private final String clientId;
    private final String clientSecret;
    private final String audience;

    private volatile String cachedToken;
    private volatile Instant tokenExpiry = Instant.MIN;

    public Auth0TokenService(
            @Value("${auth0.m2m.token-uri}") String tokenUri,
            @Value("${auth0.m2m.client-id}") String clientId,
            @Value("${auth0.m2m.client-secret}") String clientSecret,
            @Value("${auth0.m2m.audience}") String audience) {
        this.tokenClient = RestClient.builder().baseUrl(tokenUri).build();
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.audience = audience;
    }

    public synchronized String getAccessToken() {
        if (Instant.now().isBefore(tokenExpiry.minusSeconds(30))) {
            return cachedToken;
        }
        Map<String, String> body = Map.of(
                "grant_type", "client_credentials",
                "client_id", clientId,
                "client_secret", clientSecret,
                "audience", audience
        );
        TokenResponse response = tokenClient.post()
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(TokenResponse.class);
        if (response == null || response.access_token() == null) {
            throw new IllegalStateException("Auth0 M2M token request returned no token");
        }
        cachedToken = response.access_token();
        tokenExpiry = Instant.now().plusSeconds(response.expires_in());
        log.info("obtained M2M access token (expires in {}s)", response.expires_in());
        return cachedToken;
    }

    private record TokenResponse(String access_token, int expires_in, String token_type) {}
}

package be.kuleuven.dsgt4.broker.supplier;

import be.kuleuven.dsgt4.broker.config.Auth0TokenService;
import be.kuleuven.dsgt4.broker.data.SupplierType;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/*
   Talks to a real supplier service over HTTP, implementing the contract documented on
   SupplierClient:

     GET    /products?type={TYPE}            -> [{id,name,description,price,stock}]
     POST   /reservations  {productId,qty}   -> {reservationId}
     POST   /reservations/{id}/confirm
     DELETE /reservations/{id}

   Any transport failure or non-2xx response is surfaced as a SupplierException, so the
   broker's two-phase commit aborts and compensates exactly as it does for the in-process
   stub. This is the real-service replacement the stub's comments anticipated.
*/
public class HttpSupplierClient implements SupplierClient {

    private final SupplierType type;
    private final RestClient http;

    // tokenService is null outside the 'auth0' profile: then no bearer header is sent,
    // matching the suppliers' open (non-JWT) security chain.
    public HttpSupplierClient(SupplierType type, String baseUrl, Auth0TokenService tokenService) {
        this.type = type;
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(10));
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory);
        if (tokenService != null) {
            builder.requestInterceptor((request, body, execution) -> {
                try {
                    request.getHeaders().setBearerAuth(tokenService.getAccessToken());
                } catch (Exception e) {
                    // Auth0 being down must look like any unreachable-supplier failure,
                    // not escape as a raw RuntimeException past the catches below.
                    throw new SupplierException("Auth0 M2M token for " + type + " failed: " + e.getMessage());
                }
                return execution.execute(request, body);
            });
        }
        this.http = builder.build();
    }

    @Override
    public SupplierType type() {
        return type;
    }

    @Override
    public List<Product> list() {
        try {
            Product[] products = http.get()
                    .uri(uri -> uri.path("/products").queryParam("type", type.name()).build())
                    .retrieve()
                    .body(Product[].class);
            return products == null ? List.of() : List.of(products);
        } catch (RestClientException e) {
            throw new SupplierException(reasonOf(e), type + " supplier list failed: " + e.getMessage());
        }
    }

    @Override
    public Optional<Product> find(String productId) {
        return list().stream().filter(p -> productId.equals(p.id())).findFirst();
    }

    @Override
    public String reserve(String productId, int quantity) {
        try {
            ReservationResponse response = http.post()
                    .uri("/reservations")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ReserveRequest(productId, quantity))
                    .retrieve()
                    .body(ReservationResponse.class);
            if (response == null || response.reservationId() == null) {
                throw new SupplierException("reserve at " + type + " returned no reservation id");
            }
            return response.reservationId();
        } catch (RestClientException e) {
            throw new SupplierException(reasonOf(e),
                    "reserve " + productId + " x" + quantity + " at " + type + " failed: " + e.getMessage());
        }
    }

    @Override
    public void confirm(String reservationId) {
        try {
            http.post().uri("/reservations/{id}/confirm", reservationId).retrieve().toBodilessEntity();
        } catch (RestClientException e) {
            throw new SupplierException(reasonOf(e), "confirm " + reservationId + " at " + type + " failed: " + e.getMessage());
        }
    }

    @Override
    public void cancel(String reservationId) {
        try {
            http.delete().uri("/reservations/{id}", reservationId).retrieve().toBodilessEntity();
        } catch (RestClientException e) {
            throw new SupplierException(reasonOf(e), "cancel " + reservationId + " at " + type + " failed: " + e.getMessage());
        }
    }

    // HTTP status -> failure class: a 4xx is the supplier answering "no" (permanent);
    // anything without a usable answer (transport error, timeout, 5xx) is retryable.
    private static SupplierException.Reason reasonOf(RestClientException e) {
        if (e instanceof RestClientResponseException response) {
            return switch (response.getStatusCode().value()) {
                case 404 -> SupplierException.Reason.NOT_FOUND;
                case 409 -> SupplierException.Reason.CONFLICT;
                case 400 -> SupplierException.Reason.INVALID_REQUEST;
                default -> SupplierException.Reason.UNAVAILABLE;
            };
        }
        return SupplierException.Reason.UNAVAILABLE;
    }

    // Wire shapes for the reservation endpoints; kept private since they are an HTTP detail.
    private record ReserveRequest(String productId, int quantity) {}

    private record ReservationResponse(String reservationId) {}
}
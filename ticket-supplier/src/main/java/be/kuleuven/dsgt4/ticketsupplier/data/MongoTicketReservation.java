package be.kuleuven.dsgt4.ticketsupplier.data;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.UUID;

/*
   MongoDB version of TicketReservation. Stored in the "ticket_reservations" collection.
*/
@Document(collection = "ticket_reservations")
public class MongoTicketReservation {

    @Id
    private String id = UUID.randomUUID().toString();
    private String productId;
    private int quantity;
    private ReservationStatus status = ReservationStatus.PENDING;
    private Instant createdAt = Instant.now();

    public MongoTicketReservation() {
    }

    public MongoTicketReservation(String productId, int quantity) {
        this.productId = productId;
        this.quantity = quantity;
    }

    public String getId() { return id; }
    public String getProductId() { return productId; }
    public int getQuantity() { return quantity; }
    public ReservationStatus getStatus() { return status; }
    public void setStatus(ReservationStatus status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
}

package be.kuleuven.dsgt4.ticketsupplier.data;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;

import java.time.Instant;
import java.util.UUID;

@Entity
public class TicketReservation {

    @Id
    @Column(length = 36)
    private String id = UUID.randomUUID().toString();

    @Column(nullable = false, length = 16)
    private String productId;

    @Column(nullable = false)
    private int quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ReservationStatus status = ReservationStatus.PENDING;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected TicketReservation() {
    }

    public TicketReservation(String productId, int quantity) {
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

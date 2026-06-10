package be.kuleuven.dsgt4.food_and_beverages.data;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;

import java.time.Instant;
import java.util.UUID;

/*
   A hold on some units of one product, stored in the supplier's own database.

   The id is the supplier-generated reservation handle returned to the broker; the broker
   stores it opaquely (its OrderItem.reservationId column is 36+ chars) and passes it back
   on confirm/cancel.
*/
@Entity
public class Reservation {

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

    // When the hold self-releases if never confirmed/cancelled; set by the service
    // from the supplier.reservation.ttl property.
    private Instant expiresAt;

    protected Reservation() {
    }

    public Reservation(String productId, int quantity, Instant expiresAt) {
        this.productId = productId;
        this.quantity = quantity;
        this.expiresAt = expiresAt;
    }

    public String getId() {
        return id;
    }

    public String getProductId() {
        return productId;
    }

    public int getQuantity() {
        return quantity;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public void setStatus(ReservationStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
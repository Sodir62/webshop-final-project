package be.kuleuven.dsgt4.ticketsupplier.service;

import be.kuleuven.dsgt4.ticketsupplier.data.TicketProduct;
import be.kuleuven.dsgt4.ticketsupplier.data.TicketProductRepository;
import be.kuleuven.dsgt4.ticketsupplier.data.TicketReservation;
import be.kuleuven.dsgt4.ticketsupplier.data.TicketReservationRepository;
import be.kuleuven.dsgt4.ticketsupplier.data.ReservationStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
@Profile("!mongo")
public class TicketSupplierService {
    private static final Logger log = LoggerFactory.getLogger(TicketSupplierService.class);
    private final TicketProductRepository products;
    private final TicketReservationRepository reservations;
    private final Duration reservationTtl;

    // The TTL is the safety net for holds whose broker died before confirm/cancel ever
    // arrived: ReservationCleanupTask releases them. It must stay well above the broker's
    // 15-minute order-completion window -- a hold may legitimately be confirmed that late,
    // and expiring earlier would void the hold of an already-committed order.
    public TicketSupplierService(TicketProductRepository products, TicketReservationRepository reservations,
                                 @Value("${supplier.reservation.ttl:60m}") Duration reservationTtl) {
        this.products = products;
        this.reservations = reservations;
        this.reservationTtl = reservationTtl;
    }
    @Transactional(readOnly = true)
    public List<TicketProduct> listProducts() {
        return products.findAll();
    }
    @Transactional
    public TicketReservation reserve(String productId, int quantity) {
        if (quantity <= 0) {
            throw new TicketSupplierException(TicketSupplierException.Reason.INVALID_REQUEST,
                    "quantity must be positive, was " + quantity);
        }
        TicketProduct product = products.findWithLockById(productId)
                .orElseThrow(() -> new TicketSupplierException(TicketSupplierException.Reason.NOT_FOUND,
                        "unknown product " + productId));
        if (product.getStock() < quantity) {
            throw new TicketSupplierException(TicketSupplierException.Reason.CONFLICT,
                    "out of stock for " + productId + " (have " + product.getStock() + ", need " + quantity + ")");
        }
        product.setStock(product.getStock() - quantity);
        TicketReservation reservation = reservations.save(
                new TicketReservation(productId, quantity, Instant.now().plus(reservationTtl)));
        log.info("reserved {} x{} -> {}", productId, quantity, reservation.getId());
        return reservation;
    }
    @Transactional
    public void confirm(String reservationId) {
        TicketReservation reservation = reservations.findWithLockById(reservationId)
                .orElseThrow(() -> new TicketSupplierException(TicketSupplierException.Reason.NOT_FOUND,
                        "unknown reservation " + reservationId));
        switch (reservation.getStatus()) {
            case PENDING -> {
                reservation.setStatus(ReservationStatus.CONFIRMED);
                log.info("confirmed {}", reservationId);
            }
            case CONFIRMED -> log.info("confirm {} ignored (already confirmed — idempotent)", reservationId);
            case CANCELLED -> throw new TicketSupplierException(TicketSupplierException.Reason.CONFLICT,
                    "reservation " + reservationId + " was cancelled and cannot be confirmed");
        }
    }
    @Transactional
    public void cancel(String reservationId) {
        TicketReservation reservation = reservations.findWithLockById(reservationId).orElse(null);
        if (reservation == null || reservation.getStatus() != ReservationStatus.PENDING) {
            return;
        }
        TicketProduct product = products.findWithLockById(reservation.getProductId())
                .orElseThrow(() -> new TicketSupplierException(TicketSupplierException.Reason.NOT_FOUND,
                        "unknown product " + reservation.getProductId()));
        product.setStock(product.getStock() + reservation.getQuantity());
        reservation.setStatus(ReservationStatus.CANCELLED);
        log.info("cancelled {} (released {} x{})", reservationId, reservation.getProductId(), reservation.getQuantity());
    }
}

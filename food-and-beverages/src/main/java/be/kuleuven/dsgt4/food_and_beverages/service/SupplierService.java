package be.kuleuven.dsgt4.food_and_beverages.service;

import be.kuleuven.dsgt4.food_and_beverages.data.Category;
import be.kuleuven.dsgt4.food_and_beverages.data.Product;
import be.kuleuven.dsgt4.food_and_beverages.data.ProductRepository;
import be.kuleuven.dsgt4.food_and_beverages.data.Reservation;
import be.kuleuven.dsgt4.food_and_beverages.data.ReservationRepository;
import be.kuleuven.dsgt4.food_and_beverages.data.ReservationStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/*
   The supplier side of the broker's two-phase commit. The broker holds stock with reserve(),
   then either confirm()s the hold into a sale or cancel()s it. confirm and cancel are
   idempotent so the broker can safely retry them (e.g. after it crashes and resumes).

   Stock is held at reserve time and only released by cancelling a still-held reservation;
   a confirmed sale is final.
*/
@Service
public class SupplierService {

    private static final Logger log = LoggerFactory.getLogger(SupplierService.class);

    private final ProductRepository products;
    private final ReservationRepository reservations;
    private final Duration reservationTtl;

    // The TTL is the safety net for holds whose broker died before confirm/cancel ever
    // arrived: ReservationCleanupTask releases them. It must stay well above the broker's
    // 15-minute order-completion window -- a hold may legitimately be confirmed that late,
    // and expiring earlier would void the hold of an already-committed order.
    public SupplierService(ProductRepository products, ReservationRepository reservations,
                           @Value("${supplier.reservation.ttl:60m}") Duration reservationTtl) {
        this.products = products;
        this.reservations = reservations;
        this.reservationTtl = reservationTtl;
    }

    // List the catalog. With no category the whole catalog is returned; with FOOD or DRINK
    // only that kind, so the broker's two suppliers each see just their own products.
    @Transactional(readOnly = true)
    public List<Product> listProducts(Category category) {
        return category == null ? products.findAll() : products.findByCategory(category);
    }

    // Hold `quantity` units of a product and return the new reservation. Rejects bad input,
    // unknown products, and insufficient stock. Stock is decremented now (the hold).
    @Transactional
    public Reservation reserve(String productId, int quantity) {
        if (quantity <= 0) {
            throw new SupplierException(SupplierException.Reason.INVALID_REQUEST,
                    "quantity must be positive, was " + quantity);
        }
        Product product = products.findWithLockById(productId)
                .orElseThrow(() -> new SupplierException(SupplierException.Reason.NOT_FOUND,
                        "unknown product " + productId));
        if (product.getStock() < quantity) {
            throw new SupplierException(SupplierException.Reason.CONFLICT,
                    "out of stock for " + productId + " (have " + product.getStock() + ", need " + quantity + ")");
        }
        product.setStock(product.getStock() - quantity);   // hold the stock now
        Reservation reservation = reservations.save(
                new Reservation(productId, quantity, Instant.now().plus(reservationTtl)));
        log.info("reserved {} x{} -> {}", productId, quantity, reservation.getId());
        return reservation;
    }

    // Turn a hold into a permanent sale. Idempotent: a second confirm is a no-op. The stock
    // was already taken at reserve time, so confirming never moves stock.
    @Transactional
    public void confirm(String reservationId) {
        Reservation reservation = reservations.findWithLockById(reservationId)
                .orElseThrow(() -> new SupplierException(SupplierException.Reason.NOT_FOUND,
                        "unknown reservation " + reservationId));
        switch (reservation.getStatus()) {
            case PENDING -> {
                reservation.setStatus(ReservationStatus.CONFIRMED);
                log.info("confirmed {}", reservationId);
            }
            case CONFIRMED -> log.info("confirm {} ignored (already confirmed)", reservationId);
            case CANCELLED -> throw new SupplierException(SupplierException.Reason.CONFLICT,
                    "reservation " + reservationId + " was cancelled and cannot be confirmed");
        }
    }

    // Release a hold. Idempotent: cancelling an unknown or already-cancelled reservation is a
    // no-op, and a confirmed sale is final (its stock is not given back). A still-held hold's
    // stock is returned exactly once.
    @Transactional
    public void cancel(String reservationId) {
        Reservation reservation = reservations.findWithLockById(reservationId).orElse(null);
        if (reservation == null || reservation.getStatus() != ReservationStatus.PENDING) {
            return;
        }
        Product product = products.findWithLockById(reservation.getProductId())
                .orElseThrow(() -> new SupplierException(SupplierException.Reason.NOT_FOUND,
                        "unknown product " + reservation.getProductId()));
        product.setStock(product.getStock() + reservation.getQuantity());   // give the held units back
        reservation.setStatus(ReservationStatus.CANCELLED);
        log.info("cancelled {} (released {} x{})", reservationId, reservation.getProductId(), reservation.getQuantity());
    }
}
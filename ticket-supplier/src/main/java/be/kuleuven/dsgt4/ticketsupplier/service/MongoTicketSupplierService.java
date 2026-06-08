package be.kuleuven.dsgt4.ticketsupplier.service;

import be.kuleuven.dsgt4.ticketsupplier.data.MongoTicketProduct;
import be.kuleuven.dsgt4.ticketsupplier.data.MongoTicketProductRepository;
import be.kuleuven.dsgt4.ticketsupplier.data.MongoTicketReservation;
import be.kuleuven.dsgt4.ticketsupplier.data.MongoTicketReservationRepository;
import be.kuleuven.dsgt4.ticketsupplier.data.ReservationStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.List;

/*
   MongoDB implementation of the supplier service. Instead of pessimistic DB locks,
   uses MongoDB's atomic findAndModify operations to prevent overselling and
   double-restoring stock — the NoSQL equivalent of SELECT ... FOR UPDATE.

   Same 2PC invariants as the JPA version:
   1. confirm is idempotent
   2. cancel after confirm does NOT restore stock
   3. cancel is idempotent
   4. reserve is atomic (no overselling)
   5. cancel is atomic (no double-restore)
*/
@Service
@Profile("mongo")
public class MongoTicketSupplierService {

    private static final Logger log = LoggerFactory.getLogger(MongoTicketSupplierService.class);

    private final MongoTicketProductRepository products;
    private final MongoTicketReservationRepository reservations;
    private final MongoTemplate mongoTemplate;

    public MongoTicketSupplierService(MongoTicketProductRepository products,
                                       MongoTicketReservationRepository reservations,
                                       MongoTemplate mongoTemplate) {
        this.products = products;
        this.reservations = reservations;
        this.mongoTemplate = mongoTemplate;
    }

    public List<MongoTicketProduct> listProducts() {
        return products.findAll();
    }

    public MongoTicketReservation reserve(String productId, int quantity) {
        if (quantity <= 0) {
            throw new TicketSupplierException(TicketSupplierException.Reason.INVALID_REQUEST,
                    "quantity must be positive, was " + quantity);
        }

        // Atomically decrement stock only if sufficient — equivalent to pessimistic lock + check
        Query query = new Query(Criteria.where("_id").is(productId)
                .and("stock").gte(quantity));
        Update update = new Update().inc("stock", -quantity);
        MongoTicketProduct product = mongoTemplate.findAndModify(
                query, update, MongoTicketProduct.class);

        if (product == null) {
            // Either product doesn't exist or insufficient stock — check which
            boolean exists = products.existsById(productId);
            if (!exists) {
                throw new TicketSupplierException(TicketSupplierException.Reason.NOT_FOUND,
                        "unknown product " + productId);
            }
            throw new TicketSupplierException(TicketSupplierException.Reason.CONFLICT,
                    "out of stock for " + productId);
        }

        MongoTicketReservation reservation = reservations.save(
                new MongoTicketReservation(productId, quantity));
        log.info("reserved {} x{} -> {}", productId, quantity, reservation.getId());
        return reservation;
    }

    public void confirm(String reservationId) {
        MongoTicketReservation reservation = reservations.findById(reservationId)
                .orElseThrow(() -> new TicketSupplierException(TicketSupplierException.Reason.NOT_FOUND,
                        "unknown reservation " + reservationId));
        switch (reservation.getStatus()) {
            case PENDING -> {
                reservation.setStatus(ReservationStatus.CONFIRMED);
                reservations.save(reservation);
                log.info("confirmed {}", reservationId);
            }
            case CONFIRMED -> log.info("confirm {} ignored (already confirmed — idempotent)", reservationId);
            case CANCELLED -> throw new TicketSupplierException(TicketSupplierException.Reason.CONFLICT,
                    "reservation " + reservationId + " was cancelled and cannot be confirmed");
        }
    }

    public void cancel(String reservationId) {
        MongoTicketReservation reservation = reservations.findById(reservationId).orElse(null);
        if (reservation == null || reservation.getStatus() != ReservationStatus.PENDING) {
            return;
        }

        // Atomically restore stock
        Query query = new Query(Criteria.where("_id").is(reservation.getProductId()));
        Update update = new Update().inc("stock", reservation.getQuantity());
        mongoTemplate.findAndModify(query, update, MongoTicketProduct.class);

        reservation.setStatus(ReservationStatus.CANCELLED);
        reservations.save(reservation);
        log.info("cancelled {} (released {} x{})", reservationId,
                reservation.getProductId(), reservation.getQuantity());
    }
}

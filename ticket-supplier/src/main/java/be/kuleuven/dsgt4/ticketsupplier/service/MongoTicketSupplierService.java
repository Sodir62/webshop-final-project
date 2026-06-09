package be.kuleuven.dsgt4.ticketsupplier.service;

import be.kuleuven.dsgt4.ticketsupplier.data.MongoTicketProduct;
import be.kuleuven.dsgt4.ticketsupplier.data.MongoTicketProductRepository;
import be.kuleuven.dsgt4.ticketsupplier.data.MongoTicketReservation;
import be.kuleuven.dsgt4.ticketsupplier.data.MongoTicketReservationRepository;
import be.kuleuven.dsgt4.ticketsupplier.data.ReservationStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.List;

/*
   MongoDB implementation of the supplier service. With no pessimistic row locks available, every
   state change is an ATOMIC, CONDITIONAL findAndModify (a compare-and-set):

     - reserve : decrement stock only if `stock >= qty`            -> no overselling
     - confirm : flip status only if it is still PENDING           -> idempotent; CANCELLED -> conflict
     - cancel  : flip status only if it is still PENDING, and only -> idempotent; restores stock once
                 the caller that WINS that flip restores the stock

   Because each transition is gated on the current state, concurrent or duplicate calls can never
   double-restore stock or oversell: the loser's findAndModify simply matches nothing and no-ops.

   Caveat vs the JPA stack (which is fully atomic via @Transactional): reserve and cancel each touch
   TWO documents (the product and the reservation), and MongoDB gives only single-document atomicity
   here. A crash BETWEEN the two writes can leave a held unit unrestored (reserve: a decrement with no
   reservation; cancel: a CANCELLED reservation whose stock wasn't given back). That is the SAFE
   direction — it can never oversell or double-restore — but it is not the full cross-document
   atomicity of the JPA version. Closing that last gap would require MongoDB multi-document
   transactions, which need the server to run as a replica set.
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
        // Atomic conditional decrement: matches (and decrements) only if enough stock remains.
        Query query = new Query(Criteria.where("_id").is(productId).and("stock").gte(quantity));
        Update update = new Update().inc("stock", -quantity);
        MongoTicketProduct product = mongoTemplate.findAndModify(query, update, MongoTicketProduct.class);
        if (product == null) {
            // No match: either the product doesn't exist, or it's out of stock.
            if (!products.existsById(productId)) {
                throw new TicketSupplierException(TicketSupplierException.Reason.NOT_FOUND,
                        "unknown product " + productId);
            }
            throw new TicketSupplierException(TicketSupplierException.Reason.CONFLICT,
                    "out of stock for " + productId);
        }
        MongoTicketReservation reservation = reservations.save(new MongoTicketReservation(productId, quantity));
        log.info("reserved {} x{} -> {}", productId, quantity, reservation.getId());
        return reservation;
    }

    public void confirm(String reservationId) {
        // Atomically claim the PENDING -> CONFIRMED transition. The single winner gets the
        // (pre-update) document back; everyone else gets null and must inspect why.
        MongoTicketReservation reservation = mongoTemplate.findAndModify(
                new Query(Criteria.where("_id").is(reservationId).and("status").is(ReservationStatus.PENDING)),
                new Update().set("status", ReservationStatus.CONFIRMED),
                MongoTicketReservation.class);
        if (reservation != null) {
            log.info("confirmed {}", reservationId);
            return;
        }
        // Not PENDING: unknown, already CONFIRMED (idempotent no-op), or CANCELLED (a conflict).
        MongoTicketReservation existing = reservations.findById(reservationId)
                .orElseThrow(() -> new TicketSupplierException(TicketSupplierException.Reason.NOT_FOUND,
                        "unknown reservation " + reservationId));
        switch (existing.getStatus()) {
            case CONFIRMED -> log.info("confirm {} ignored (already confirmed — idempotent)", reservationId);
            case CANCELLED -> throw new TicketSupplierException(TicketSupplierException.Reason.CONFLICT,
                    "reservation " + reservationId + " was cancelled and cannot be confirmed");
            case PENDING -> log.info("confirm {} ignored (won concurrently by another caller)", reservationId);
        }
    }

    public void cancel(String reservationId) {
        // Atomically claim the PENDING -> CANCELLED transition; only the winner restores stock, so
        // concurrent/duplicate cancels can never double-restore. Unknown or already CONFIRMED/
        // CANCELLED reservations match nothing and are a safe no-op.
        MongoTicketReservation reservation = mongoTemplate.findAndModify(
                new Query(Criteria.where("_id").is(reservationId).and("status").is(ReservationStatus.PENDING)),
                new Update().set("status", ReservationStatus.CANCELLED),
                MongoTicketReservation.class);
        if (reservation == null) {
            return;
        }
        // We won the cancel: give the held units back, exactly once.
        Query stockQuery = new Query(Criteria.where("_id").is(reservation.getProductId()));
        Update stockUpdate = new Update().inc("stock", reservation.getQuantity());
        mongoTemplate.findAndModify(stockQuery, stockUpdate, MongoTicketProduct.class);
        log.info("cancelled {} (released {} x{})", reservationId, reservation.getProductId(), reservation.getQuantity());
    }
}

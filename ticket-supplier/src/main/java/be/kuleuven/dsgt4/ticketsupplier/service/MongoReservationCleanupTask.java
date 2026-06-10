package be.kuleuven.dsgt4.ticketsupplier.service;

import be.kuleuven.dsgt4.ticketsupplier.data.MongoTicketReservation;
import be.kuleuven.dsgt4.ticketsupplier.data.MongoTicketReservationRepository;
import be.kuleuven.dsgt4.ticketsupplier.data.ReservationStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
@Profile("mongo")
public class MongoReservationCleanupTask {

    private static final Logger log = LoggerFactory.getLogger(MongoReservationCleanupTask.class);

    private final MongoTicketSupplierService supplier;
    private final MongoTicketReservationRepository reservations;

    public MongoReservationCleanupTask(MongoTicketSupplierService supplier,
                                       MongoTicketReservationRepository reservations) {
        this.supplier = supplier;
        this.reservations = reservations;
    }

    @Scheduled(fixedRate = 60_000)
    public void cancelExpiredReservations() {
        List<MongoTicketReservation> expired = reservations.findByStatusAndExpiresAtBefore(
                ReservationStatus.PENDING, Instant.now());
        for (MongoTicketReservation r : expired) {
            try {
                supplier.cancel(r.getId());
                log.info("TTL expired: cancelled reservation {} for product {}", r.getId(), r.getProductId());
            } catch (Exception e) {
                log.error("TTL cleanup failed for reservation {}: {}", r.getId(), e.getMessage());
            }
        }
    }
}

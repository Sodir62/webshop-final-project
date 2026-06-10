package be.kuleuven.dsgt4.ticketsupplier.service;

import be.kuleuven.dsgt4.ticketsupplier.data.ReservationStatus;
import be.kuleuven.dsgt4.ticketsupplier.data.TicketReservation;
import be.kuleuven.dsgt4.ticketsupplier.data.TicketReservationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
@Profile("!mongo")
public class ReservationCleanupTask {

    private static final Logger log = LoggerFactory.getLogger(ReservationCleanupTask.class);

    private final TicketSupplierService supplier;
    private final TicketReservationRepository reservations;

    public ReservationCleanupTask(TicketSupplierService supplier, TicketReservationRepository reservations) {
        this.supplier = supplier;
        this.reservations = reservations;
    }

    @Scheduled(fixedRate = 60_000)
    public void cancelExpiredReservations() {
        List<TicketReservation> expired = reservations.findByStatusAndExpiresAtBefore(
                ReservationStatus.PENDING, Instant.now());
        for (TicketReservation r : expired) {
            try {
                supplier.cancel(r.getId());
                log.info("TTL expired: cancelled reservation {} for product {}", r.getId(), r.getProductId());
            } catch (Exception e) {
                log.error("TTL cleanup failed for reservation {}: {}", r.getId(), e.getMessage());
            }
        }
    }
}

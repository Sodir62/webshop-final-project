package be.kuleuven.dsgt4.food_and_beverages.service;

import be.kuleuven.dsgt4.food_and_beverages.data.Reservation;
import be.kuleuven.dsgt4.food_and_beverages.data.ReservationRepository;
import be.kuleuven.dsgt4.food_and_beverages.data.ReservationStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class ReservationCleanupTask {

    private static final Logger log = LoggerFactory.getLogger(ReservationCleanupTask.class);

    private final SupplierService supplier;
    private final ReservationRepository reservations;

    public ReservationCleanupTask(SupplierService supplier, ReservationRepository reservations) {
        this.supplier = supplier;
        this.reservations = reservations;
    }

    @Scheduled(fixedRate = 60_000)
    public void cancelExpiredReservations() {
        List<Reservation> expired = reservations.findByStatusAndExpiresAtBefore(
                ReservationStatus.PENDING, Instant.now());
        for (Reservation r : expired) {
            try {
                supplier.cancel(r.getId());
                log.info("TTL expired: cancelled reservation {} for product {}", r.getId(), r.getProductId());
            } catch (Exception e) {
                log.error("TTL cleanup failed for reservation {}: {}", r.getId(), e.getMessage());
            }
        }
    }
}

package be.kuleuven.dsgt4.ticketsupplier.data;

import jakarta.persistence.LockModeType;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Profile("!mongo")
public interface TicketReservationRepository extends JpaRepository<TicketReservation, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<TicketReservation> findWithLockById(String id);

    List<TicketReservation> findByStatusAndExpiresAtBefore(ReservationStatus status, Instant cutoff);
}

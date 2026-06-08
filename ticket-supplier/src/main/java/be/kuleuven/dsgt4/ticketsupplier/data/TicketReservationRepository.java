package be.kuleuven.dsgt4.ticketsupplier.data;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;
import org.springframework.context.annotation.Profile;

@Profile("!mongo")
public interface TicketReservationRepository extends JpaRepository<TicketReservation, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<TicketReservation> findWithLockById(String id);
}

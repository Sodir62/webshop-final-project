package be.kuleuven.dsgt4.food_and_beverages.data;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/*
    Data access for reservations.
*/
public interface ReservationRepository extends JpaRepository<Reservation, String> {

    // confirm/cancel take a write lock on the reservation row so concurrent duplicate calls
    // serialise: the second one sees the already-final status and is a safe no-op, instead of
    // racing (e.g. a cancel double-restoring stock, or a cancel undoing a confirmed sale).
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Reservation> findWithLockById(String id);

    List<Reservation> findByStatusAndExpiresAtBefore(ReservationStatus status, Instant cutoff);
}
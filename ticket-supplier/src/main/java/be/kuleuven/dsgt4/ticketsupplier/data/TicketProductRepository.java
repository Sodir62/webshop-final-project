package be.kuleuven.dsgt4.ticketsupplier.data;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface TicketProductRepository extends JpaRepository<TicketProduct, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<TicketProduct> findWithLockById(String id);
}

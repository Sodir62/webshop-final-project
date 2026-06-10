package be.kuleuven.dsgt4.ticketsupplier.data;

import org.springframework.context.annotation.Profile;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;

@Profile("mongo")
public interface MongoTicketReservationRepository extends MongoRepository<MongoTicketReservation, String> {

    List<MongoTicketReservation> findByStatusAndExpiresAtBefore(ReservationStatus status, Instant cutoff);
}

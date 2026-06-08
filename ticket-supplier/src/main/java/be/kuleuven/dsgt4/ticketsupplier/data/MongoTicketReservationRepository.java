package be.kuleuven.dsgt4.ticketsupplier.data;

import org.springframework.context.annotation.Profile;
import org.springframework.data.mongodb.repository.MongoRepository;

/*
   MongoDB repository for ticket reservations.
   Only active when running with the "mongo" profile.
*/
@Profile("mongo")
public interface MongoTicketReservationRepository extends MongoRepository<MongoTicketReservation, String> {
}

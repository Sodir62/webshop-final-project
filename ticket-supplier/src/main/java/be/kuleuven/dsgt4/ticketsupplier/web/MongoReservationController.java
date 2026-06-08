package be.kuleuven.dsgt4.ticketsupplier.web;

import be.kuleuven.dsgt4.ticketsupplier.data.MongoTicketReservation;
import be.kuleuven.dsgt4.ticketsupplier.service.MongoTicketSupplierService;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/*
   MongoDB version of ReservationController.
   Only active when running with the "mongo" profile.
*/
@RestController
@Profile("mongo")
@RequestMapping("/reservations")
public class MongoReservationController {

    private final MongoTicketSupplierService supplier;

    public MongoReservationController(MongoTicketSupplierService supplier) {
        this.supplier = supplier;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReservationResponse reserve(@RequestBody ReserveRequest request) {
        MongoTicketReservation reservation = supplier.reserve(request.productId(), request.quantity());
        return new ReservationResponse(reservation.getId());
    }

    @PostMapping("/{id}/confirm")
    public void confirm(@PathVariable String id) {
        supplier.confirm(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable String id) {
        supplier.cancel(id);
    }
}

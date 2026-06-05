package be.kuleuven.dsgt4.ticketsupplier.web;

import be.kuleuven.dsgt4.ticketsupplier.data.TicketReservation;
import be.kuleuven.dsgt4.ticketsupplier.service.TicketSupplierService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/reservations")
public class ReservationController {

    private final TicketSupplierService supplier;

    public ReservationController(TicketSupplierService supplier) {
        this.supplier = supplier;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReservationResponse reserve(@RequestBody ReserveRequest request) {
        TicketReservation reservation = supplier.reserve(request.productId(), request.quantity());
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

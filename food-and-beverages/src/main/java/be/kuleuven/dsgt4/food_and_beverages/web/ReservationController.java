package be.kuleuven.dsgt4.food_and_beverages.web;

import be.kuleuven.dsgt4.food_and_beverages.data.Reservation;
import be.kuleuven.dsgt4.food_and_beverages.service.SupplierService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/*
   The reservation side of the broker's two-phase commit:

     POST   /reservations            -> hold stock, return a reservation id   (201)
     POST   /reservations/{id}/confirm -> turn the hold into a sale            (200, idempotent)
     DELETE /reservations/{id}       -> release the hold                       (204, idempotent)
*/
@RestController
@RequestMapping("/reservations")
public class ReservationController {

    private final SupplierService supplier;

    public ReservationController(SupplierService supplier) {
        this.supplier = supplier;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReservationResponse reserve(@RequestBody ReserveRequest request) {
        Reservation reservation = supplier.reserve(request.productId(), request.quantity());
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
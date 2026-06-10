package be.kuleuven.dsgt4.food_and_beverages.service;

import be.kuleuven.dsgt4.food_and_beverages.data.Product;
import be.kuleuven.dsgt4.food_and_beverages.data.ProductRepository;
import be.kuleuven.dsgt4.food_and_beverages.data.Reservation;
import be.kuleuven.dsgt4.food_and_beverages.data.ReservationRepository;
import be.kuleuven.dsgt4.food_and_beverages.data.ReservationStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/*
   Needs the local foodbevdb, like the rest of this suite. ttl=0s: every hold is born
   expired, so one cleanup pass deterministically exercises the TTL backstop (the safety
   net for holds whose broker died before confirm/cancel ever arrived).
*/
@SpringBootTest(properties = "supplier.reservation.ttl=0s")
@Transactional
class ReservationCleanupTaskTests {

    @Autowired private SupplierService supplier;
    @Autowired private ReservationCleanupTask cleanup;
    @Autowired private ProductRepository products;
    @Autowired private ReservationRepository reservations;

    @Test
    void expiredPendingHoldIsReleased() {
        cleanup.cancelExpiredReservations();   // flush stale leftovers so the delta below is ours
        Product product = products.findAll().get(0);
        int before = product.getStock();
        Reservation hold = supplier.reserve(product.getId(), 1);

        cleanup.cancelExpiredReservations();

        assertEquals(ReservationStatus.CANCELLED, reservations.findById(hold.getId()).orElseThrow().getStatus());
        assertEquals(before, products.findById(product.getId()).orElseThrow().getStock());
    }

    @Test
    void confirmedSaleIsNeverExpired() {
        cleanup.cancelExpiredReservations();
        Product product = products.findAll().get(0);
        int before = product.getStock();
        Reservation hold = supplier.reserve(product.getId(), 1);
        supplier.confirm(hold.getId());

        cleanup.cancelExpiredReservations();

        assertEquals(ReservationStatus.CONFIRMED, reservations.findById(hold.getId()).orElseThrow().getStatus());
        assertEquals(before - 1, products.findById(product.getId()).orElseThrow().getStock());
    }
}

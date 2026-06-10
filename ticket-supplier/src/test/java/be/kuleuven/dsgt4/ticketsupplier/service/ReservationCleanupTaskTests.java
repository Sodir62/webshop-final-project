package be.kuleuven.dsgt4.ticketsupplier.service;

import be.kuleuven.dsgt4.ticketsupplier.data.ReservationStatus;
import be.kuleuven.dsgt4.ticketsupplier.data.TicketProduct;
import be.kuleuven.dsgt4.ticketsupplier.data.TicketProductRepository;
import be.kuleuven.dsgt4.ticketsupplier.data.TicketReservation;
import be.kuleuven.dsgt4.ticketsupplier.data.TicketReservationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/*
   Needs the local ticketdb, like the rest of this suite (JPA stack; the mongo profile has
   its own cleanup task). ttl=0s: every hold is born expired, so one cleanup pass
   deterministically exercises the TTL backstop (the safety net for holds whose broker
   died before confirm/cancel ever arrived).
*/
@SpringBootTest(properties = "supplier.reservation.ttl=0s")
@Transactional
class ReservationCleanupTaskTests {

    @Autowired private TicketSupplierService supplier;
    @Autowired private ReservationCleanupTask cleanup;
    @Autowired private TicketProductRepository products;
    @Autowired private TicketReservationRepository reservations;

    @Test
    void expiredPendingHoldIsReleased() {
        cleanup.cancelExpiredReservations();   // flush stale leftovers so the delta below is ours
        TicketProduct product = products.findAll().get(0);
        int before = product.getStock();
        TicketReservation hold = supplier.reserve(product.getId(), 1);

        cleanup.cancelExpiredReservations();

        assertEquals(ReservationStatus.CANCELLED, reservations.findById(hold.getId()).orElseThrow().getStatus());
        assertEquals(before, products.findById(product.getId()).orElseThrow().getStock());
    }

    @Test
    void confirmedSaleIsNeverExpired() {
        cleanup.cancelExpiredReservations();
        TicketProduct product = products.findAll().get(0);
        int before = product.getStock();
        TicketReservation hold = supplier.reserve(product.getId(), 1);
        supplier.confirm(hold.getId());

        cleanup.cancelExpiredReservations();

        assertEquals(ReservationStatus.CONFIRMED, reservations.findById(hold.getId()).orElseThrow().getStatus());
        assertEquals(before - 1, products.findById(product.getId()).orElseThrow().getStock());
    }
}

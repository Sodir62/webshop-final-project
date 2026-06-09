package be.kuleuven.dsgt4.ticketsupplier.service;

import be.kuleuven.dsgt4.ticketsupplier.data.ReservationStatus;
import be.kuleuven.dsgt4.ticketsupplier.data.TicketProductRepository;
import be.kuleuven.dsgt4.ticketsupplier.data.TicketReservation;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/*
    The supplier side of the broker's 2PC for tickets. Same reserve/confirm/cancel contract as
    the food-and-beverages service: stock held at reserve, idempotent confirm/cancel, a confirmed
    sale is final. @Transactional rolls every test back; stock asserted relative to a fresh read.
*/
@SpringBootTest
@Transactional
class TicketSupplierServiceTests {

    @Autowired private TicketSupplierService supplier;
    @Autowired private TicketProductRepository products;

    private int stock(String productId) {
        return products.findById(productId).orElseThrow().getStock();
    }

    @Test
    void reserveHoldsStockAndCreatesPendingReservation() {
        int before = stock("T-001");
        TicketReservation r = supplier.reserve("T-001", 2);
        assertEquals(ReservationStatus.PENDING, r.getStatus());
        assertEquals(before - 2, stock("T-001"));
    }

    @Test
    void reserveOutOfStockThrowsConflictAndHoldsNothing() {
        int before = stock("T-002");   // Metallica, scarce
        TicketSupplierException e = assertThrows(TicketSupplierException.class,
                () -> supplier.reserve("T-002", before + 1));
        assertEquals(TicketSupplierException.Reason.CONFLICT, e.reason());
        assertEquals(before, stock("T-002"));
    }

    @Test
    void reserveUnknownProductThrowsNotFound() {
        TicketSupplierException e = assertThrows(TicketSupplierException.class,
                () -> supplier.reserve("NOPE", 1));
        assertEquals(TicketSupplierException.Reason.NOT_FOUND, e.reason());
    }

    @Test
    void reserveNonPositiveQuantityThrowsInvalidRequest() {
        TicketSupplierException e = assertThrows(TicketSupplierException.class,
                () -> supplier.reserve("T-001", 0));
        assertEquals(TicketSupplierException.Reason.INVALID_REQUEST, e.reason());
    }

    @Test
    void confirmIsIdempotentAndDoesNotMoveStock() {
        int before = stock("T-001");
        TicketReservation r = supplier.reserve("T-001", 2);
        supplier.confirm(r.getId());
        assertDoesNotThrow(() -> supplier.confirm(r.getId()));   // second confirm is a no-op
        assertEquals(before - 2, stock("T-001"));
    }

    @Test
    void confirmAfterCancelThrowsConflict() {
        TicketReservation r = supplier.reserve("T-001", 1);
        supplier.cancel(r.getId());
        TicketSupplierException e = assertThrows(TicketSupplierException.class,
                () -> supplier.confirm(r.getId()));
        assertEquals(TicketSupplierException.Reason.CONFLICT, e.reason());
    }

    @Test
    void cancelReleasesAHeldHoldExactlyOnce() {
        int before = stock("T-001");
        TicketReservation r = supplier.reserve("T-001", 2);     // before - 2
        supplier.cancel(r.getId());                              // restored: before
        assertDoesNotThrow(() -> supplier.cancel(r.getId()));   // second cancel is a no-op
        assertEquals(before, stock("T-001"));
    }

    @Test
    void cancelAfterConfirmDoesNotRestoreStock() {
        int before = stock("T-001");
        TicketReservation r = supplier.reserve("T-001", 2);
        supplier.confirm(r.getId());
        supplier.cancel(r.getId());                              // the sale is final
        assertEquals(before - 2, stock("T-001"));
    }
}

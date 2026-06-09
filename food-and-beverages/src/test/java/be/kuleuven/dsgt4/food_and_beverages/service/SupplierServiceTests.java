package be.kuleuven.dsgt4.food_and_beverages.service;

import be.kuleuven.dsgt4.food_and_beverages.data.Category;
import be.kuleuven.dsgt4.food_and_beverages.data.ProductRepository;
import be.kuleuven.dsgt4.food_and_beverages.data.Reservation;
import be.kuleuven.dsgt4.food_and_beverages.data.ReservationStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/*
    The supplier side of the broker's 2PC. Drives reserve/confirm/cancel against the real DB
    (the seeded catalog), checking stock holds, idempotency, and the status transitions the
    broker relies on. @Transactional rolls every test back, so stock is left untouched.
    Stock is asserted relative to a freshly-read baseline (the catalog is shared/persistent).
*/
@SpringBootTest
@Transactional
class SupplierServiceTests {

    @Autowired private SupplierService supplier;
    @Autowired private ProductRepository products;

    private int stock(String productId) {
        return products.findById(productId).orElseThrow().getStock();
    }

    @Test
    void reserveHoldsStockAndCreatesPendingReservation() {
        int before = stock("F-001");
        Reservation r = supplier.reserve("F-001", 3);
        assertEquals(ReservationStatus.PENDING, r.getStatus());
        assertEquals(before - 3, stock("F-001"));   // stock held at reserve time
    }

    @Test
    void reserveOutOfStockThrowsConflictAndHoldsNothing() {
        int before = stock("F-001");
        SupplierException e = assertThrows(SupplierException.class, () -> supplier.reserve("F-001", before + 1));
        assertEquals(SupplierException.Reason.CONFLICT, e.reason());
        assertEquals(before, stock("F-001"));
    }

    @Test
    void reserveUnknownProductThrowsNotFound() {
        SupplierException e = assertThrows(SupplierException.class, () -> supplier.reserve("NOPE", 1));
        assertEquals(SupplierException.Reason.NOT_FOUND, e.reason());
    }

    @Test
    void reserveNonPositiveQuantityThrowsInvalidRequest() {
        SupplierException e = assertThrows(SupplierException.class, () -> supplier.reserve("F-001", 0));
        assertEquals(SupplierException.Reason.INVALID_REQUEST, e.reason());
    }

    @Test
    void confirmIsIdempotentAndDoesNotMoveStock() {
        int before = stock("F-001");
        Reservation r = supplier.reserve("F-001", 2);
        supplier.confirm(r.getId());
        assertDoesNotThrow(() -> supplier.confirm(r.getId()));   // second confirm is a no-op
        assertEquals(before - 2, stock("F-001"));                // stock taken at reserve, unchanged by confirm
    }

    @Test
    void confirmAfterCancelThrowsConflict() {
        Reservation r = supplier.reserve("F-001", 2);
        supplier.cancel(r.getId());
        SupplierException e = assertThrows(SupplierException.class, () -> supplier.confirm(r.getId()));
        assertEquals(SupplierException.Reason.CONFLICT, e.reason());
    }

    @Test
    void cancelReleasesAHeldHoldExactlyOnce() {
        int before = stock("F-001");
        Reservation r = supplier.reserve("F-001", 2);       // before - 2
        supplier.cancel(r.getId());                          // restored: before
        assertDoesNotThrow(() -> supplier.cancel(r.getId())); // second cancel is a no-op
        assertEquals(before, stock("F-001"));                // restored once, not twice
    }

    @Test
    void cancelAfterConfirmDoesNotRestoreStock() {
        int before = stock("F-001");
        Reservation r = supplier.reserve("F-001", 2);
        supplier.confirm(r.getId());
        supplier.cancel(r.getId());                          // the sale is final
        assertEquals(before - 2, stock("F-001"));            // stock is NOT given back
    }

    @Test
    void listProductsFiltersByCategory() {
        assertTrue(supplier.listProducts(Category.FOOD).stream().allMatch(p -> p.getCategory() == Category.FOOD));
        assertTrue(supplier.listProducts(Category.DRINK).stream().allMatch(p -> p.getCategory() == Category.DRINK));
        int all = supplier.listProducts(null).size();
        int food = supplier.listProducts(Category.FOOD).size();
        int drink = supplier.listProducts(Category.DRINK).size();
        assertEquals(all, food + drink);   // no category leaks out of FOOD/DRINK
    }
}

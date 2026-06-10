package be.kuleuven.dsgt4.broker.data;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/*
    Persistence tests for CustomerOrder against the real MySQL test DB (Replace.NONE, not an
    H2 substitute), so the negative tests below exercise the actual column/null constraints.
*/
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CustomerOrderRepositoryTests {

    @Autowired
    private CustomerOrderRepository repository;

    @Test
    void savesAndLoadsOrder() {
        CustomerOrder order = new CustomerOrder("Diestsestraat 1, Leuven", "Alice Smith", "4242");
        repository.save(order);

        // Look up the order we just saved by its id, so other rows in the DB don't matter.
        CustomerOrder loaded = repository.findById(order.getId()).orElseThrow();

        assertEquals("Alice Smith", loaded.getCardholderName());
        assertEquals(OrderStatus.CREATED, loaded.getStatus());
    }

    @Test
    void savesOrderWithItemsAndComputesTotal() {
        CustomerOrder order = new CustomerOrder("Diestsestraat 1, Leuven", "Alice Smith", "4242");
        order.addItem(new OrderItem(SupplierType.TICKET, "T-001", "Coldplay @ Sportpaleis", new BigDecimal("85.00"), 2));
        order.addItem(new OrderItem(SupplierType.DRINK, "D-001", "Trappist beer", new BigDecimal("4.00"), 3));
        repository.save(order);   // one save persists the order AND both items (cascade)

        CustomerOrder loaded = repository.findById(order.getId()).orElseThrow();

        assertEquals(2, loaded.getItems().size());
        assertEquals(new BigDecimal("182.00"), loaded.total());   // 85.00*2 + 4.00*3
    }

    // The status CAS that makes an order single-executor: of concurrent claimers, exactly
    // one transition matches the row; everyone else updates nothing.
    @Test
    void statusTransitionIsASingleWinnerCas() {
        CustomerOrder order = new CustomerOrder("Some street 1", "Alice Smith", "4242");
        repository.saveAndFlush(order);

        assertEquals(1, repository.transitionStatus(order.getId(), OrderStatus.CREATED, OrderStatus.RESERVING));
        assertEquals(0, repository.transitionStatus(order.getId(), OrderStatus.CREATED, OrderStatus.RESERVING));
        assertEquals(OrderStatus.RESERVING, repository.findById(order.getId()).orElseThrow().getStatus());
    }

    // --- Negative tests: prove the database REJECTS bad data ----------------------

    @Test
    void rejectsOrderWithNullRequiredField() {
        // deliveryAddress is @Column(nullable = false) -> a null must be refused.
        CustomerOrder order = new CustomerOrder(null, "Alice Smith", "4242");
        assertThrows(DataIntegrityViolationException.class,
                () -> repository.saveAndFlush(order));
    }

    @Test
    void rejectsCardLast4LongerThanColumn() {
        // cardLast4 column is varchar(4); 5 chars must be refused, not truncated silently.
        CustomerOrder order = new CustomerOrder("Some street 1", "Alice Smith", "12345");
        assertThrows(DataIntegrityViolationException.class,
                () -> repository.saveAndFlush(order));
    }
}

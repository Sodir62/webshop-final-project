package be.kuleuven.dsgt4.broker.transaction;

import be.kuleuven.dsgt4.broker.data.CustomerOrder;
import be.kuleuven.dsgt4.broker.data.CustomerOrderRepository;
import be.kuleuven.dsgt4.broker.data.ItemStatus;
import be.kuleuven.dsgt4.broker.data.OrderItem;
import be.kuleuven.dsgt4.broker.data.OrderStatus;
import be.kuleuven.dsgt4.broker.data.SupplierType;
import be.kuleuven.dsgt4.broker.supplier.StubSupplierClient;
import be.kuleuven.dsgt4.broker.supplier.SupplierRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;


// "stub": run FOOD/DRINK as in-process stubs so the 2PC success/rollback paths can be driven
// (e.g. setDown) without a live supplier service.
// min-age 0: production recovery waits 5 minutes so it never races a live order; these
// tests sweep orders they created moments ago.
@SpringBootTest(properties = "broker.recovery.min-age=0s")
@ActiveProfiles("stub")
@Transactional
class AtomicOrderServiceTests {

    @Autowired private AtomicOrderService atomicOrder;
    @Autowired private CustomerOrderRepository orders;
    @Autowired private SupplierRegistry suppliers;

    private int stock(SupplierType type, String productId) {
        return suppliers.get(type).find(productId).orElseThrow().stock();
    }

    @Test
    void successConfirmsEveryItemAndConsumesStock() {
        int ticketsBefore = stock(SupplierType.TICKET, "T-001");
        int drinksBefore = stock(SupplierType.DRINK, "D-001");

        CustomerOrder order = new CustomerOrder("Street 1", "Alice", "4242");
        order.addItem(new OrderItem(SupplierType.TICKET, "T-001", "Coldplay", new BigDecimal("85.00"), 2));
        order.addItem(new OrderItem(SupplierType.DRINK, "D-001", "Trappist", new BigDecimal("4.00"), 3));
        orders.save(order);

        CustomerOrder result = atomicOrder.placeOrder(order.getId());

        assertEquals(OrderStatus.SUCCEEDED, result.getStatus());
        assertTrue(result.getItems().stream().allMatch(i -> i.getStatus() == ItemStatus.CONFIRMED));
        assertEquals(ticketsBefore - 2, stock(SupplierType.TICKET, "T-001"));
        assertEquals(drinksBefore - 3, stock(SupplierType.DRINK, "D-001"));
    }

    @Test
    void downedSupplierRollsBackAndRestoresStock() {
        int ticketsBefore = stock(SupplierType.TICKET, "T-001");

        // Drink supplier is offline: its reserve fails AFTER the ticket was already reserved.
        StubSupplierClient drink = (StubSupplierClient) suppliers.get(SupplierType.DRINK);
        drink.setDown(true);
        try {
            CustomerOrder order = new CustomerOrder("Street 1", "Alice", "4242");
            order.addItem(new OrderItem(SupplierType.TICKET, "T-001", "Coldplay", new BigDecimal("85.00"), 2));
            order.addItem(new OrderItem(SupplierType.DRINK, "D-001", "Trappist", new BigDecimal("4.00"), 3));
            orders.save(order);

            CustomerOrder result = atomicOrder.placeOrder(order.getId());

            assertEquals(OrderStatus.FAILED, result.getStatus());
            // the ticket hold was taken, then cancelled -> stock is back where it started
            assertEquals(ticketsBefore, stock(SupplierType.TICKET, "T-001"));
        } finally {
            drink.setDown(false);   // don't leak the simulated outage into other tests
        }
    }

    // F2 regression: once the commit decision is made (RESERVED), a confirm failure must roll
    // FORWARD, never back. A partially-confirmed order must keep its already-confirmed sale and
    // wait in CONFIRMING for recovery -- it must NOT be cancelled and marked FAILED.
    @Test
    void confirmPhaseFailureRollsForwardNotBackAndKeepsTheSale() {
        int ticketsBefore = stock(SupplierType.TICKET, "T-001");
        int drinksBefore = stock(SupplierType.DRINK, "D-001");

        StubSupplierClient drink = (StubSupplierClient) suppliers.get(SupplierType.DRINK);
        CustomerOrder order = new CustomerOrder("Street 1", "Alice", "4242");
        order.addItem(new OrderItem(SupplierType.TICKET, "T-001", "Coldplay", new BigDecimal("85.00"), 2));
        order.addItem(new OrderItem(SupplierType.DRINK, "D-001", "Trappist", new BigDecimal("4.00"), 3));
        orders.save(order);

        drink.setFailConfirm(true);   // both holds are taken; confirming the drink then fails
        try {
            CustomerOrder result = atomicOrder.placeOrder(order.getId());

            // committed order is NOT rolled back: it waits in CONFIRMING for recovery
            assertEquals(OrderStatus.CONFIRMING, result.getStatus());
            assertEquals(ItemStatus.CONFIRMED, itemFor(result, SupplierType.TICKET).getStatus()); // sale stands
            assertEquals(ItemStatus.RESERVED, itemFor(result, SupplierType.DRINK).getStatus());    // still held
            // nothing was given back: ticket sold (-2), drink still held (-3)
            assertEquals(ticketsBefore - 2, stock(SupplierType.TICKET, "T-001"));
            assertEquals(drinksBefore - 3, stock(SupplierType.DRINK, "D-001"));
        } finally {
            drink.setFailConfirm(false);
        }

        // with the drink healthy again, recovery rolls the order forward to a full success
        atomicOrder.recoverInterruptedOrders();
        CustomerOrder recovered = orders.findById(order.getId()).orElseThrow();
        assertEquals(OrderStatus.SUCCEEDED, recovered.getStatus());
        assertTrue(recovered.getItems().stream().allMatch(i -> i.getStatus() == ItemStatus.CONFIRMED));
    }

    @Test
    void outOfStockOnFirstReserveFailsWithNoHolds() {
        int available = stock(SupplierType.TICKET, "T-002");   // Metallica, scarce
        CustomerOrder order = new CustomerOrder("Street 1", "Alice", "4242");
        order.addItem(new OrderItem(SupplierType.TICKET, "T-002", "Metallica", new BigDecimal("120.00"), available + 1));
        orders.save(order);

        CustomerOrder result = atomicOrder.placeOrder(order.getId());

        assertEquals(OrderStatus.FAILED, result.getStatus());
        assertTrue(result.getItems().stream().allMatch(i -> i.getStatus() == ItemStatus.FAILED));
        assertEquals(available, stock(SupplierType.TICKET, "T-002"));   // nothing held
    }

    @Test
    void successAcrossAllThreeSupplierTypes() {
        int t = stock(SupplierType.TICKET, "T-003");
        int f = stock(SupplierType.FOOD, "F-001");
        int d = stock(SupplierType.DRINK, "D-002");

        CustomerOrder order = new CustomerOrder("Street 1", "Bob", "1111");
        order.addItem(new OrderItem(SupplierType.TICKET, "T-003", "Daft Punk", new BigDecimal("95.00"), 1));
        order.addItem(new OrderItem(SupplierType.FOOD, "F-001", "Nachos", new BigDecimal("6.50"), 2));
        order.addItem(new OrderItem(SupplierType.DRINK, "D-002", "Cola", new BigDecimal("2.50"), 4));
        orders.save(order);

        CustomerOrder result = atomicOrder.placeOrder(order.getId());

        assertEquals(OrderStatus.SUCCEEDED, result.getStatus());
        assertTrue(result.getItems().stream().allMatch(i -> i.getStatus() == ItemStatus.CONFIRMED));
        assertEquals(t - 1, stock(SupplierType.TICKET, "T-003"));
        assertEquals(f - 2, stock(SupplierType.FOOD, "F-001"));
        assertEquals(d - 4, stock(SupplierType.DRINK, "D-002"));
    }

    // Re-entrancy regression: a retrier calling placeOrder on an order that is already
    // mid-flight (here: stuck CONFIRMING) must be a no-op -- the CREATED->RESERVING claim
    // fails -- and must never reserve the items a second time.
    @Test
    void reEnteringAnInFlightOrderNeverReReserves() {
        StubSupplierClient drink = (StubSupplierClient) suppliers.get(SupplierType.DRINK);
        CustomerOrder order = new CustomerOrder("Street 1", "Alice", "4242");
        order.addItem(new OrderItem(SupplierType.TICKET, "T-001", "Coldplay", new BigDecimal("85.00"), 2));
        order.addItem(new OrderItem(SupplierType.DRINK, "D-001", "Trappist", new BigDecimal("4.00"), 3));
        orders.save(order);

        drink.setFailConfirm(true);
        try {
            atomicOrder.placeOrder(order.getId());   // ends CONFIRMING: ticket sold, drink held
            CustomerOrder inFlight = orders.findById(order.getId()).orElseThrow();
            String ticketHold = itemFor(inFlight, SupplierType.TICKET).getReservationId();
            int tickets = stock(SupplierType.TICKET, "T-001");
            int drinks = stock(SupplierType.DRINK, "D-001");

            CustomerOrder again = atomicOrder.placeOrder(order.getId());

            assertEquals(OrderStatus.CONFIRMING, again.getStatus());
            assertEquals(ticketHold, itemFor(again, SupplierType.TICKET).getReservationId());
            assertEquals(tickets, stock(SupplierType.TICKET, "T-001"));   // not reserved twice
            assertEquals(drinks, stock(SupplierType.DRINK, "D-001"));
        } finally {
            drink.setFailConfirm(false);
        }
    }

    // Background completion ('async'): a supplier that is merely unreachable must not fail
    // the order -- it is wound back to CREATED (holds released, items unheld) so the
    // listener can claim and run it again, and that retry then succeeds.
    @Test
    void transientReserveFailureResetsForRetryAndTheRetrySucceeds() {
        int ticketsBefore = stock(SupplierType.TICKET, "T-001");
        StubSupplierClient drink = (StubSupplierClient) suppliers.get(SupplierType.DRINK);
        CustomerOrder order = new CustomerOrder("Street 1", "Alice", "4242");
        order.addItem(new OrderItem(SupplierType.TICKET, "T-001", "Coldplay", new BigDecimal("85.00"), 2));
        order.addItem(new OrderItem(SupplierType.DRINK, "D-001", "Trappist", new BigDecimal("4.00"), 3));
        orders.save(order);

        drink.setDown(true);
        try {
            CustomerOrder result = atomicOrder.placeOrder(order.getId(), true);

            assertEquals(OrderStatus.CREATED, result.getStatus());
            assertTrue(result.getItems().stream().allMatch(i -> i.getStatus() == ItemStatus.PENDING));
            assertNull(itemFor(result, SupplierType.TICKET).getReservationId());
            assertEquals(ticketsBefore, stock(SupplierType.TICKET, "T-001"));   // nothing leaked
        } finally {
            drink.setDown(false);
        }

        CustomerOrder retried = atomicOrder.placeOrder(order.getId(), true);
        assertEquals(OrderStatus.SUCCEEDED, retried.getStatus());
        assertEquals(ticketsBefore - 2, stock(SupplierType.TICKET, "T-001"));
    }

    // A permanent refusal (out of stock) must fail immediately even on the retrying path:
    // retrying cannot change the supplier's answer.
    @Test
    void permanentReserveFailureFailsEvenWithRetryEnabled() {
        int available = stock(SupplierType.TICKET, "T-002");
        CustomerOrder order = new CustomerOrder("Street 1", "Alice", "4242");
        order.addItem(new OrderItem(SupplierType.TICKET, "T-002", "Metallica", new BigDecimal("120.00"), available + 1));
        orders.save(order);

        CustomerOrder result = atomicOrder.placeOrder(order.getId(), true);

        assertEquals(OrderStatus.FAILED, result.getStatus());
        assertEquals(available, stock(SupplierType.TICKET, "T-002"));
    }

    private OrderItem itemFor(CustomerOrder order, SupplierType type) {
        return order.getItems().stream()
                .filter(i -> i.getSupplierType() == type)
                .findFirst().orElseThrow();
    }
}

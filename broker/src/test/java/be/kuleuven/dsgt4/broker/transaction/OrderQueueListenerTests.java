package be.kuleuven.dsgt4.broker.transaction;

import be.kuleuven.dsgt4.broker.data.CustomerOrder;
import be.kuleuven.dsgt4.broker.data.CustomerOrderRepository;
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
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

/*
   Drives the queue listener's loop directly -- processOrder is plain Java; the 'async'
   profile only controls whether it is wired to a JMS queue. Listeners are constructed
   with tiny windows/backoffs so the deadline paths run instantly.

   completion-window 0s makes every order born past its window, which is exactly what the
   deadline and stale-sweep tests need; min-age 0s as in OrderRecoveryTests.
*/
@SpringBootTest(properties = {"broker.recovery.min-age=0s", "broker.order.completion-window=0s"})
@ActiveProfiles("stub")
@Transactional
class OrderQueueListenerTests {

    @Autowired private AtomicOrderService atomicOrder;
    @Autowired private CustomerOrderRepository orders;
    @Autowired private SupplierRegistry suppliers;

    private OrderQueueListener listener(Duration window) {
        return new OrderQueueListener(atomicOrder, orders, window, Duration.ofMillis(1));
    }

    private CustomerOrder savedOrder() {
        CustomerOrder order = new CustomerOrder("Street 1", "Alice", "4242");
        order.addItem(new OrderItem(SupplierType.TICKET, "T-001", "Coldplay", new BigDecimal("85.00"), 1));
        order.addItem(new OrderItem(SupplierType.DRINK, "D-001", "Trappist", new BigDecimal("4.00"), 2));
        orders.saveAndFlush(order);
        return order;
    }

    private int stock(SupplierType type, String productId) {
        return suppliers.get(type).find(productId).orElseThrow().stock();
    }

    @Test
    void completesAQueuedOrder() {
        CustomerOrder order = savedOrder();

        listener(Duration.ofMinutes(15)).processOrder(order.getId());

        assertEquals(OrderStatus.SUCCEEDED, orders.findById(order.getId()).orElseThrow().getStatus());
    }

    // THE deadline rule: an order past its commit point is never aborted, however late.
    // (Stamping FAILED over CONFIRMING here would discard the already-sold ticket.)
    @Test
    void deadlineNeverAbortsACommittedOrder() {
        StubSupplierClient drink = (StubSupplierClient) suppliers.get(SupplierType.DRINK);
        CustomerOrder order = savedOrder();
        drink.setFailConfirm(true);
        try {
            atomicOrder.placeOrder(order.getId());                  // ends CONFIRMING: ticket sold
            listener(Duration.ZERO).processOrder(order.getId());   // window already over
        } finally {
            drink.setFailConfirm(false);
        }

        assertEquals(OrderStatus.CONFIRMING, orders.findById(order.getId()).orElseThrow().getStatus());
    }

    // Before the commit point the deadline may abort: nothing is held, FAILED is safe.
    @Test
    void deadlineFailsAnOrderThatNeverGotProcessed() {
        int ticketsBefore = stock(SupplierType.TICKET, "T-001");
        StubSupplierClient drink = (StubSupplierClient) suppliers.get(SupplierType.DRINK);
        CustomerOrder order = savedOrder();
        drink.setDown(true);   // the attempt resets to CREATED (transient), then the window is over
        try {
            listener(Duration.ZERO).processOrder(order.getId());
        } finally {
            drink.setDown(false);
        }

        assertEquals(OrderStatus.FAILED, orders.findById(order.getId()).orElseThrow().getStatus());
        assertEquals(ticketsBefore, stock(SupplierType.TICKET, "T-001"));   // nothing leaked
    }

    // The recovery sweep's CREATED arm with the window already over: fail, never execute --
    // running a customer's order long after they were told it didn't go through is worse.
    @Test
    void sweepFailsAStaleNeverProcessedOrderPastItsWindow() {
        int ticketsBefore = stock(SupplierType.TICKET, "T-001");
        CustomerOrder order = savedOrder();

        atomicOrder.recoverInterruptedOrders();

        assertEquals(OrderStatus.FAILED, orders.findById(order.getId()).orElseThrow().getStatus());
        assertEquals(ticketsBefore, stock(SupplierType.TICKET, "T-001"));   // never executed
    }
}

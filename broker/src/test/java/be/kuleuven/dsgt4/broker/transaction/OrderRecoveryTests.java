package be.kuleuven.dsgt4.broker.transaction;

import be.kuleuven.dsgt4.broker.data.CustomerOrder;
import be.kuleuven.dsgt4.broker.data.CustomerOrderRepository;
import be.kuleuven.dsgt4.broker.data.ItemStatus;
import be.kuleuven.dsgt4.broker.data.OrderItem;
import be.kuleuven.dsgt4.broker.data.OrderStatus;
import be.kuleuven.dsgt4.broker.data.SupplierType;
import be.kuleuven.dsgt4.broker.supplier.SupplierRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

/*
    Simulates a broker crash mid-transaction by persisting an order in an interrupted
    state (with a real supplier hold already taken), then runs recovery and checks the
    2PC rule: a committed decision rolls forward, an undecided one rolls back.
*/
// "stub": in-process suppliers, so the test runs without live supplier services.
// min-age 0: production recovery waits 5 minutes so it never races a live order; these
// tests inject crash leftovers that are seconds old.
@SpringBootTest(properties = "broker.recovery.min-age=0s")
@ActiveProfiles("stub")
@Transactional
class OrderRecoveryTests {

    @Autowired private AtomicOrderService atomicOrder;
    @Autowired private CustomerOrderRepository orders;
    @Autowired private SupplierRegistry suppliers;

    private int stock(SupplierType type, String productId) {
        return suppliers.get(type).find(productId).orElseThrow().stock();
    }

    @Test
    void rollsForwardWhenCommitDecisionWasMade() {
        int before = stock(SupplierType.TICKET, "T-001");
        String resId = suppliers.get(SupplierType.TICKET).reserve("T-001", 2);   // hold exists, stock -2

        CustomerOrder order = new CustomerOrder("Street 1", "Alice", "4242");
        OrderItem item = new OrderItem(SupplierType.TICKET, "T-001", "Coldplay", new BigDecimal("85.00"), 2);
        order.addItem(item);
        item.setReservationId(resId);
        item.setStatus(ItemStatus.RESERVED);
        order.setStatus(OrderStatus.RESERVED);   // crashed after reserving, before confirming
        orders.saveAndFlush(order);

        atomicOrder.recoverInterruptedOrders();

        CustomerOrder recovered = orders.findById(order.getId()).orElseThrow();
        assertEquals(OrderStatus.SUCCEEDED, recovered.getStatus());
        assertEquals(ItemStatus.CONFIRMED, recovered.getItems().get(0).getStatus());
        assertEquals(before - 2, stock(SupplierType.TICKET, "T-001"));   // sale stands; confirm doesn't restore
    }

    @Test
    void rollsBackWhenNoCommitDecisionYet() {
        int before = stock(SupplierType.TICKET, "T-001");
        String resId = suppliers.get(SupplierType.TICKET).reserve("T-001", 2);   // one hold taken, stock -2

        CustomerOrder order = new CustomerOrder("Street 1", "Alice", "4242");
        OrderItem t = new OrderItem(SupplierType.TICKET, "T-001", "Coldplay", new BigDecimal("85.00"), 2);
        OrderItem d = new OrderItem(SupplierType.DRINK, "D-001", "Trappist", new BigDecimal("4.00"), 3);
        order.addItem(t);
        order.addItem(d);
        t.setReservationId(resId);
        t.setStatus(ItemStatus.RESERVED);
        // d stays PENDING: the broker crashed mid-reserve, before the drink
        order.setStatus(OrderStatus.RESERVING);
        orders.saveAndFlush(order);

        atomicOrder.recoverInterruptedOrders();

        CustomerOrder recovered = orders.findById(order.getId()).orElseThrow();
        assertEquals(OrderStatus.FAILED, recovered.getStatus());
        assertEquals(before, stock(SupplierType.TICKET, "T-001"));   // hold cancelled -> stock restored
    }

    // A confirm that is REFUSED (the hold vanished: TTL-expired or cancelled at the
    // supplier) can never succeed by retrying. With nothing sold yet, recovery aborts
    // cleanly -- externally invisible despite the commit decision.
    @Test
    void refusedConfirmWithNothingSoldAbortsCleanly() {
        int ticketsBefore = stock(SupplierType.TICKET, "T-001");
        int drinksBefore = stock(SupplierType.DRINK, "D-001");
        String ticketHold = suppliers.get(SupplierType.TICKET).reserve("T-001", 2);
        String drinkHold = suppliers.get(SupplierType.DRINK).reserve("D-001", 3);
        suppliers.get(SupplierType.TICKET).cancel(ticketHold);   // vanishes behind the broker's back

        CustomerOrder order = new CustomerOrder("Street 1", "Alice", "4242");
        OrderItem t = new OrderItem(SupplierType.TICKET, "T-001", "Coldplay", new BigDecimal("85.00"), 2);
        OrderItem d = new OrderItem(SupplierType.DRINK, "D-001", "Trappist", new BigDecimal("4.00"), 3);
        order.addItem(t);
        order.addItem(d);
        t.setReservationId(ticketHold);
        t.setStatus(ItemStatus.RESERVED);
        d.setReservationId(drinkHold);
        d.setStatus(ItemStatus.RESERVED);
        order.setStatus(OrderStatus.CONFIRMING);
        orders.saveAndFlush(order);

        atomicOrder.recoverInterruptedOrders();

        CustomerOrder recovered = orders.findById(order.getId()).orElseThrow();
        assertEquals(OrderStatus.FAILED, recovered.getStatus());
        assertEquals(ticketsBefore, stock(SupplierType.TICKET, "T-001"));   // given back at its cancel
        assertEquals(drinksBefore, stock(SupplierType.DRINK, "D-001"));     // released by the abort
    }

    // Same refusal, but part of the order is already a permanent sale: aborting would
    // discard a paid sale, retrying can never finish. Recovery must leave it CONFIRMING
    // for manual reconciliation -- and must not loop on it destructively.
    @Test
    void refusedConfirmAfterAPartialSaleLeavesConfirmingForReconciliation() {
        int ticketsBefore = stock(SupplierType.TICKET, "T-001");
        int drinksBefore = stock(SupplierType.DRINK, "D-001");
        String ticketHold = suppliers.get(SupplierType.TICKET).reserve("T-001", 2);
        suppliers.get(SupplierType.TICKET).confirm(ticketHold);   // already a permanent sale
        String drinkHold = suppliers.get(SupplierType.DRINK).reserve("D-001", 3);
        suppliers.get(SupplierType.DRINK).cancel(drinkHold);      // vanishes behind the broker's back

        CustomerOrder order = new CustomerOrder("Street 1", "Alice", "4242");
        OrderItem t = new OrderItem(SupplierType.TICKET, "T-001", "Coldplay", new BigDecimal("85.00"), 2);
        OrderItem d = new OrderItem(SupplierType.DRINK, "D-001", "Trappist", new BigDecimal("4.00"), 3);
        order.addItem(t);
        order.addItem(d);
        t.setReservationId(ticketHold);
        t.setStatus(ItemStatus.CONFIRMED);
        d.setReservationId(drinkHold);
        d.setStatus(ItemStatus.RESERVED);
        order.setStatus(OrderStatus.CONFIRMING);
        orders.saveAndFlush(order);

        atomicOrder.recoverInterruptedOrders();

        CustomerOrder recovered = orders.findById(order.getId()).orElseThrow();
        assertEquals(OrderStatus.CONFIRMING, recovered.getStatus());   // neither FAILED nor SUCCEEDED
        assertEquals(ticketsBefore - 2, stock(SupplierType.TICKET, "T-001"));   // the sale stands
        assertEquals(drinksBefore, stock(SupplierType.DRINK, "D-001"));         // restored at its cancel
    }

    @Test
    void rollsForwardFromConfirmingState() {
        int before = stock(SupplierType.TICKET, "T-001");
        String resId = suppliers.get(SupplierType.TICKET).reserve("T-001", 2);   // hold exists, stock -2

        CustomerOrder order = new CustomerOrder("Street 1", "Alice", "4242");
        OrderItem item = new OrderItem(SupplierType.TICKET, "T-001", "Coldplay", new BigDecimal("85.00"), 2);
        order.addItem(item);
        item.setReservationId(resId);
        item.setStatus(ItemStatus.RESERVED);
        order.setStatus(OrderStatus.CONFIRMING);   // crashed mid-confirm; commit decision already made
        orders.saveAndFlush(order);

        atomicOrder.recoverInterruptedOrders();

        CustomerOrder recovered = orders.findById(order.getId()).orElseThrow();
        assertEquals(OrderStatus.SUCCEEDED, recovered.getStatus());
        assertEquals(ItemStatus.CONFIRMED, recovered.getItems().get(0).getStatus());
        assertEquals(before - 2, stock(SupplierType.TICKET, "T-001"));   // sale stands; confirm doesn't restore
    }
}

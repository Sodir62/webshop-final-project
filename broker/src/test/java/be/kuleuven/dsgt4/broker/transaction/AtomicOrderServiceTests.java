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
import static org.junit.jupiter.api.Assertions.assertTrue;


// "stub": run FOOD/DRINK as in-process stubs so the 2PC success/rollback paths can be driven
// (e.g. setDown) without a live supplier service.
@SpringBootTest
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
}

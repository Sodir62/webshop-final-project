package be.kuleuven.dsgt4.broker.transaction;

import be.kuleuven.dsgt4.broker.data.CustomerOrder;
import be.kuleuven.dsgt4.broker.data.CustomerOrderRepository;
import be.kuleuven.dsgt4.broker.data.ItemStatus;
import be.kuleuven.dsgt4.broker.data.OrderItem;
import be.kuleuven.dsgt4.broker.data.OrderStatus;
import be.kuleuven.dsgt4.broker.supplier.SupplierException;
import be.kuleuven.dsgt4.broker.supplier.SupplierRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/*
   Places an order ATOMICALLY across several independent suppliers, with the broker as
   coordinator (two-phase commit):

    phase 1  RESERVE every item   (each supplier places a cancellable hold)
    phase 2  CONFIRM every item   (each hold becomes a permanent sale)
    on any failure -> CANCEL the holds we already took, mark the order FAILED
*/
@Service
public class AtomicOrderService {

    private static final Logger log = LoggerFactory.getLogger(AtomicOrderService.class);

    private final CustomerOrderRepository orders;
    private final SupplierRegistry suppliers;

    public AtomicOrderService(CustomerOrderRepository orders, SupplierRegistry suppliers) {
        this.orders = orders;
        this.suppliers = suppliers;
    }

    // Runs the two-phase order and returns it in its final state (SUCCEEDED or FAILED).
    public CustomerOrder placeOrder(String orderId) {
        CustomerOrder order = orders.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("no such order " + orderId));
        log.info("order {} start: {} item(s)", orderId, order.getItems().size());

        order.setStatus(OrderStatus.RESERVING);
        save(order);

        // PHASE 1 (the "vote"): reserve every item. While we are still voting the order is
        // UNDECIDED, so a failure here rolls BACK: cancel the holds we took and mark it FAILED.
        List<OrderItem> reserved = new ArrayList<>();   // exactly the holds we must undo on failure
        try {
            for (OrderItem item : List.copyOf(order.getItems())) {
                String reservationId = suppliers.get(item.getSupplierType())
                        .reserve(item.getProductId(), item.getQuantity());
                item.setReservationId(reservationId);
                item.setStatus(ItemStatus.RESERVED);
                reserved.add(item);
                save(order);
                //to do if our broker crashes here we are lowkey fucked, because it remains pending, so we cant cancel it.
                // We either need to add some kind way to track this, probably with the supplier.
                log.info("  reserved {} x{} -> {}", item.getProductId(), item.getQuantity(), reservationId);
            }
        } catch (SupplierException failure) {
            log.warn("order {} FAILING in reserve phase (\"{}\") -> undoing {} reservation(s)",
                    orderId, failure.getMessage(), reserved.size());
            compensate(reserved);
            markRemainingItemsFailed(order);
            order.setStatus(OrderStatus.FAILED);
            save(order);
            return order;
        }

        // COMMIT POINT: every hold is in place, so the order is now DECIDED to commit. Past here
        // we only ever roll FORWARD -- a committed transaction must never abort.
        order.setStatus(OrderStatus.RESERVED);
        save(order);

        // PHASE 2 (the "commit"): confirm every reservation, making each sale permanent.
        order.setStatus(OrderStatus.CONFIRMING);
        save(order);
        try {
            confirmReserved(order);
        } catch (SupplierException failure) {
            // Do NOT compensate/abort: some items may already be permanent sales. Leave the order
            // durably CONFIRMING so OrderRecoveryRunner finishes confirming it on the next startup
            // (exactly what resume() does). No paid sale is ever discarded. TODO: no inline retry yet.
            log.error("order {} confirm interrupted (\"{}\"); left CONFIRMING for recovery to roll forward",
                    orderId, failure.getMessage());
            return order;
        }

        order.setStatus(OrderStatus.SUCCEEDED);
        save(order);
        log.info("order {} SUCCEEDED, total={}", orderId, order.total());
        return order;
    }

    /**
     * Resume every order the broker left mid-transaction (called on startup after a
     * crash). Returns how many were found. 2PC recovery rule below.
     */
    public int recoverInterruptedOrders() {
        List<CustomerOrder> stuck = orders.findByStatusIn(
                List.of(OrderStatus.RESERVING, OrderStatus.RESERVED, OrderStatus.CONFIRMING));
        if (!stuck.isEmpty()) {
            log.warn("recovery: {} interrupted order(s) to resume", stuck.size());
        }
        for (CustomerOrder order : stuck) {
            resume(order);
        }
        return stuck.size();
    }

    /**
     * Resume one interrupted order. If the commit decision was already made
     * (RESERVED/CONFIRMING) we roll FORWARD (confirm); if we crashed mid-vote
     * (RESERVING) we roll BACK (cancel). A committed transaction must never abort.
     */
    public void resume(CustomerOrder order) {
        switch (order.getStatus()) {
            case RESERVED, CONFIRMING -> {
                order.setStatus(OrderStatus.CONFIRMING);
                save(order);
                try {
                    confirmReserved(order);
                    order.setStatus(OrderStatus.SUCCEEDED);
                    save(order);
                    log.info("recovery: order {} rolled forward to SUCCEEDED", order.getId());
                } catch (SupplierException e) {
                    // Leave it CONFIRMING and reattempt on the next startup (recovery runs once per boot).
                    // TODO: no automatic retry yet
                    log.error("recovery: order {} confirm not possible yet ({}); will retry on next startup",
                            order.getId(), e.getMessage());
                }
            }
            case RESERVING -> {
                List<OrderItem> held = order.getItems().stream()
                        .filter(item -> item.getStatus() == ItemStatus.RESERVED)
                        .toList();
                compensate(held);
                markRemainingItemsFailed(order);
                order.setStatus(OrderStatus.FAILED);
                save(order);
                log.info("recovery: order {} rolled back to FAILED", order.getId());
            }
            default -> log.debug("recovery: order {} in {} needs no action", order.getId(), order.getStatus());
        }
    }

    // Confirms every RESERVED item 
    private void confirmReserved(CustomerOrder order) {
        // Snapshot for the same reason as phase 1: save() inside the loop flushes the
        // managed item collection, so we must not iterate the live one.
        for (OrderItem item : List.copyOf(order.getItems())) {
            if (item.getStatus() == ItemStatus.RESERVED) {
                suppliers.get(item.getSupplierType()).confirm(item.getReservationId());
                item.setStatus(ItemStatus.CONFIRMED);
                save(order);
                log.info("  confirmed {}", item.getReservationId());
            }
        }
    }

    // Undo holds in reverse order.
    private void compensate(List<OrderItem> reserved) {
        for (int i = reserved.size() - 1; i >= 0; i--) {
            OrderItem item = reserved.get(i);
            if (item.getStatus() == ItemStatus.CONFIRMED) {
                // already a completed sale; cancel can't undo it
                log.error("  item {} already CONFIRMED, cannot auto-undo; needs a refund", item.getProductId());
                continue;
            }
            try {
                suppliers.get(item.getSupplierType()).cancel(item.getReservationId());
                item.setStatus(ItemStatus.FAILED);
                log.info("  cancelled {}", item.getReservationId());
            } catch (SupplierException e) {
                log.error("  COULD NOT cancel {} ({}); manual cleanup may be needed",
                        item.getReservationId(), e.getMessage());
                item.setStatus(ItemStatus.FAILED);
            }
        }
    }

    // Items that never got reserved are marked FAILED
    private void markRemainingItemsFailed(CustomerOrder order) {
        for (OrderItem item : order.getItems()) {
            if (item.getStatus() == ItemStatus.PENDING) {
                item.setStatus(ItemStatus.FAILED);
            }
        }
    }

    // Write the order's current status to the DB now, as its own committed step 
    private void save(CustomerOrder order) {
        orders.saveAndFlush(order);
    }
}

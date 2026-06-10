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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/*
   Places an order ATOMICALLY across several independent suppliers, with the broker as
   coordinator (two-phase commit):

    phase 1  RESERVE every item   (each supplier places a cancellable hold)
    phase 2  CONFIRM every item   (each hold becomes a permanent sale)
    on any failure -> CANCEL the holds we already took, mark the order FAILED

   An order can have several potential executors -- the customer's request (default
   profile) or the queue listener ('async'), plus the periodic recovery sweep. Starting
   one is therefore a status CAS (CREATED -> RESERVING): exactly one executor wins and
   runs the protocol; every other caller sees a no-op and just reads the current state.
*/
@Service
public class AtomicOrderService {

    private static final Logger log = LoggerFactory.getLogger(AtomicOrderService.class);

    private final CustomerOrderRepository orders;
    private final SupplierRegistry suppliers;
    private final Duration recoveryMinAge;
    private final Duration completionWindow;

    public AtomicOrderService(CustomerOrderRepository orders,
                              SupplierRegistry suppliers,
                              @Value("${broker.recovery.min-age:5m}") Duration recoveryMinAge,
                              @Value("${broker.order.completion-window:15m}") Duration completionWindow) {
        this.orders = orders;
        this.suppliers = suppliers;
        this.recoveryMinAge = recoveryMinAge;
        this.completionWindow = completionWindow;
    }

    /**
     * Runs the two-phase order and returns it in the state it reached: SUCCEEDED, FAILED,
     * or CONFIRMING when phase 2 was interrupted (the queue listener / recovery sweep
     * rolls that forward; confirm is idempotent). Callers that lose the CREATED->RESERVING
     * claim (concurrent listener, sweep, or double submit) get the current state back.
     */
    public CustomerOrder placeOrder(String orderId) {
        if (orders.transitionStatus(orderId, OrderStatus.CREATED, OrderStatus.RESERVING) == 0) {
            CustomerOrder order = orders.findById(orderId)
                    .orElseThrow(() -> new IllegalArgumentException("no such order " + orderId));
            log.info("order {} not claimable in {}; another executor owns it", orderId, order.getStatus());
            return order;
        }
        CustomerOrder order = orders.findById(orderId).orElseThrow();
        log.info("order {} start: {} item(s)", orderId, order.getItems().size());

        // PHASE 1 (the "vote"): reserve every item. While we are still voting the order is
        // UNDECIDED, so a failure here rolls BACK: cancel the holds we took and mark it FAILED.
        List<OrderItem> reserved = new ArrayList<>();   // exactly the holds we must undo on failure
        try {
            for (OrderItem item : List.copyOf(order.getItems())) {
                if (item.getReservationId() != null) {
                    continue;   // already holds stock (a reset retry); never double-reserve
                }
                String reservationId = suppliers.get(item.getSupplierType())
                        .reserve(item.getProductId(), item.getQuantity());
                item.setReservationId(reservationId);
                item.setStatus(ItemStatus.RESERVED);
                reserved.add(item);
                save(order);
                // If the broker dies between the reserve() above and this save(), the hold is
                // recorded nowhere on our side, so recovery cannot cancel it. The suppliers'
                // reservation TTL is the backstop: an unconfirmed hold expires on its own and
                // gives its stock back.
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
            // Do NOT compensate/abort: some items may already be permanent sales. Leave the
            // order durably CONFIRMING; the queue listener ('async') and the periodic recovery
            // sweep keep rolling it forward. No paid sale is ever discarded.
            log.error("order {} confirm interrupted (\"{}\"); left CONFIRMING to be rolled forward",
                    orderId, failure.getMessage());
            return order;
        }

        order.setStatus(OrderStatus.SUCCEEDED);
        save(order);
        log.info("order {} SUCCEEDED, total={}", orderId, order.total());
        return order;
    }

    /**
     * Resume every order left mid-transaction (run periodically by OrderRecoveryRunner).
     * Only orders older than the min age are touched: younger ones may still be executing
     * in a live request or in the queue listener, and sweeping those concurrently would
     * double-process them. Returns how many were found.
     */
    public int recoverInterruptedOrders() {
        Instant cutoff = Instant.now().minus(recoveryMinAge);
        List<CustomerOrder> stuck = orders.findByStatusInAndCreatedAtBefore(
                List.of(OrderStatus.CREATED, OrderStatus.RESERVING, OrderStatus.RESERVED, OrderStatus.CONFIRMING),
                cutoff);
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
                    log.error("recovery: order {} confirm not possible yet (\"{}\"); will retry next sweep",
                            order.getId(), e.getMessage());
                }
            }
            case CREATED -> {
                // The order never started: the queue message was lost in a crash, or the
                // process died between save and execution. Within the completion window we
                // can still honour it; past the window nothing is held anywhere, so failing
                // is the safe abort.
                if (order.getCreatedAt().plus(completionWindow).isBefore(Instant.now())) {
                    if (failIfUnprocessed(order.getId())) {
                        log.warn("recovery: order {} was never processed and its completion window passed; FAILED",
                                order.getId());
                    }
                } else {
                    log.info("recovery: order {} was CREATED but never processed; running it now", order.getId());
                    placeOrder(order.getId());
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

    /**
     * Terminal abort for an order that never started processing: CAS CREATED -> FAILED.
     * Safe at any moment because a CREATED order holds nothing at any supplier. Returns
     * whether this call won the transition (false: someone started or already ended it).
     */
    public boolean failIfUnprocessed(String orderId) {
        if (orders.transitionStatus(orderId, OrderStatus.CREATED, OrderStatus.FAILED) == 0) {
            return false;
        }
        CustomerOrder order = orders.findById(orderId).orElseThrow();
        markRemainingItemsFailed(order);
        save(order);
        return true;
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

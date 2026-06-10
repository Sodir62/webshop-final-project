package be.kuleuven.dsgt4.broker.transaction;

import be.kuleuven.dsgt4.broker.data.CustomerOrder;
import be.kuleuven.dsgt4.broker.data.CustomerOrderRepository;
import be.kuleuven.dsgt4.broker.data.OrderStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/*
   Completes queued orders ('async' profile). The customer can leave the shop; this keeps
   working on the order until it is terminal or its completion window runs out. The window
   is anchored to the order's CREATION time, so a redelivered message or a broker restart
   never extends the 15 minutes.

   Re-entry safety: placeOrder runs at most once here and is itself guarded by the
   CREATED -> RESERVING claim; afterwards we only retry through resume(), which is
   state-aware and idempotent. At the deadline only a still-CREATED order may be failed --
   an order past its commit point is never aborted, the periodic recovery sweep keeps
   rolling it forward even beyond the window.
*/
@Component
@Profile("async")
public class OrderQueueListener {

    private static final Logger log = LoggerFactory.getLogger(OrderQueueListener.class);

    private final AtomicOrderService atomicOrder;
    private final CustomerOrderRepository orders;
    private final Duration completionWindow;
    private final Duration initialBackoff;

    public OrderQueueListener(AtomicOrderService atomicOrder,
                              CustomerOrderRepository orders,
                              @Value("${broker.order.completion-window:15m}") Duration completionWindow,
                              @Value("${broker.order.retry-backoff:1s}") Duration initialBackoff) {
        this.atomicOrder = atomicOrder;
        this.orders = orders;
        this.completionWindow = completionWindow;
        this.initialBackoff = initialBackoff;
    }

    @JmsListener(destination = OrderProcessingConfig.ORDER_QUEUE)
    public void processOrder(String orderId) {
        CustomerOrder order = orders.findById(orderId).orElse(null);
        if (order == null) {
            log.error("order {} not found in database; dropping the message", orderId);
            return;
        }
        Instant deadline = order.getCreatedAt().plus(completionWindow);
        log.info("dequeued order {}; working on it until {}", orderId, deadline);

        long backoffMs = initialBackoff.toMillis();
        while (true) {
            try {
                order = step(orderId);
            } catch (Exception e) {
                // Never let this escape: JMS would just redeliver the message. The backoff
                // below is the retry mechanism; the periodic sweep is the last resort.
                log.warn("order {} processing attempt failed: {}", orderId, e.getMessage());
            }
            if (order == null || isTerminal(order.getStatus())) {
                return;
            }
            if (!Instant.now().isBefore(deadline)) {
                break;
            }
            if (!sleep(backoffMs)) {
                return;   // interrupted (shutdown); the sweep takes over
            }
            backoffMs = Math.min(backoffMs * 2, 60_000);
        }

        // Deadline reached. Failing is only allowed BEFORE the commit point.
        if (atomicOrder.failIfUnprocessed(orderId)) {
            log.error("order {} could not be completed within its window; FAILED (nothing was held)", orderId);
        } else {
            log.error("order {} still {} past its window; committed work is never aborted -- "
                    + "the recovery sweep keeps resolving it", orderId, order.getStatus());
        }
    }

    // One state-aware attempt; returns the order's state afterwards (null = vanished).
    private CustomerOrder step(String orderId) {
        CustomerOrder order = orders.findById(orderId).orElse(null);
        if (order == null) {
            log.error("order {} disappeared from the database; dropping", orderId);
            return null;
        }
        return switch (order.getStatus()) {
            case CREATED -> atomicOrder.placeOrder(orderId);   // claims it, or no-ops if raced
            case RESERVED, CONFIRMING -> {
                atomicOrder.resume(order);
                yield orders.findById(orderId).orElse(null);
            }
            // RESERVING: another executor is mid-phase-1 right now; just wait for it.
            case RESERVING, SUCCEEDED, FAILED -> order;
        };
    }

    private static boolean isTerminal(OrderStatus status) {
        return status == OrderStatus.SUCCEEDED || status == OrderStatus.FAILED;
    }

    private static boolean sleep(long ms) {
        try {
            Thread.sleep(ms);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}

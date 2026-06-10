package be.kuleuven.dsgt4.broker.transaction;

import be.kuleuven.dsgt4.broker.data.CustomerOrder;
import be.kuleuven.dsgt4.broker.data.CustomerOrderRepository;
import be.kuleuven.dsgt4.broker.data.OrderStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
@Profile("async")
public class OrderQueueListener {

    private static final Logger log = LoggerFactory.getLogger(OrderQueueListener.class);
    private static final Duration MAX_RETRY_WINDOW = Duration.ofMinutes(15);

    private final AtomicOrderService atomicOrder;
    private final CustomerOrderRepository orders;

    public OrderQueueListener(AtomicOrderService atomicOrder, CustomerOrderRepository orders) {
        this.atomicOrder = atomicOrder;
        this.orders = orders;
    }

    @JmsListener(destination = OrderProcessingConfig.ORDER_QUEUE)
    public void processOrder(String orderId) {
        log.info("dequeued order {} for processing", orderId);
        Instant deadline = Instant.now().plus(MAX_RETRY_WINDOW);
        long backoffMs = 1000;

        while (Instant.now().isBefore(deadline)) {
            CustomerOrder current = orders.findById(orderId).orElse(null);
            if (current == null) {
                log.error("order {} not found in database, dropping", orderId);
                return;
            }
            if (current.getStatus() == OrderStatus.SUCCEEDED || current.getStatus() == OrderStatus.FAILED) {
                log.info("order {} already in terminal state {}", orderId, current.getStatus());
                return;
            }

            try {
                CustomerOrder result = atomicOrder.placeOrder(orderId);
                if (result.getStatus() == OrderStatus.SUCCEEDED || result.getStatus() == OrderStatus.FAILED) {
                    return;
                }
            } catch (Exception e) {
                log.warn("order {} processing attempt failed: {}", orderId, e.getMessage());
            }

            try {
                Thread.sleep(Math.min(backoffMs, 60_000));
                backoffMs = Math.min(backoffMs * 2, 60_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }

        log.error("order {} exceeded 15-minute retry window; marking FAILED", orderId);
        orders.findById(orderId).ifPresent(order -> {
            if (order.getStatus() != OrderStatus.SUCCEEDED) {
                order.setStatus(OrderStatus.FAILED);
                orders.saveAndFlush(order);
            }
        });
    }
}

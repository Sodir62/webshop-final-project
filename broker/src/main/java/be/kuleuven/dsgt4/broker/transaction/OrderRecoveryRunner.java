package be.kuleuven.dsgt4.broker.transaction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OrderRecoveryRunner {

    private static final Logger log = LoggerFactory.getLogger(OrderRecoveryRunner.class);

    private final AtomicOrderService atomicOrder;

    public OrderRecoveryRunner(AtomicOrderService atomicOrder) {
        this.atomicOrder = atomicOrder;
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 5_000)
    public void resumeInterruptedOrders() {
        int found = atomicOrder.recoverInterruptedOrders();
        if (found > 0) {
            log.warn("recovery: found {} interrupted order(s) to resume", found);
        }
    }
}

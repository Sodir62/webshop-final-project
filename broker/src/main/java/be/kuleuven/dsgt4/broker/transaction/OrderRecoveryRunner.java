package be.kuleuven.dsgt4.broker.transaction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/*
   Resumes orders the broker left mid-transaction (e.g. it crashed between reserve and
   confirm). This is what makes the "broker crashed before the supplier could confirm"
   scenario respect ACID: the durable order status is read back and the two-phase
   protocol is finished. The sweep runs periodically, not only at startup, so a stuck
   confirm is also retried while the broker stays up; AtomicOrderService's min-age guard
   keeps it away from orders that are still being executed live.
*/
@Component
public class OrderRecoveryRunner {

    private static final Logger log = LoggerFactory.getLogger(OrderRecoveryRunner.class);

    private final AtomicOrderService atomicOrder;

    public OrderRecoveryRunner(AtomicOrderService atomicOrder) {
        this.atomicOrder = atomicOrder;
    }

    @Scheduled(fixedDelayString = "${broker.recovery.interval:60s}", initialDelayString = "5s")
    public void resumeInterruptedOrders() {
        int found = atomicOrder.recoverInterruptedOrders();
        if (found > 0) {
            log.warn("recovery: found {} interrupted order(s) to resume", found);
        }
    }
}

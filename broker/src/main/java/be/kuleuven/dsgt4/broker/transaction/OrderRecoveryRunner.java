package be.kuleuven.dsgt4.broker.transaction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/*
   On startup, resume any orders the broker left mid-transaction (e.g. it crashed
   between reserve and confirm). This is what makes the "broker crashed before the
   supplier could confirm" scenario respect ACID: the durable order status is read
   back and the two-phase protocol is finished.
*/
@Component
public class OrderRecoveryRunner {

    private static final Logger log = LoggerFactory.getLogger(OrderRecoveryRunner.class);

    private final AtomicOrderService atomicOrder;

    public OrderRecoveryRunner(AtomicOrderService atomicOrder) {
        this.atomicOrder = atomicOrder;
    }

    @EventListener(ApplicationReadyEvent.class) // run on start up
    public void resumeInterruptedOrders() {
        int found = atomicOrder.recoverInterruptedOrders();
        if (found > 0) {
            // per-order outcome (rolled forward / back / still CONFIRMING) is logged inside resume()
            log.warn("recovery: found {} interrupted order(s) to resume on startup", found);
        }
    }
}

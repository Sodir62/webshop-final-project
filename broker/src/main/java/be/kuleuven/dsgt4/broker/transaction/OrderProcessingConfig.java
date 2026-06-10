package be.kuleuven.dsgt4.broker.transaction;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jms.core.JmsTemplate;

@Configuration
public class OrderProcessingConfig {

    /** The queue OrderQueueListener consumes; only used under the 'async' profile. */
    public static final String ORDER_QUEUE = "order-queue";

    // Basic level: the two-phase order runs synchronously in the customer's request,
    // so the redirect to the order page already shows the final state.
    @Bean
    @Profile("!async")
    OrderProcessor syncOrderProcessor(AtomicOrderService atomicOrder) {
        return atomicOrder::placeOrder;
    }

    // Level 2: enqueue and return immediately; the customer can leave while the broker
    // keeps trying. OrderQueueListener completes the order within the retry window.
    @Bean
    @Profile("async")
    OrderProcessor queuedOrderProcessor(JmsTemplate jms) {
        return orderId -> jms.convertAndSend(ORDER_QUEUE, orderId);
    }
}

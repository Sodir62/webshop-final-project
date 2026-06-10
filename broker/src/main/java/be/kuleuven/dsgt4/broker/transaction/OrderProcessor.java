package be.kuleuven.dsgt4.broker.transaction;

/*
   How a freshly saved (CREATED) order gets executed. The web layer only knows this seam;
   OrderProcessingConfig picks the implementation: run the two-phase order inside the
   customer's request (default), or enqueue it for OrderQueueListener ('async' profile).
*/
@FunctionalInterface
public interface OrderProcessor {

    void process(String orderId);
}

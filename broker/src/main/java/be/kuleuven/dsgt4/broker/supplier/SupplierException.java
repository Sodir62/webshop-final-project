package be.kuleuven.dsgt4.broker.supplier;

/*
   Thrown by a SupplierClient for any supplier-side failure.
   When implementing real services = correct http exceptions? 
*/
public class SupplierException extends RuntimeException {
    public SupplierException(String message) {
        super(message);
    }
}

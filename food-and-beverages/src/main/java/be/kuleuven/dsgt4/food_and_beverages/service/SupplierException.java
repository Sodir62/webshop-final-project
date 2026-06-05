package be.kuleuven.dsgt4.food_and_beverages.service;

/*
   Thrown by SupplierService for any request the supplier rejects. The Reason is a domain
   classification (kept free of web types); the web layer maps it to an HTTP status.
*/
public class SupplierException extends RuntimeException {

    public enum Reason {
        INVALID_REQUEST,   // -> 400: the request itself is wrong (e.g. non-positive quantity)
        NOT_FOUND,         // -> 404: the product or reservation does not exist
        CONFLICT           // -> 409: not possible against current state (out of stock / illegal transition)
    }

    private final Reason reason;

    public SupplierException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
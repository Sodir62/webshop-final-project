package be.kuleuven.dsgt4.broker.supplier;

import be.kuleuven.dsgt4.broker.data.SupplierType;

import java.util.List;
import java.util.Optional;

/*
    How the broker sees the external suppliers. HttpSupplierClient talks to the real services
    over HTTP; StubSupplierClient is the in-process fake used under the 'stub' profile (tests).

    list()                 -> GET    /products
    reserve(productId, n)  -> POST   /reservations
    confirm(reservationId) -> POST   /reservations/{id}/confirm
    cancel(reservationId)  -> DELETE /reservations/{id}
*/

public interface SupplierClient {
    // Which supplier this client talks to; the registry routes by this.
    SupplierType type();

    // Current catalog with live stock. 
    List<Product> list();

    // One product by id, or empty if this supplier doesn't have it.
    Optional<Product> find(String productId);

    // Hold quantity amount of units; returns an reservation id. Throws if out of stock/unreachable.
    String reserve(String productId, int quantity);

    // Turn a hold into a sale. Throws if the reservation is unknown or supplier unreachable.
    void confirm(String reservationId);

    // Release a hold
    void cancel(String reservationId);
}

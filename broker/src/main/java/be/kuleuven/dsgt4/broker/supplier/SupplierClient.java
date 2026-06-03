package be.kuleuven.dsgt4.broker.supplier;

import java.util.List; 

/*
    The broker's view of a supplier.
    For now it is just fake stubs, but should become real HTTP calls to drink-supplier, food-supplier and ticket-supplier. 
*/

public interface SupplierClient {
    List<Product> getProducts();
}

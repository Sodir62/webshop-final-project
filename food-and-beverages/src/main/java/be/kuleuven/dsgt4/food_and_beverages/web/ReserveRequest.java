package be.kuleuven.dsgt4.food_and_beverages.web;

/*
   POST /reservations body: which product and how many units to hold.
*/
public record ReserveRequest(
        String productId,
        int quantity
) {}
package be.kuleuven.dsgt4.ticketsupplier.web;

public record ReserveRequest(
        String productId,
        int quantity
) {}

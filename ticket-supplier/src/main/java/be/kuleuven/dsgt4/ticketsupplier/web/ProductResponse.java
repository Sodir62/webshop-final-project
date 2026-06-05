package be.kuleuven.dsgt4.ticketsupplier.web;

import java.math.BigDecimal;

public record ProductResponse(
        String id,
        String name,
        String description,
        BigDecimal price,
        int stock
) {}

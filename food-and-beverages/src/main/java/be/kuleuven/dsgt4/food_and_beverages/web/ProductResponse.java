package be.kuleuven.dsgt4.food_and_beverages.web;

import java.math.BigDecimal;

/*
   GET /products payload. Mirrors the broker's be.kuleuven.dsgt4.broker.supplier.Product
   record field-for-field so the broker's HTTP client deserialises it directly.
*/
public record ProductResponse(
        String id,
        String name,
        String description,
        BigDecimal price,
        int stock
) {}
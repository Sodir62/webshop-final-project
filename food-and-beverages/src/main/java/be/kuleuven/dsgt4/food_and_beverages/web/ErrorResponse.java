package be.kuleuven.dsgt4.food_and_beverages.web;

/*
   JSON body returned for a rejected request, e.g. {"error": "out of stock for F-001 ..."}.
*/
public record ErrorResponse(
        String error
) {}
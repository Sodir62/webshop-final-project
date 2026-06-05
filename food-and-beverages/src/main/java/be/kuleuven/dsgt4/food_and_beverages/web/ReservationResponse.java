package be.kuleuven.dsgt4.food_and_beverages.web;

/*
   POST /reservations response: the reservation handle the broker stores and later
   passes back to confirm or cancel.
*/
public record ReservationResponse(
        String reservationId
) {}
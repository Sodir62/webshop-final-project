package be.kuleuven.dsgt4.food_and_beverages.data;

/*
   Lifecycle of a hold. A reservation starts PENDING (stock is held), then becomes either
   CONFIRMED (the sale is final, stock consumed) or CANCELLED (the held stock is released).
*/
public enum ReservationStatus {
    PENDING,
    CONFIRMED,
    CANCELLED
}
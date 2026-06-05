package be.kuleuven.dsgt4.food_and_beverages.data;

/*
   Which kind of product this is. The broker models food and drink as two separate suppliers
   (SupplierType.FOOD / SupplierType.DRINK) that each list their own catalog, so this one
   service tags every product and can return just one kind via GET /products?type=FOOD.
*/
public enum Category {
    FOOD,
    DRINK
}
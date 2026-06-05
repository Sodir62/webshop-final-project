package be.kuleuven.dsgt4.food_and_beverages.web;

import be.kuleuven.dsgt4.food_and_beverages.data.Category;
import be.kuleuven.dsgt4.food_and_beverages.service.SupplierService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/*
   The catalog the broker lists. GET /products returns every food and drink product with its
   live stock, in the broker's Product shape; GET /products?type=FOOD (or DRINK) returns just
   that kind, which is how the broker's separate FOOD and DRINK suppliers each list.
*/
@RestController
public class ProductController {

    private final SupplierService supplier;

    public ProductController(SupplierService supplier) {
        this.supplier = supplier;
    }

    @GetMapping("/products")
    public List<ProductResponse> list(@RequestParam(required = false) Category type) {
        return supplier.listProducts(type).stream()
                .map(p -> new ProductResponse(p.getId(), p.getName(), p.getDescription(), p.getPrice(), p.getStock()))
                .toList();
    }
}
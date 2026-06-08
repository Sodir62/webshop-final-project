package be.kuleuven.dsgt4.ticketsupplier.web;

import be.kuleuven.dsgt4.ticketsupplier.service.MongoTicketSupplierService;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/*
   MongoDB version of ProductController.
   Only active when running with the "mongo" profile.
*/
@RestController
@Profile("mongo")
public class MongoProductController {

    private final MongoTicketSupplierService supplier;

    public MongoProductController(MongoTicketSupplierService supplier) {
        this.supplier = supplier;
    }

    @GetMapping("/products")
    public List<ProductResponse> list(@RequestParam(required = false) String type) {
        return supplier.listProducts().stream()
                .map(p -> new ProductResponse(p.getId(), p.getName(), p.getDescription(), p.getPrice(), p.getStock()))
                .toList();
    }
}

package be.kuleuven.dsgt4.broker.data;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/*
    Data access for orders. Spring Data generates the implementation at runtime
    from this interface 
    JpaRepo = gives some functions we can use
*/ 

public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, String> {
    // Newest orders first
    List<CustomerOrder> findAllByOrderByCreatedAtDesc();
}

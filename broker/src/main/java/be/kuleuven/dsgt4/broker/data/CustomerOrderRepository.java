package be.kuleuven.dsgt4.broker.data;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

/*
    Data access for orders.
*/
public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, String> {
    // Orders left mid-transaction 
    List<CustomerOrder> findByStatusIn(Collection<OrderStatus> statuses);
}

package be.kuleuven.dsgt4.broker.data;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

/*
    Data access for orders.
*/
public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, String> {
    // Orders left mid-transaction, but only ones old enough that no live executor can
    // still be working on them (the recovery sweep must not race a running placeOrder).
    List<CustomerOrder> findByStatusInAndCreatedAtBefore(Collection<OrderStatus> statuses, Instant cutoff);

    // All orders, newest first
    List<CustomerOrder> findAllByOrderByCreatedAtDesc();

    // Atomic status CAS. An order can have several potential executors (request thread,
    // queue listener, recovery sweep); this single row update decides the one winner.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("update CustomerOrder o set o.status = :to where o.id = :id and o.status = :from")
    int transitionStatus(@Param("id") String id, @Param("from") OrderStatus from, @Param("to") OrderStatus to);
}

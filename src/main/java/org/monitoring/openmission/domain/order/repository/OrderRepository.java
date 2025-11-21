package org.monitoring.openmission.domain.order.repository;

import org.monitoring.openmission.domain.order.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {

}

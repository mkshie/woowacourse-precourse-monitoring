package org.monitoring.openmission.domain.item.repository;

import java.util.Optional;
import org.monitoring.openmission.domain.item.entity.Item;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ItemRepository extends JpaRepository<Item, Integer> {
    Optional<Item> getItemsById(Long id);
}

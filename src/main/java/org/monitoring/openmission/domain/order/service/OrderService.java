package org.monitoring.openmission.domain.order.service;


import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.monitoring.openmission.domain.item.entity.Item;
import org.monitoring.openmission.domain.item.repository.ItemRepository;
import org.monitoring.openmission.domain.item.service.ItemService;
import org.monitoring.openmission.domain.order.entity.Order;
import org.monitoring.openmission.domain.order.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final ItemRepository itemRepository;
    private final ItemService itemService;

    @Transactional
    public Order createOrder(Long itemId , Integer quantity) {
        Item item = itemRepository.getItemsById(itemId).orElseThrow(
                () -> new EntityNotFoundException(itemId +"에 맞는 물건이 존재하지 않습니다."));

        Order order = Order.builder()
                .item(item)
                .quantity(quantity)
                .totalPrice(item.getPrice() * quantity)
                .build();

        orderRepository.save(order);

        return order;
    }
}

package org.monitoring.openmission.domain.item.service;

import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.monitoring.openmission.domain.item.dto.request.ItemCreateRequest;
import org.monitoring.openmission.domain.item.dto.request.ItemUpdateRequest;
import org.monitoring.openmission.domain.item.dto.response.ItemResponse;
import org.monitoring.openmission.domain.item.entity.Item;
import org.monitoring.openmission.domain.item.repository.ItemRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ItemService {

    private final ItemRepository itemRepository;

    @Transactional
    public Long createItem(ItemCreateRequest request){
        Item item = Item.builder()
                .name(request.name())
                .price(request.price())
                .stock(request.stock())
                .build();

        item = itemRepository.save(item);

        return item.getId();
    }

    @Transactional
    public ItemResponse updateItem(Long id , ItemUpdateRequest request) {
        Item item = itemRepository.getItemsById(id).orElseThrow(()-> new EntityNotFoundException("Item not found"));

        if(request.name() != null){
            item.setName(request.name());
        }
        if(request.price() != null){
            item.setPrice(request.price());
        }
        if(request.stock() != null){
            item.setStock(request.stock());
        }
        item = itemRepository.save(item);

        return ItemResponse.of(item);
    }

    @Transactional(readOnly = true)
    public ItemResponse getItem(Long id){
        Item item = itemRepository.getItemsById(id).orElseThrow(()-> new EntityNotFoundException("Item not found"));
        return ItemResponse.of(item);
    }

    @Transactional(readOnly = true)
    public List<ItemResponse> getAllItems(int page , int size) {
        Pageable pageable = PageRequest.of(page, size);
        List<Item> items = itemRepository.findAll(pageable).getContent();
        return items.stream().map(ItemResponse::of).toList();
    }
}

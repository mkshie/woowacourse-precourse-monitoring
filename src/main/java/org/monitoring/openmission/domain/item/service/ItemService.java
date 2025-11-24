package org.monitoring.openmission.domain.item.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.annotation.Observed;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class ItemService {

    private final ItemRepository itemRepository;
    private final MeterRegistry meterRegistry; // 도메인 메트릭


    @Transactional
    public Long createItem(ItemCreateRequest request) {

        log.info("item.create metric increment");
        meterRegistry.counter("item.create.attempt").increment();

        long startTime = System.nanoTime();
        boolean success = false;

        try {
            Item item = Item.builder()
                    .name(request.name())
                    .price(request.price())
                    .stock(request.stock())
                    .build();

            item = itemRepository.save(item);

            success = true;

            return item.getId();
        } catch (Exception e) {
            log.info("item.create.failed", e);
            meterRegistry.counter("item.create.failed", "exception", e.getClass().getSimpleName()
            ).increment();

            throw e;
        } finally {
            long duration = System.nanoTime() - startTime;
            meterRegistry.timer("item.create.duration", "success", String.valueOf(success))
                    .record(duration, TimeUnit.NANOSECONDS);
        }
    }

    @Transactional
    public ItemResponse updateItem(Long id, ItemUpdateRequest request) {
        Item item = itemRepository.getItemsById(id).orElseThrow(() -> new EntityNotFoundException("Item not found"));

        if (request.name() != null) {
            item.setName(request.name());
        }
        if (request.price() != null) {
            item.setPrice(request.price());
        }
        if (request.stock() != null) {
            item.setStock(request.stock());
        }
        item = itemRepository.save(item);

        return ItemResponse.of(item);
    }

    @Transactional(readOnly = true)
    @Observed(name = "Item.get", contextualName = "ItemService#getItem")
    public ItemResponse getItem(Long id) {
        log.info("getAllItems service start");
        Item item = itemRepository.getItemsById(id).orElseThrow(() -> new EntityNotFoundException("Item not found"));
        return ItemResponse.of(item);
    }


    @Transactional(readOnly = true)
    @Observed(name = "AllItem.get", contextualName = "ItemService#getAllItems")
    public List<ItemResponse> getAllItems(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        List<Item> items = itemRepository.findAll(pageable).getContent();
        return items.stream().map(ItemResponse::of).toList();
    }
}

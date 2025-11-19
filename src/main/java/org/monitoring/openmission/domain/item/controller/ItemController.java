package org.monitoring.openmission.domain.item.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.monitoring.openmission.domain.item.dto.request.ItemCreateRequest;
import org.monitoring.openmission.domain.item.dto.request.ItemUpdateRequest;
import org.monitoring.openmission.domain.item.dto.response.ItemResponse;
import org.monitoring.openmission.domain.order.service.ItemService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Item API", description = "물건 관리 API")
@RestController
@RequestMapping("/api/items")
@RequiredArgsConstructor
public class ItemController {

    private final ItemService itemService;

    @Operation(summary = "아이템 생성", description = "물건의 이름 , 가격 , 재고를 입력해주세요")
    @PostMapping
    public ResponseEntity<String> createItem(@Valid @RequestBody ItemCreateRequest request) {

        return ResponseEntity.ok("물건의 아이디가 " + itemService.createItem(request) + "인 물건이 생성되었습니다");
    }

    @Operation(summary = "아이템 수정", description = "수정 할 물건의 아이디 ,이름 , 가격 , 재고를 입력해주세요")
    @PatchMapping("/{id}")
    public ResponseEntity<ItemResponse> updateItem(
            @PathVariable(name = "id") Long itemId,
            @Valid @RequestBody ItemUpdateRequest request) {

        return ResponseEntity.ok(itemService.updateItem(itemId, request));
    }

    @Operation(summary = "아이템 조회", description = "조회할 item 의 id 를 입력해주세요")
    @GetMapping("/{id}")
    public ResponseEntity<ItemResponse> getItem(
            @PathVariable(name = "id") Long itemId
    ){
        return ResponseEntity.ok(itemService.getItem(itemId));
    }


}

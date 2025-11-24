package org.monitoring.openmission.domain.order.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.monitoring.openmission.domain.order.dto.response.OrderResponse;
import org.monitoring.openmission.domain.order.entity.Order;
import org.monitoring.openmission.domain.order.service.OrderService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Tag(name = "주문 API", description = "생성 , 조회 , 삭제 , 수정 api 존재")
public class OrderController {

    private final OrderService orderService;

    @PostMapping("/{id}")
    @Operation(summary = "주문 생성 API", description = "주문 ID 를 입력해주세요")
    public ResponseEntity<OrderResponse> createOrder(
            @PathVariable(name = "id") Long itemId,
            @RequestParam(name = "quantity") @Positive Integer quantity
    ) {
        Order order = orderService.createOrder(itemId, quantity);
        return ResponseEntity.ok(OrderResponse.of(order));
    }

    @GetMapping("/{id}")
    @Operation(summary = "특정 주문을 조회하는 API", description = "특정 주문 ID 를 입력해주세요")
    public ResponseEntity<OrderResponse> getOrder(
            @PathVariable(name = "id") Long itemId) {
        Order order = orderService.getOrder(itemId);
        return ResponseEntity.ok(OrderResponse.of(order));
    }
}

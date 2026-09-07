package com.poc.elasticsearch.controller;

import com.poc.elasticsearch.dto.CreateOrderRequest;
import com.poc.elasticsearch.dto.OrderResponse;
import com.poc.elasticsearch.model.OrderStatus;
import com.poc.elasticsearch.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse createOrder(@Valid @RequestBody CreateOrderRequest request) {
        return orderService.createOrder(request);
    }

    @GetMapping("/search")
    public List<OrderResponse> searchOrders(
            @RequestParam(required = false) String customerEmail,
            @RequestParam(required = false) OrderStatus status
    ) {
        return orderService.searchOrders(customerEmail, status);
    }
}

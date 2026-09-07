package com.poc.elasticsearch.service;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.poc.elasticsearch.dto.CreateOrderRequest;
import com.poc.elasticsearch.dto.OrderResponse;
import com.poc.elasticsearch.exception.BadRequestException;
import com.poc.elasticsearch.model.Order;
import com.poc.elasticsearch.model.OrderItem;
import com.poc.elasticsearch.model.OrderStatus;
import com.poc.elasticsearch.model.Product;
import com.poc.elasticsearch.repository.OrderRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductService productService;
    private final ElasticsearchOperations elasticsearchOperations;

    public OrderService(
            OrderRepository orderRepository,
            ProductService productService,
            ElasticsearchOperations elasticsearchOperations
    ) {
        this.orderRepository = orderRepository;
        this.productService = productService;
        this.elasticsearchOperations = elasticsearchOperations;
    }

    public OrderResponse createOrder(CreateOrderRequest request) {
        List<OrderItem> items = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;

        for (CreateOrderRequest.OrderItemRequest itemRequest : request.items()) {
            Product product = productService.getProductEntity(itemRequest.productId());

            if (product.getStock() < itemRequest.quantity()) {
                throw new BadRequestException(
                        "Insufficient stock for product " + product.getName() + ": requested "
                                + itemRequest.quantity() + ", available " + product.getStock()
                );
            }

            BigDecimal lineTotal = product.getPrice().multiply(BigDecimal.valueOf(itemRequest.quantity()));
            total = total.add(lineTotal);

            items.add(new OrderItem(
                    product.getId(),
                    product.getName(),
                    itemRequest.quantity(),
                    product.getPrice()
            ));

            product.setStock(product.getStock() - itemRequest.quantity());
            productService.saveProduct(product);
        }

        Order order = new Order();
        order.setCustomerEmail(request.customerEmail());
        order.setStatus(OrderStatus.PENDING);
        order.setItems(items);
        order.setTotalAmount(total);
        order.setCreatedAt(Instant.now());

        Order saved = orderRepository.save(order);
        return OrderResponse.from(saved);
    }

    public List<OrderResponse> searchOrders(String customerEmail, OrderStatus status) {
        BoolQuery.Builder boolQuery = new BoolQuery.Builder();

        if (StringUtils.hasText(customerEmail)) {
            boolQuery.filter(Query.of(q -> q.term(t -> t
                    .field("customerEmail")
                    .value(customerEmail)
            )));
        }

        if (status != null) {
            boolQuery.filter(Query.of(q -> q.term(t -> t
                    .field("status")
                    .value(status.name())
            )));
        }

        if (!StringUtils.hasText(customerEmail) && status == null) {
            boolQuery.must(Query.of(q -> q.matchAll(ma -> ma)));
        }

        NativeQuery nativeQuery = NativeQuery.builder()
                .withQuery(Query.of(q -> q.bool(boolQuery.build())))
                .withPageable(PageRequest.of(0, 50))
                .build();

        return elasticsearchOperations.search(nativeQuery, Order.class)
                .stream()
                .map(SearchHit::getContent)
                .map(OrderResponse::from)
                .toList();
    }
}

package com.poc.elasticsearch.repository;

import com.poc.elasticsearch.model.Order;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface OrderRepository extends ElasticsearchRepository<Order, String> {
}

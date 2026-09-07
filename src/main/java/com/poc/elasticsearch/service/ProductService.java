package com.poc.elasticsearch.service;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import com.poc.elasticsearch.model.Product;
import com.poc.elasticsearch.repository.ProductRepository;
import com.poc.elasticsearch.web.ProductForm;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.StreamSupport;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final ElasticsearchOperations elasticsearchOperations;

    public ProductService(
            ProductRepository productRepository,
            ElasticsearchOperations elasticsearchOperations
    ) {
        this.productRepository = productRepository;
        this.elasticsearchOperations = elasticsearchOperations;
    }

    public List<Product> findAll() {
        return StreamSupport.stream(productRepository.findAll().spliterator(), false)
                .sorted(Comparator.comparing(Product::id))
                .toList();
    }

    public List<Product> search(String text, String category) {
        boolean hasText = StringUtils.hasText(text);
        boolean hasCategory = StringUtils.hasText(category);

        if (!hasText && !hasCategory) {
            return findAll();
        }

        BoolQuery.Builder boolQuery = new BoolQuery.Builder();

        if (hasText) {
            boolQuery.must(query -> query.multiMatch(multiMatch -> multiMatch
                    .query(text.trim())
                    .fields("name^2", "description")
            ));
        }

        if (hasCategory) {
            boolQuery.filter(query -> query.term(term -> term
                    .field("category")
                    .value(category.trim())
            ));
        }

        NativeQuery query = NativeQuery.builder()
                .withQuery(boolQuery.build()._toQuery())
                .withPageable(PageRequest.of(0, 100))
                .build();

        return elasticsearchOperations.search(query, Product.class).stream()
                .map(SearchHit::getContent)
                .toList();
    }

    public Product create(ProductForm form) {
        Product product = new Product(
                UUID.randomUUID().toString(),
                form.getName().trim(),
                form.getDescription().trim(),
                form.getCategory().trim(),
                form.getPrice(),
                form.getStock()
        );
        return productRepository.save(product);
    }
}

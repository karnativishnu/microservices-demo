package com.example.order.client;

import com.example.order.dto.ProductDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class InventoryClient {

    private final RestTemplate restTemplate;
    private final String inventoryServiceUrl;

    public InventoryClient(RestTemplate restTemplate,
                            @Value("${inventory.service.url}") String inventoryServiceUrl) {
        this.restTemplate = restTemplate;
        this.inventoryServiceUrl = inventoryServiceUrl;
    }

    public ProductDto getProduct(Long productId) {
        return restTemplate.getForObject(inventoryServiceUrl + "/products/" + productId, ProductDto.class);
    }

    // Tells inventory-service to decrement stock once an order is confirmed
    public void reduceStock(Long productId, Integer quantity) {
        String url = inventoryServiceUrl + "/products/" + productId + "/reduce?quantity=" + quantity;
        restTemplate.exchange(url, HttpMethod.PUT, null, ProductDto.class);
    }
}

package com.example.order.controller;

import com.example.order.client.InventoryClient;
import com.example.order.dto.OrderRequest;
import com.example.order.dto.ProductDto;
import com.example.order.model.Order;
import com.example.order.repository.OrderRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderRepository orderRepository;
    private final InventoryClient inventoryClient;

    public OrderController(OrderRepository orderRepository, InventoryClient inventoryClient) {
        this.orderRepository = orderRepository;
        this.inventoryClient = inventoryClient;
    }

    @GetMapping
    public List<Order> getAll() {
        return orderRepository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Order> getById(@PathVariable Long id) {
        return orderRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Order> place(@RequestBody OrderRequest request) {
        ProductDto product = inventoryClient.getProduct(request.getProductId());
        if (product == null || product.getQuantity() < request.getQuantity()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }

        inventoryClient.reduceStock(request.getProductId(), request.getQuantity());

        Order order = new Order();
        order.setProductId(product.getId());
        order.setProductName(product.getName());
        order.setQuantity(request.getQuantity());
        order.setTotalPrice(product.getPrice() * request.getQuantity());
        order.setStatus("CONFIRMED");

        return ResponseEntity.ok(orderRepository.save(order));
    }
}

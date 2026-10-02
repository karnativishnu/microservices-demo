package com.example.inventory.controller;

import com.example.inventory.model.Product;
import com.example.inventory.repository.ProductRepository;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/products")
public class ProductController {

    private final ProductRepository productRepository;

    public ProductController(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @GetMapping
    public List<Product> getAll() {
        return productRepository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Product> getById(@PathVariable Long id) {
        return productRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public Product create(@RequestBody Product product) {
        return productRepository.save(product);
    }

    // Called by order-service to decrement stock when an order is placed
    @PutMapping("/{id}/reduce")
    public ResponseEntity<Product> reduceStock(@PathVariable Long id, @RequestParam Integer quantity) {
        return productRepository.findById(id)
                .map(product -> {
                    if (product.getQuantity() < quantity) {
                        return ResponseEntity.badRequest().<Product>build();
                    }
                    product.setQuantity(product.getQuantity() - quantity);
                    return ResponseEntity.ok(productRepository.save(product));
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}

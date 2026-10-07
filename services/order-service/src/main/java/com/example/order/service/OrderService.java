package com.example.order.service;

import com.example.order.client.InventoryClient;
import com.example.order.dto.InventoryResponse;
import com.example.order.dto.OrderResponse;
import com.example.order.repository.OrderRepository;
import org.springframework.stereotype.Service;
import com.example.order.event.OrderCreatedEvent;
import com.example.order.kafka.OrderEventProducer;
import java.util.Map;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final InventoryClient inventoryClient;
    private final OrderEventProducer orderEventProducer;

	public OrderService(
        OrderRepository orderRepository,
        InventoryClient inventoryClient,
        OrderEventProducer orderEventProducer) {

    	this.orderRepository = orderRepository;
    	this.inventoryClient = inventoryClient;
    	this.orderEventProducer = orderEventProducer;
	}
    public OrderResponse getOrder(String id) {

        Map<String, Object> order = orderRepository.findById(id);

        return new OrderResponse(
                (String) order.get("id"),
                (String) order.get("customer_name"),
                (String) order.get("product"),
                (Integer) order.get("quantity"),
                (String) order.get("status")
        );
    }

    public Map<String, Object> checkDatabase() {
        return orderRepository.checkDatabase();
    }

    public OrderInventoryResponse getOrderInventory(String id) {

        OrderResponse order = getOrder(id);

        InventoryResponse inventory =
                inventoryClient.getInventory(order.product());

        return new OrderInventoryResponse(order, inventory);
    }

    public record OrderInventoryResponse(
            OrderResponse order,
            InventoryResponse inventory
    ) {
    }
public void publishOrderCreated(String id) {

    OrderResponse order = getOrder(id);

    OrderCreatedEvent event = new OrderCreatedEvent(
            "ORDER_CREATED",
            order.id(),
            order.product(),
            order.quantity()
    );

    orderEventProducer.publishOrderCreated(event);
}
}

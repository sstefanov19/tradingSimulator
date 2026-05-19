package com.example.tradingsimulator;

import com.example.tradingsimulator.dto.OrderRequestDto;
import com.example.tradingsimulator.model.Idempotency;
import com.example.tradingsimulator.model.Order;
import com.example.tradingsimulator.model.OrderType;
import com.example.tradingsimulator.repository.OrderRepository;
import com.example.tradingsimulator.repository.UserTransactionRepository;
import com.example.tradingsimulator.service.IdempotencyService;
import com.example.tradingsimulator.service.OrderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class OrderServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private UserTransactionRepository userTransactionRepository;
    @Mock private IdempotencyService idempotencyService;

    @InjectMocks private OrderService orderService;

    @Test
    void publishOrder_withNewIdempotencyKey_shouldSaveOrderAndReturnId() {
        String idempotencyKey = "key-abc-123";
        Long userId = 2L;
        BigDecimal quantity = new BigDecimal("1.00");

        when(idempotencyService.findById(idempotencyKey)).thenReturn(Optional.empty());

        Order savedOrder = new Order();
        savedOrder.setId(7L);
        when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);

        Long result = orderService.publishOrder(
                new OrderRequestDto(userId, "AAPL", quantity, OrderType.BUY), idempotencyKey
        );

        assertEquals(7L, result);
        verify(orderRepository).save(any(Order.class));
        verify(userTransactionRepository).save(any());
    }

    @Test
    void publishOrder_withDuplicateIdempotencyKey_shouldReturnExistingOrderIdWithoutProcessing() {
        String idempotencyKey = "key-abc-123";
        Long userId = 2L;

        when(idempotencyService.findById(idempotencyKey))
                .thenReturn(Optional.of(new Idempotency(idempotencyKey, 42L)));

        Long result = orderService.publishOrder(
                new OrderRequestDto(userId, "AAPL", new BigDecimal("1.00"), OrderType.BUY),
                idempotencyKey
        );

        assertEquals(42L, result);
        verify(orderRepository, never()).save(any());
        verify(userTransactionRepository, never()).save(any());
    }
}

package com.example.tradingsimulator;

import com.example.tradingsimulator.dto.OrderDto;
import com.example.tradingsimulator.dto.OrderRequestDto;
import com.example.tradingsimulator.dto.PriceDto;
import com.example.tradingsimulator.model.Holding;
import com.example.tradingsimulator.model.Idempotency;
import com.example.tradingsimulator.model.Order;
import com.example.tradingsimulator.model.OrderType;
import com.example.tradingsimulator.repository.HoldingRepository;
import com.example.tradingsimulator.repository.OrderRepository;
import com.example.tradingsimulator.service.EmailSenderService;
import com.example.tradingsimulator.service.IdempotencyService;
import com.example.tradingsimulator.service.OrderService;
import com.example.tradingsimulator.service.PriceTickerService;
import com.example.tradingsimulator.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class OrderServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private UserService userService;
    @Mock private HoldingRepository holdingRepository;
    @Mock private EmailSenderService emailSenderService;
    @Mock private PriceTickerService priceService;
    @Mock private IdempotencyService idempotencyService;

    @InjectMocks private OrderService orderService;

    @Test
    void testBuyOrder_withSufficientBalance_shouldSucceed() {
        Long userId = 2L;
        String ticker = "ETH";
        BigDecimal price = new BigDecimal("1.00");
        BigDecimal quantity = new BigDecimal("1.00");
        BigDecimal totalCost = price.multiply(quantity);
        BigDecimal balance = new BigDecimal("1000000.00");

        String idempotencyKey = "key-buy-test";
        when(idempotencyService.findById(idempotencyKey)).thenReturn(Optional.empty());
        when(priceService.getPrice(ticker)).thenReturn(new PriceDto(ticker, price));
        when(userService.getBalance(userId)).thenReturn(balance);
        when(holdingRepository.findByUserIdAndTicker(userId, ticker))
                .thenReturn(Optional.of(new Holding(userId, ticker, BigDecimal.ZERO)));
        when(userService.findEmail(userId)).thenReturn("example@gmail.com");

        Order savedOrder = new Order();
        savedOrder.setId(1L);
        savedOrder.setUserId(userId);
        when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);

        OrderDto result = orderService.placeOrder(
                new OrderRequestDto(userId, ticker, quantity, OrderType.BUY), idempotencyKey
        );

        String expectedEmail = "You have successfully bought 1.00 shares of ETH for a total cost of $1.0000. Your new balance is: $1000000.00";
        assertEquals(userId, result.userId());
        verify(userService).decreaseBalance(userId, totalCost);
        verify(emailSenderService).sendEmail("example@gmail.com", "Order Confirmation", expectedEmail);
        verify(holdingRepository).save(any(Holding.class));
        verify(orderRepository).save(any(Order.class));
    }

    @Test
    void testPlaceOrder_withNewIdempotencyKey_shouldProcessOrderAndSaveKey() {
        String idempotencyKey = "key-abc-123";
        Long userId = 2L;
        BigDecimal price = new BigDecimal("150.00");
        BigDecimal quantity = new BigDecimal("1.00");

        when(idempotencyService.findById(idempotencyKey)).thenReturn(Optional.empty());
        when(priceService.getPrice("AAPL")).thenReturn(new PriceDto("AAPL", price));
        when(userService.getBalance(userId)).thenReturn(new BigDecimal("10000.00"));
        when(holdingRepository.findByUserIdAndTicker(userId, "AAPL")).thenReturn(Optional.empty());
        when(userService.findEmail(userId)).thenReturn("user@example.com");

        Order savedOrder = new Order();
        savedOrder.setId(7L);
        when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);

        orderService.placeOrder(new OrderRequestDto(userId, "AAPL", quantity, OrderType.BUY), idempotencyKey);

        // key must be persisted after order is saved, with the new order's id
        verify(idempotencyService).save(idempotencyKey, 7L);
    }

    @Test
    void testPlaceOrder_withDuplicateIdempotencyKey_shouldReturnCachedOrderWithoutProcessing() {
        String idempotencyKey = "key-abc-123";
        Long userId = 2L;

        Order existingOrder = new Order();
        existingOrder.setId(42L);
        existingOrder.setUserId(userId);
        existingOrder.setTicker("AAPL");
        existingOrder.setQuantity(new BigDecimal("1.00"));
        existingOrder.setOrderType(OrderType.BUY);
        existingOrder.setExecutionType("MARKET");
        existingOrder.setStatus("FILLED");
        existingOrder.setTimestamp(Instant.now());

        when(idempotencyService.findById(idempotencyKey))
                .thenReturn(Optional.of(new Idempotency(idempotencyKey, 42L)));
        when(orderRepository.findById(42L)).thenReturn(Optional.of(existingOrder));

        OrderDto result = orderService.placeOrder(
                new OrderRequestDto(userId, "AAPL", new BigDecimal("1.00"), OrderType.BUY),
                idempotencyKey
        );

        assertEquals(42L, result.orderId());
        // duplicate request must not re-process the order
        verify(orderRepository, never()).save(any());
        verify(userService, never()).decreaseBalance(any(), any());
        verify(idempotencyService, never()).save(any(), any());
    }
}

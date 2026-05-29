package com.example.tradingsimulator;

import com.example.tradingsimulator.dto.OrderRequestDto;
import com.example.tradingsimulator.dto.PriceDto;
import com.example.tradingsimulator.exception.InsufficientFundsException;
import com.example.tradingsimulator.exception.InsufficientHoldingsException;
import com.example.tradingsimulator.model.Holding;
import com.example.tradingsimulator.model.Idempotency;
import com.example.tradingsimulator.model.Order;
import com.example.tradingsimulator.model.OrderType;
import com.example.tradingsimulator.model.User;
import com.example.tradingsimulator.repository.HoldingRepository;
import com.example.tradingsimulator.repository.OrderRepository;
import com.example.tradingsimulator.repository.UserRepository;
import com.example.tradingsimulator.repository.UserTransactionRepository;
import com.example.tradingsimulator.service.IdempotencyService;
import com.example.tradingsimulator.service.OrderService;
import com.example.tradingsimulator.service.PriceTickerService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class OrderServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private UserTransactionRepository userTransactionRepository;
    @Mock private IdempotencyService idempotencyService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private PriceTickerService priceService;
    @Mock private UserRepository userRepository;
    @Mock private HoldingRepository holdingRepository;

    @InjectMocks private OrderService orderService;

    private User user(BigDecimal balance, BigDecimal reserved) {
        User u = new User();
        u.setId(2L);
        u.setBalance(balance);
        u.setReserved(reserved);
        return u;
    }

    private Holding holding(BigDecimal quantity, BigDecimal reservedQuantity) {
        Holding h = new Holding(2L, "bob", "AAPL", quantity);
        h.setReservedQuantity(reservedQuantity);
        return h;
    }

    @Test
    void publishOrder_withDuplicateIdempotencyKey_shouldReturnExistingOrderIdWithoutReserving() {
        when(idempotencyService.findById("key-abc-123"))
                .thenReturn(Optional.of(new Idempotency("key-abc-123", 42L)));

        Long result = orderService.publishOrder(
                new OrderRequestDto(2L, "AAPL", new BigDecimal("1.00"), OrderType.BUY), "key-abc-123");

        // A duplicate must be a no-op so retries never double-reserve funds.
        assertEquals(42L, result);
        verify(orderRepository, never()).save(any());
        verify(userTransactionRepository, never()).save(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void publishOrder_buy_shouldReserveFundsAndStampReservedAmountOnOrder() {
        when(idempotencyService.findById("k1")).thenReturn(Optional.empty());
        when(priceService.getPrice("AAPL")).thenReturn(new PriceDto("AAPL", new BigDecimal("10")));
        User u = user(new BigDecimal("100"), BigDecimal.ZERO);
        when(userRepository.findByIdWithLock(2L)).thenReturn(Optional.of(u));
        Order saved = new Order();
        saved.setId(7L);
        when(orderRepository.save(any(Order.class))).thenReturn(saved);

        Long result = orderService.publishOrder(
                new OrderRequestDto(2L, "AAPL", new BigDecimal("3"), OrderType.BUY), "k1");

        assertEquals(7L, result);
        // Reservation = price * qty must be escrowed up-front so concurrent orders can't oversell balance.
        assertEquals(0, u.getReserved().compareTo(new BigDecimal("30")));

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());
        // reservedAmount is what settlement later releases — it must equal the reserved cost.
        assertEquals(0, captor.getValue().getReservedAmount().compareTo(new BigDecimal("30")));
    }

    @Test
    void publishOrder_buy_whenAvailableLessThanCost_shouldRejectAndNotPersist() {
        when(idempotencyService.findById("k2")).thenReturn(Optional.empty());
        when(priceService.getPrice("AAPL")).thenReturn(new PriceDto("AAPL", new BigDecimal("10")));
        // balance 100 but 95 already reserved by in-flight orders => only 5 available, order needs 30.
        when(userRepository.findByIdWithLock(2L))
                .thenReturn(Optional.of(user(new BigDecimal("100"), new BigDecimal("95"))));

        assertThrows(InsufficientFundsException.class, () -> orderService.publishOrder(
                new OrderRequestDto(2L, "AAPL", new BigDecimal("3"), OrderType.BUY), "k2"));

        // Admission must refuse an order it cannot honor — nothing reaches the outbox.
        verify(orderRepository, never()).save(any());
        verify(userTransactionRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void publishOrder_sell_shouldReserveQuantityAndLeaveReservedAmountNull() {
        when(idempotencyService.findById("k3")).thenReturn(Optional.empty());
        Holding h = holding(new BigDecimal("5"), BigDecimal.ZERO);
        when(holdingRepository.findByUserIdAndTicker(2L, "AAPL")).thenReturn(Optional.of(h));
        Order saved = new Order();
        saved.setId(9L);
        when(orderRepository.save(any(Order.class))).thenReturn(saved);

        orderService.publishOrder(
                new OrderRequestDto(2L, "AAPL", new BigDecimal("2"), OrderType.SELL), "k3");

        // Selling reserves units, not cash, so two sells can't dump more than is held.
        assertEquals(0, h.getReservedQuantity().compareTo(new BigDecimal("2")));

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());
        assertNull(captor.getValue().getReservedAmount());
    }

    @Test
    void publishOrder_sell_whenAvailableQuantityTooLow_shouldReject() {
        when(idempotencyService.findById("k4")).thenReturn(Optional.empty());
        // hold 5 but 4 already reserved by an in-flight sell => only 1 available, order wants 2.
        when(holdingRepository.findByUserIdAndTicker(2L, "AAPL"))
                .thenReturn(Optional.of(holding(new BigDecimal("5"), new BigDecimal("4"))));

        assertThrows(InsufficientHoldingsException.class, () -> orderService.publishOrder(
                new OrderRequestDto(2L, "AAPL", new BigDecimal("2"), OrderType.SELL), "k4"));

        verify(orderRepository, never()).save(any());
    }
}

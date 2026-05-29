package com.example.tradingsimulator;

import com.example.tradingsimulator.dto.PriceDto;
import com.example.tradingsimulator.exception.InsufficientFundsException;
import com.example.tradingsimulator.kafka.OrderEvent;
import com.example.tradingsimulator.model.Holding;
import com.example.tradingsimulator.model.Order;
import com.example.tradingsimulator.model.OrderType;
import com.example.tradingsimulator.repository.HoldingRepository;
import com.example.tradingsimulator.repository.OrderRepository;
import com.example.tradingsimulator.service.IdempotencyService;
import com.example.tradingsimulator.service.OrderExecutionService;
import com.example.tradingsimulator.service.PriceTickerService;
import com.example.tradingsimulator.service.UserService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class OrderExecutionServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private UserService userService;
    @Mock private HoldingRepository holdingRepository;
    @Mock private PriceTickerService priceService;
    @Mock private IdempotencyService idempotencyService;
    @Mock private PlatformTransactionManager txManager;

    private OrderExecutionService service;

    @BeforeEach
    void setUp() {
        // Real TransactionTemplate over a stub manager: getTransaction/commit/rollback are no-ops,
        // so we exercise the same commit-vs-rollback branching the production code relies on.
        when(txManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        service = new OrderExecutionService(orderRepository, userService, holdingRepository,
                priceService, idempotencyService, txManager, new SimpleMeterRegistry());
    }

    private OrderEvent event(OrderType type, BigDecimal qty) {
        return new OrderEvent(1L, 2L, "AAPL", qty, type, "idem-1", Instant.now());
    }

    private Order order(OrderType type, BigDecimal reservedAmount) {
        Order o = new Order();
        o.setId(1L);
        o.setUserId(2L);
        o.setOrderType(type);
        o.setReservedAmount(reservedAmount);
        o.setStatus("PENDING");
        return o;
    }

    @Test
    void executeOrder_buyFill_settlesReservationDebitsActualCostAndCreditsHolding() {
        Order order = order(OrderType.BUY, new BigDecimal("30"));
        when(idempotencyService.findById("idem-1")).thenReturn(Optional.empty());
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(priceService.getPrice("AAPL")).thenReturn(new PriceDto("AAPL", new BigDecimal("10")));
        when(userService.getUsername(2L)).thenReturn("bob");
        when(holdingRepository.findByUserIdAndTicker(2L, "AAPL")).thenReturn(Optional.empty());

        service.executeOrder(event(OrderType.BUY, new BigDecimal("3")));

        // Settlement debits the actual cost (10*3) and frees exactly the reserved amount (30).
        verify(userService).settleBuy(eq(2L), eq(new BigDecimal("30")), eq(new BigDecimal("30")));

        ArgumentCaptor<Holding> holdingCaptor = ArgumentCaptor.forClass(Holding.class);
        verify(holdingRepository).save(holdingCaptor.capture());
        assertEquals(0, holdingCaptor.getValue().getQuantity().compareTo(new BigDecimal("3")));

        assertEquals("FILLED", order.getStatus());
        // Idempotency stamped only on success, so a redelivery is recognised as a duplicate.
        verify(idempotencyService).save("idem-1", 1L);
    }

    @Test
    void executeOrder_buy_whenSettlementRejected_releasesReservationAndMarksRejected() {
        Order order = order(OrderType.BUY, new BigDecimal("30"));
        when(idempotencyService.findById("idem-1")).thenReturn(Optional.empty());
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(priceService.getPrice("AAPL")).thenReturn(new PriceDto("AAPL", new BigDecimal("10")));
        doThrow(new InsufficientFundsException("price rose past reservation"))
                .when(userService).settleBuy(any(), any(), any());

        // Business rejection is terminal — swallowed, not rethrown, so the Kafka offset advances.
        service.executeOrder(event(OrderType.BUY, new BigDecimal("3")));

        // The escrow must be returned exactly, never leaked.
        verify(userService).releaseReservation(2L, new BigDecimal("30"));
        assertEquals("REJECTED", order.getStatus());
        // A rejected order never settles, so idempotency must NOT be recorded.
        verify(idempotencyService, never()).save(any(), any());
    }

    @Test
    void executeOrder_sellFill_decrementsReservedQuantityAndCreditsBalance() {
        Order order = order(OrderType.SELL, null);
        Holding holding = new Holding(2L, "bob", "AAPL", new BigDecimal("5"));
        holding.setReservedQuantity(new BigDecimal("2"));
        when(idempotencyService.findById("idem-1")).thenReturn(Optional.empty());
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(priceService.getPrice("AAPL")).thenReturn(new PriceDto("AAPL", new BigDecimal("10")));
        when(userService.getUsername(2L)).thenReturn("bob");
        when(holdingRepository.findByUserIdAndTicker(2L, "AAPL")).thenReturn(Optional.of(holding));

        service.executeOrder(event(OrderType.SELL, new BigDecimal("2")));

        verify(userService).increaseBalance(2L, new BigDecimal("20"));
        // Both quantity and its reservation drop by the sold amount, keeping available-to-sell honest.
        ArgumentCaptor<Holding> holdingCaptor = ArgumentCaptor.forClass(Holding.class);
        verify(holdingRepository).save(holdingCaptor.capture());
        assertEquals(0, holdingCaptor.getValue().getQuantity().compareTo(new BigDecimal("3")));
        assertEquals(0, holdingCaptor.getValue().getReservedQuantity().compareTo(BigDecimal.ZERO));
        assertEquals("FILLED", order.getStatus());
    }

    @Test
    void executeOrder_transientFailure_rethrowsAndKeepsReservation() {
        Order order = order(OrderType.BUY, new BigDecimal("30"));
        when(idempotencyService.findById("idem-1")).thenReturn(Optional.empty());
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        // Non-business failure (e.g. price feed down) must NOT be treated as a rejection.
        when(priceService.getPrice("AAPL")).thenThrow(new RuntimeException("price feed unavailable"));

        assertThrows(RuntimeException.class,
                () -> service.executeOrder(event(OrderType.BUY, new BigDecimal("3"))));

        // Reservation stays intact and the order is not rejected, so a retry can settle cleanly.
        verify(userService, never()).releaseReservation(any(), any());
        verify(idempotencyService, never()).save(any(), any());
    }
}

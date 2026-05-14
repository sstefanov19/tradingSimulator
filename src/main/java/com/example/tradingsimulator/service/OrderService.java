package com.example.tradingsimulator.service;

import com.example.tradingsimulator.dto.OrderDto;
import com.example.tradingsimulator.dto.OrderRequestDto;
import com.example.tradingsimulator.dto.PriceDto;
import com.example.tradingsimulator.model.Holding;
import com.example.tradingsimulator.model.Idempotency;
import com.example.tradingsimulator.model.Order;
import com.example.tradingsimulator.repository.HoldingRepository;
import com.example.tradingsimulator.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final HoldingRepository holdingRepository;
    private final EmailSenderService emailSenderService;
    private final PriceTickerService priceService;
    private final UserService userService;
    private final IdempotencyService idempotencyService;

    public OrderService(OrderRepository orderRepository,
                        UserService userService ,
                        HoldingRepository holdingRepository,
                        EmailSenderService emailSenderService,
                        PriceTickerService priceService,
                        IdempotencyService idempotencyService) {
        this.orderRepository = orderRepository;
        this.userService = userService;
        this.holdingRepository = holdingRepository;
        this.priceService = priceService;
        this.emailSenderService = emailSenderService;
        this.idempotencyService = idempotencyService;
    }

    @Transactional
    public OrderDto placeOrder(OrderRequestDto request, String idempotencyKey) {
        Optional<Idempotency> existing = idempotencyService.findById(idempotencyKey);
        if (existing.isPresent()) {
            Order order = orderRepository.findById(existing.get().getOrderId())
                    .orElseThrow(() -> new RuntimeException("Order not found for idempotency key"));
            return new OrderDto(
                    order.getId(),
                    order.getUserId(),
                    order.getTicker(),
                    order.getQuantity(),
                    order.getOrderType(),
                    order.getExecutionType(),
                    order.getStatus(),
                    order.getTimestamp()
            );
        }

        PriceDto priceDto = priceService.getPrice(request.ticker());
        BigDecimal totalCost = priceDto.price().multiply(request.quantity());

        switch (request.orderType()) {
            case BUY -> buyOrder(request, totalCost);
            case SELL -> sellOrder(request, totalCost);
        }

        Order order = new Order(
                request.userId(),
                request.ticker(),
                request.quantity(),
                totalCost,
                request.orderType(),
                "MARKET",
                "FILLED",
                Instant.now()
        );

        Order savedOrder = orderRepository.save(order);

        idempotencyService.save(idempotencyKey, savedOrder.getId());

        return new OrderDto(
                savedOrder.getId(),
                savedOrder.getUserId(),
                savedOrder.getTicker(),
                savedOrder.getQuantity(),
                savedOrder.getOrderType(),
                savedOrder.getExecutionType(),
                savedOrder.getStatus(),
                savedOrder.getTimestamp()
        );
    }

    private void buyOrder(OrderRequestDto request, BigDecimal totalCost) {
        BigDecimal userBalance = userService.getBalance(request.userId());
        if (userBalance.compareTo(totalCost) < 0) {
            throw new RuntimeException("Insufficient balance!");
        }

        userService.decreaseBalance(request.userId(), totalCost);

        Holding holding = holdingRepository
                .findByUserIdAndTicker(request.userId(), request.ticker())
                .orElse(new Holding(request.userId(), request.ticker(), BigDecimal.ZERO));

        holding.setQuantity(holding.getQuantity().add(request.quantity()));
        holdingRepository.save(holding);

        String emailBody = "You have successfully bought " + request.quantity() + " shares of " + request.ticker() +
                " for a total cost of $" + totalCost + ". Your new balance is: $" + userService.getBalance(request.userId());
        emailSenderService.sendEmail(userService.findEmail(request.userId()), "Order Confirmation", emailBody);
    }

    private void sellOrder(OrderRequestDto request , BigDecimal totalCost) {
        Holding holding = holdingRepository
                .findByUserIdAndTicker(request.userId(), request.ticker())
                .orElse(new Holding(request.userId(), request.ticker(), BigDecimal.ZERO));


        if(request.quantity().compareTo(holding.getQuantity()) > 0) {
            throw new RuntimeException("Insufficient quantity!");
        }

        userService.increaseBalance(request.userId(), totalCost);

        holding.setQuantity(holding.getQuantity().subtract(request.quantity()));

        if(holding.getQuantity().compareTo(BigDecimal.ZERO) == 0) {
            holdingRepository.delete(holding);
        }else {
            holdingRepository.save(holding);
        }
    }
}

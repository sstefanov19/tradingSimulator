package com.example.tradingsimulator.service;

import com.example.tradingsimulator.dto.OrderDto;
import com.example.tradingsimulator.model.Holding;
import com.example.tradingsimulator.model.Order;
import com.example.tradingsimulator.model.OrderType;
import com.example.tradingsimulator.repository.HoldingRepository;
import com.example.tradingsimulator.repository.OrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final HoldingRepository holdingRepository;
    @Autowired
    private final EmailSenderService emailSenderService;
    private final UserService userService;

    public OrderService(OrderRepository orderRepository,
                        UserService userService ,
                        HoldingRepository holdingRepository,
                        EmailSenderService emailSenderService) {
        this.orderRepository = orderRepository;
        this.userService = userService;
        this.holdingRepository = holdingRepository;
        this.emailSenderService = emailSenderService;
    }

    public OrderDto placeOrder(String userId,
                               String ticker,
                               BigDecimal quantity,
                               OrderType orderType,
                               BigDecimal totalCost) {

        if(orderType.equals(OrderType.BUY)){
            BigDecimal userBalance = userService.getBalance(userId);

            if (userBalance.compareTo(totalCost) < 0) {
                throw new RuntimeException("Insufficient balance!");
            }

            userService.decreaseBalance(userId, totalCost);

            Holding holding = holdingRepository
                    .findByUserIdAndTicker(Long.parseLong(userId), ticker)
                    .orElse(new Holding(Long.parseLong(userId), ticker, BigDecimal.ZERO));

            holding.setQuantity(holding.getQuantity().add(quantity));
            holdingRepository.save(holding);

            String emailBody = "You have successfully bought " + quantity + " shares of " + ticker +
                    " for a total cost of $" + totalCost + ". Your new balance is: $" + userService.getBalance(userId);
            emailSenderService.sendEmail(userService.findEmail(userId), "Order Confirmation", emailBody);
        }


        if(orderType.equals(OrderType.SELL)){
            userService.increaseBalance(userId, totalCost);

            Holding holding = holdingRepository
                    .findByUserIdAndTicker(Long.parseLong(userId), ticker)
                    .orElse(new Holding(Long.parseLong(userId), ticker, BigDecimal.ZERO));

            if(quantity.compareTo(holding.getQuantity()) > 0) {
                throw new RuntimeException("Insufficient quantity!");
            }

            holding.setQuantity(holding.getQuantity().subtract(quantity));

            if(holding.getQuantity().compareTo(BigDecimal.ZERO) == 0) {
                holdingRepository.delete(holding);
            }else {
            holdingRepository.save(holding);
            }
        }

        Order order = new Order(
                userId,
                ticker,
                quantity,
                totalCost,
                orderType,
                "MARKET",
                "FILLED",
                Instant.now()
        );

        Order savedOrder = orderRepository.save(order);

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
}

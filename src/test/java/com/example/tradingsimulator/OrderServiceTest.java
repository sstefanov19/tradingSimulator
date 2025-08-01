package com.example.tradingsimulator;

import com.example.tradingsimulator.dto.OrderDto;
import com.example.tradingsimulator.model.Holding;
import com.example.tradingsimulator.model.Order;
import com.example.tradingsimulator.model.OrderType;
import com.example.tradingsimulator.repository.HoldingRepository;
import com.example.tradingsimulator.repository.OrderRepository;
import com.example.tradingsimulator.service.EmailSenderService;
import com.example.tradingsimulator.service.OrderService;
import com.example.tradingsimulator.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class OrderServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private UserService userService;
    @Mock private HoldingRepository holdingRepository;
    @Mock private EmailSenderService emailSenderService;

    @InjectMocks private OrderService orderService;

    @Test
    void testBuyOrder_withSufficientBalance_shouldSucceed() {
        String userId =  "2";
        String ticker =  "ETH";
        String userEmail  = "example@gmail.com";
        BigDecimal price = new BigDecimal("1.00");
        BigDecimal quantity = new BigDecimal("1.00");
        BigDecimal totalCost = price.multiply(quantity);
        BigDecimal balance = new BigDecimal("1000000.00");

        when(userService.getBalance(userId))
                .thenReturn(balance);
        when(holdingRepository.findByUserIdAndTicker(2L , ticker))
                .thenReturn(Optional.of(new Holding(
                        2L,
                        ticker,
                        BigDecimal.ZERO
                )));
        when(userService.findEmail(userId)).thenReturn(userEmail);

        Order order = new Order();
        order.setId(1L);
        order.setUserId(userId);
        when(orderRepository.save(any(Order.class)))
                .thenReturn(order);

        OrderDto result = orderService.placeOrder(
                userId , ticker , quantity , OrderType.BUY ,  totalCost
        );

        emailSenderService.sendEmail(
                "example@gmail.com",
                "Order Confirmed",
                "Thank you for your purchase"
        );

        String message = "You have successfully bought 1.00 shares of ETH for a total cost of $1.0000. Your new balance is: $1000000.00";

        assertEquals(userId , result.getUserId());
        verify(userService).decreaseBalance(userId , totalCost);
        verify(emailSenderService).sendEmail("example@gmail.com" , "Order Confirmation",  message);
        verify(holdingRepository).save(any(Holding.class));
        verify(orderRepository).save(any(Order.class));
    }

}

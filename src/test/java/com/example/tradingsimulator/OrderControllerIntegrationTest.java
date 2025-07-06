package com.example.tradingsimulator;

import com.example.tradingsimulator.dto.OrderRequestDto;
import com.example.tradingsimulator.model.OrderType;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.MediaType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
public class OrderControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;

    @Autowired private ObjectMapper objectMapper;

    @Test
    void placeOrder_shouldReturn200_whenValid() throws Exception {
        OrderRequestDto request = new OrderRequestDto();
        request.setUserId("2");
        request.setTicker("AAPL");
        request.setOrderType(OrderType.SELL);
        request.setQuantity(new BigDecimal("1"));

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(String.valueOf(MediaType.APPLICATION_JSON))
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }
}

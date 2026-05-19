package com.example.tradingsimulator;

import com.example.tradingsimulator.dto.OrderRequestDto;
import com.example.tradingsimulator.model.OrderType;
import com.example.tradingsimulator.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.MediaType;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
public class OrderControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;
    @MockBean
    private UserService userService;

    @BeforeEach
    void setUp() {
        Mockito.when(userService.findEmail(9999L)).thenReturn("test@exmample.com");
    }

    @Test
    void placeOrder_shouldReturn200_whenValid() throws Exception {
        OrderRequestDto request = new OrderRequestDto(9999L, "AAPL", new BigDecimal("1"), OrderType.BUY);

        mockMvc.perform(post("/api/v1/orders")
                .contentType(String.valueOf(MediaType.APPLICATION_JSON))
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }
}

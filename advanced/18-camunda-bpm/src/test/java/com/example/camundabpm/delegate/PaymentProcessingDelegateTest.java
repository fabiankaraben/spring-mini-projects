package com.example.camundabpm.delegate;

import com.example.camundabpm.domain.Order;
import com.example.camundabpm.domain.OrderStatus;
import com.example.camundabpm.repository.OrderRepository;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PaymentProcessingDelegate}.
 *
 * <p>Tests the payment computation logic and the process variable outputs
 * without requiring a Spring context or database.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentProcessingDelegate")
class PaymentProcessingDelegateTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private DelegateExecution execution;

    private PaymentProcessingDelegate delegate;

    @BeforeEach
    void setUp() {
        delegate = new PaymentProcessingDelegate(orderRepository);
    }

    /**
     * Happy path: computes totalAmount = quantity × unitPrice, updates status,
     * and sets paymentSuccess=true.
     */
    @Test
    @DisplayName("computes total, sets PAYMENT_PROCESSED status and paymentSuccess=true")
    void execute_validOrder_processesPaymentSuccessfully() throws Exception {
        // Arrange
        Long orderId = 10L;
        Order order = new Order("Alice", "Laptop", 3, new BigDecimal("499.99"));

        when(execution.getVariable("orderId")).thenReturn(orderId);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        delegate.execute(execution);

        // Assert: order was saved with correct total and status
        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());

        Order saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(OrderStatus.PAYMENT_PROCESSED);
        // totalAmount = 3 × 499.99 = 1499.97
        assertThat(saved.getTotalAmount()).isEqualByComparingTo("1499.97");

        // Assert: process variables were set correctly
        verify(execution).setVariable(PaymentProcessingDelegate.VAR_PAYMENT_SUCCESS, true);
        verify(execution).setVariable(
                PaymentProcessingDelegate.VAR_TOTAL_AMOUNT,
                new BigDecimal("1499.97").toPlainString());
    }

    /**
     * Tests that the total amount is correctly calculated for a single unit.
     */
    @Test
    @DisplayName("calculates total correctly for single unit")
    void execute_singleUnit_totalEqualsUnitPrice() throws Exception {
        // Arrange
        Long orderId = 11L;
        Order order = new Order("Bob", "USB-C Hub", 1, new BigDecimal("39.99"));

        when(execution.getVariable("orderId")).thenReturn(orderId);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        delegate.execute(execution);

        // Assert: total for 1 unit = unit price
        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());
        assertThat(captor.getValue().getTotalAmount()).isEqualByComparingTo("39.99");
    }

    /**
     * Failure path: order not found.
     */
    @Test
    @DisplayName("throws IllegalArgumentException when order is not found")
    void execute_orderNotFound_throwsException() {
        // Arrange
        Long orderId = 99L;
        when(execution.getVariable("orderId")).thenReturn(orderId);
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> delegate.execute(execution))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Order not found: 99");

        verify(orderRepository, never()).save(any());
    }
}

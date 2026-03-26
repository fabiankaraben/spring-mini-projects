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
 * Unit tests for {@link InventoryCheckDelegate}.
 *
 * <p>These tests use Mockito to mock out dependencies (OrderRepository and
 * DelegateExecution) so that the delegate's logic can be verified in complete
 * isolation — no Spring context, no database, no Camunda engine.
 *
 * <p>@ExtendWith(MockitoExtension.class) integrates Mockito with JUnit 5,
 * enabling @Mock and @InjectMocks field injection.
 *
 * <p>What is being tested:
 * <ul>
 *   <li>Happy path: delegate finds the order, sets inventoryAvailable=true,
 *       updates status to INVENTORY_CHECKED, and saves.</li>
 *   <li>Failure case: order not found — delegate throws IllegalArgumentException.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("InventoryCheckDelegate")
class InventoryCheckDelegateTest {

    /** Mocked repository — returns controlled Order instances. */
    @Mock
    private OrderRepository orderRepository;

    /** Mocked Camunda execution context — allows verifying variable writes. */
    @Mock
    private DelegateExecution execution;

    /** The class under test, constructed manually with the mocked repository. */
    private InventoryCheckDelegate delegate;

    @BeforeEach
    void setUp() {
        // Construct the delegate with the mocked dependency.
        // We don't use @InjectMocks here to be explicit about the construction.
        delegate = new InventoryCheckDelegate(orderRepository);
    }

    /**
     * Happy path: order exists with positive quantity.
     * Expects status update to INVENTORY_CHECKED and inventoryAvailable=true.
     */
    @Test
    @DisplayName("sets INVENTORY_CHECKED status and inventoryAvailable=true when stock is available")
    void execute_inventoryAvailable_setsCheckedStatus() throws Exception {
        // Arrange
        Long orderId = 42L;
        Order order = new Order("Alice", "Laptop", 2, new BigDecimal("999.99"));

        // Stub execution.getVariable("orderId") to return our test order ID
        when(execution.getVariable("orderId")).thenReturn(orderId);
        // Stub repository lookup to return the test order
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        // Stub save to return the order (real save would set the ID but not needed here)
        when(orderRepository.save(any(Order.class))).thenReturn(order);

        // Act
        delegate.execute(execution);

        // Assert: order status was updated to INVENTORY_CHECKED
        ArgumentCaptor<Order> savedOrder = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(savedOrder.capture());
        assertThat(savedOrder.getValue().getStatus()).isEqualTo(OrderStatus.INVENTORY_CHECKED);

        // Assert: the process variable was set to true
        verify(execution).setVariable(InventoryCheckDelegate.VAR_INVENTORY_AVAILABLE, true);
    }

    /**
     * Failure path: order is not found in the repository.
     * Expects an IllegalArgumentException to be thrown so Camunda can create an incident.
     */
    @Test
    @DisplayName("throws IllegalArgumentException when order is not found")
    void execute_orderNotFound_throwsException() {
        // Arrange
        Long orderId = 99L;
        when(execution.getVariable("orderId")).thenReturn(orderId);
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        // Act & Assert: the delegate must throw so Camunda handles the error
        assertThatThrownBy(() -> delegate.execute(execution))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Order not found: 99");

        // Assert: no save should have been called
        verify(orderRepository, never()).save(any());
    }
}

package com.example.grpcclient.service;

import com.example.grpcclient.domain.InventoryItem;
import com.example.grpcclient.repository.InventoryItemRepository;
import com.example.grpcclient.service.InventoryService.StockReservationResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link InventoryService}.
 *
 * <p>Tests focus purely on inventory business logic without a Spring context,
 * gRPC server, or database. The repository is mocked with Mockito.
 *
 * <p>Business rules verified:
 * <ul>
 *   <li>Reservations succeed only when available stock >= requested quantity.</li>
 *   <li>Reservations fail gracefully (return unchanged item) when stock is insufficient.</li>
 *   <li>Releases cap at the current reserved quantity (no negative reserved).</li>
 *   <li>Available quantity = total - reserved is always correctly computed.</li>
 *   <li>Non-positive quantity arguments are rejected with IllegalArgumentException.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("InventoryService Unit Tests")
class InventoryServiceTest {

    /**
     * Mocked repository — all calls intercepted by Mockito.
     */
    @Mock
    private InventoryItemRepository inventoryItemRepository;

    /**
     * The class under test.
     */
    @InjectMocks
    private InventoryService inventoryService;

    // =========================================================================
    // Tests for findBySku()
    // =========================================================================

    @Nested
    @DisplayName("findBySku()")
    class FindBySku {

        @Test
        @DisplayName("returns the inventory item when SKU exists")
        void returnsItemWhenFound() {
            // Given: an item exists for this SKU.
            InventoryItem item = new InventoryItem("SKU-A", "Laptop", 50, 5);
            when(inventoryItemRepository.findById("SKU-A")).thenReturn(Optional.of(item));

            // When: we look up the SKU.
            Optional<InventoryItem> result = inventoryService.findBySku("SKU-A");

            // Then: the item is found with correct data.
            assertThat(result).isPresent();
            assertThat(result.get().getSku()).isEqualTo("SKU-A");
            assertThat(result.get().getAvailableQuantity()).isEqualTo(45); // 50 - 5
        }

        @Test
        @DisplayName("returns empty Optional when SKU does not exist")
        void returnsEmptyWhenNotFound() {
            // Given: no item for this SKU.
            when(inventoryItemRepository.findById("SKU-UNKNOWN")).thenReturn(Optional.empty());

            // When: we look up the SKU.
            Optional<InventoryItem> result = inventoryService.findBySku("SKU-UNKNOWN");

            // Then: empty Optional.
            assertThat(result).isEmpty();
        }
    }

    // =========================================================================
    // Tests for reserveStock()
    // =========================================================================

    @Nested
    @DisplayName("reserveStock()")
    class ReserveStock {

        @Test
        @DisplayName("increments reserved quantity when sufficient stock is available")
        void incrementsReservedWhenSufficientStock() {
            // Given: 100 total, 10 reserved → 90 available.
            InventoryItem item = new InventoryItem("SKU-B", "Mouse", 100, 10);
            when(inventoryItemRepository.findById("SKU-B")).thenReturn(Optional.of(item));
            when(inventoryItemRepository.save(any(InventoryItem.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // When: we reserve 20 units.
            Optional<StockReservationResult> result = inventoryService.reserveStock("SKU-B", 20);

            // Then: reservation succeeded and reserved goes from 10 to 30 (10 + 20).
            assertThat(result).isPresent();
            assertThat(result.get().success()).isTrue();
            assertThat(result.get().item().getReservedQuantity()).isEqualTo(30);
            // Available goes from 90 to 70 (100 - 30).
            assertThat(result.get().item().getAvailableQuantity()).isEqualTo(70);
        }

        @Test
        @DisplayName("returns item unchanged when requested quantity exceeds available stock")
        void returnsItemUnchangedWhenInsufficientStock() {
            // Given: 10 total, 8 reserved → only 2 available.
            InventoryItem item = new InventoryItem("SKU-C", "Keyboard", 10, 8);
            when(inventoryItemRepository.findById("SKU-C")).thenReturn(Optional.of(item));

            // When: we try to reserve 5 units (but only 2 available).
            Optional<StockReservationResult> result = inventoryService.reserveStock("SKU-C", 5);

            // Then: result is present but success=false (insufficient stock).
            assertThat(result).isPresent();
            assertThat(result.get().success()).isFalse();
            // Item is returned unchanged: reserved still 8, available still 2.
            assertThat(result.get().item().getReservedQuantity()).isEqualTo(8);
            assertThat(result.get().item().getAvailableQuantity()).isEqualTo(2);

            // Repository.save() should NOT be called on failure.
            verify(inventoryItemRepository, never()).save(any());
        }

        @Test
        @DisplayName("returns empty Optional when SKU does not exist")
        void returnsEmptyWhenSkuNotFound() {
            // Given: no item with this SKU.
            when(inventoryItemRepository.findById("SKU-MISSING")).thenReturn(Optional.empty());

            // When: we try to reserve.
            Optional<StockReservationResult> result = inventoryService.reserveStock("SKU-MISSING", 3);

            // Then: empty Optional (SKU not found).
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("throws IllegalArgumentException for non-positive quantity")
        void throwsForNonPositiveQuantity() {
            // When + Then: zero quantity throws.
            assertThatThrownBy(() -> inventoryService.reserveStock("SKU-D", 0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("positive");

            // And: negative quantity throws.
            assertThatThrownBy(() -> inventoryService.reserveStock("SKU-D", -1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("positive");

            // Repository should never be called.
            verify(inventoryItemRepository, never()).findById(any());
        }

        @Test
        @DisplayName("allows reserving exactly all available stock")
        void allowsReservingExactlyAllAvailable() {
            // Given: 5 total, 0 reserved → 5 available.
            InventoryItem item = new InventoryItem("SKU-E", "GPU", 5, 0);
            when(inventoryItemRepository.findById("SKU-E")).thenReturn(Optional.of(item));
            when(inventoryItemRepository.save(any(InventoryItem.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // When: we reserve all 5 units.
            Optional<StockReservationResult> result = inventoryService.reserveStock("SKU-E", 5);

            // Then: all units are reserved, available = 0, success=true.
            assertThat(result).isPresent();
            assertThat(result.get().success()).isTrue();
            assertThat(result.get().item().getReservedQuantity()).isEqualTo(5);
            assertThat(result.get().item().getAvailableQuantity()).isEqualTo(0);
        }
    }

    // =========================================================================
    // Tests for releaseStock()
    // =========================================================================

    @Nested
    @DisplayName("releaseStock()")
    class ReleaseStock {

        @Test
        @DisplayName("decrements reserved quantity when releasing stock")
        void decrementsReservedOnRelease() {
            // Given: 100 total, 30 reserved → 70 available.
            InventoryItem item = new InventoryItem("SKU-F", "Monitor", 100, 30);
            when(inventoryItemRepository.findById("SKU-F")).thenReturn(Optional.of(item));
            when(inventoryItemRepository.save(any(InventoryItem.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // When: we release 10 units.
            Optional<InventoryItem> result = inventoryService.releaseStock("SKU-F", 10);

            // Then: reserved goes from 30 to 20 (30 - 10).
            assertThat(result).isPresent();
            assertThat(result.get().getReservedQuantity()).isEqualTo(20);
            // Available goes from 70 to 80 (100 - 20).
            assertThat(result.get().getAvailableQuantity()).isEqualTo(80);
        }

        @Test
        @DisplayName("caps release at current reserved quantity (prevents negative reserved)")
        void capsReleaseAtCurrentReserved() {
            // Given: only 3 units reserved.
            InventoryItem item = new InventoryItem("SKU-G", "Webcam", 50, 3);
            when(inventoryItemRepository.findById("SKU-G")).thenReturn(Optional.of(item));
            when(inventoryItemRepository.save(any(InventoryItem.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // When: we try to release 10 units (more than the 3 currently reserved).
            Optional<InventoryItem> result = inventoryService.releaseStock("SKU-G", 10);

            // Then: only 3 are released (capped at reservedQuantity), reserved goes to 0.
            assertThat(result).isPresent();
            assertThat(result.get().getReservedQuantity()).isEqualTo(0);
        }

        @Test
        @DisplayName("returns empty Optional when SKU does not exist")
        void returnsEmptyWhenSkuNotFound() {
            // Given: no item for this SKU.
            when(inventoryItemRepository.findById("SKU-GONE")).thenReturn(Optional.empty());

            // When: we try to release.
            Optional<InventoryItem> result = inventoryService.releaseStock("SKU-GONE", 5);

            // Then: empty Optional.
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("throws IllegalArgumentException for non-positive quantity")
        void throwsForNonPositiveQuantity() {
            assertThatThrownBy(() -> inventoryService.releaseStock("SKU-H", 0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("positive");

            verify(inventoryItemRepository, never()).findById(any());
        }
    }

    // =========================================================================
    // Tests for findAll()
    // =========================================================================

    @Nested
    @DisplayName("findAll()")
    class FindAll {

        @Test
        @DisplayName("returns all items when onlyAvailable=false")
        void returnsAllItemsWhenNotFiltered() {
            // Given: two items — one available, one fully reserved.
            List<InventoryItem> allItems = List.of(
                    new InventoryItem("SKU-X", "ItemX", 10, 0),  // 10 available
                    new InventoryItem("SKU-Y", "ItemY", 5, 5)    // 0 available (fully reserved)
            );
            when(inventoryItemRepository.findAll()).thenReturn(allItems);

            // When: we request all items.
            List<InventoryItem> result = inventoryService.findAll(false);

            // Then: both items are returned including the fully reserved one.
            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("filters to only available items when onlyAvailable=true")
        void returnsOnlyAvailableItemsWhenFiltered() {
            // Given: two items — one available (10 avail), one fully reserved (0 avail).
            List<InventoryItem> allItems = List.of(
                    new InventoryItem("SKU-X", "ItemX", 10, 0),  // 10 available
                    new InventoryItem("SKU-Y", "ItemY", 5, 5)    // 0 available (fully reserved)
            );
            when(inventoryItemRepository.findAll()).thenReturn(allItems);

            // When: we request only available items.
            List<InventoryItem> result = inventoryService.findAll(true);

            // Then: only the item with available stock > 0 is returned.
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getSku()).isEqualTo("SKU-X");
        }
    }
}

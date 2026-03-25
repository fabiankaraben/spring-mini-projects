package com.example.grpc.mapper;

import com.example.grpc.domain.Product;
import com.example.grpc.domain.ProductStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ProductMapper}.
 *
 * <p>The mapper has no Spring dependencies — it only converts between plain Java objects
 * and protobuf message builders. We can therefore test it with a plain {@code new}
 * instantiation, without any Spring context or Mockito involvement.
 *
 * <p>Tests verify:
 * <ul>
 *   <li>All fields from a JPA {@link Product} entity are correctly reflected in the
 *       generated protobuf {@link com.example.grpc.proto.Product} message.</li>
 *   <li>Null string fields in the entity are safely converted to empty strings in the
 *       protobuf message (proto3 strings default to "").</li>
 *   <li>Every {@link ProductStatus} enum value maps to the correct proto enum value.</li>
 *   <li>Every proto enum value maps back to the correct {@link ProductStatus} enum value.</li>
 * </ul>
 */
@DisplayName("ProductMapper Unit Tests")
class ProductMapperTest {

    /** The class under test — instantiated directly (no Spring context needed). */
    private ProductMapper productMapper;

    @BeforeEach
    void setUp() {
        // Plain instantiation — no Spring context or mocks required.
        productMapper = new ProductMapper();
    }

    // =========================================================================
    // Tests for toProto()
    // =========================================================================

    @Test
    @DisplayName("maps all fields from JPA entity to protobuf message correctly")
    void mapsAllFieldsToProto() {
        // Given: a fully-populated JPA product entity.
        Product product = buildProduct(42L, "Keyboard", "Mechanical keyboard", "electronics", 89.99, 100, ProductStatus.ACTIVE);

        // When: we convert it to a protobuf message.
        com.example.grpc.proto.Product proto = productMapper.toProto(product);

        // Then: all fields are correctly reflected in the protobuf message.
        assertThat(proto.getId()).isEqualTo(42L);
        assertThat(proto.getName()).isEqualTo("Keyboard");
        assertThat(proto.getDescription()).isEqualTo("Mechanical keyboard");
        assertThat(proto.getCategory()).isEqualTo("electronics");
        assertThat(proto.getPrice()).isEqualTo(89.99);
        assertThat(proto.getStockQuantity()).isEqualTo(100);
        assertThat(proto.getStatus()).isEqualTo(com.example.grpc.proto.ProductStatus.PRODUCT_STATUS_ACTIVE);
    }

    @Test
    @DisplayName("converts null description to empty string in protobuf message")
    void convertsNullDescriptionToEmptyString() {
        // Given: a product with a null description.
        Product product = buildProduct(1L, "Chair", null, "furniture", 299.00, 5, ProductStatus.ACTIVE);

        // When: converted to proto.
        com.example.grpc.proto.Product proto = productMapper.toProto(product);

        // Then: description is empty string, not null (proto3 string default).
        assertThat(proto.getDescription()).isEqualTo("");
    }

    @Test
    @DisplayName("converts null category to empty string in protobuf message")
    void convertsNullCategoryToEmptyString() {
        // Given: a product with a null category.
        Product product = buildProduct(1L, "Widget", "A widget", null, 9.99, 10, ProductStatus.ACTIVE);

        // When: converted to proto.
        com.example.grpc.proto.Product proto = productMapper.toProto(product);

        // Then: category is empty string.
        assertThat(proto.getCategory()).isEqualTo("");
    }

    @Test
    @DisplayName("maps OUT_OF_STOCK status correctly")
    void mapsOutOfStockStatus() {
        // Given: an out-of-stock product.
        Product product = buildProduct(2L, "Headphones", "ANC headphones", "electronics", 249.99, 0, ProductStatus.OUT_OF_STOCK);

        // When: converted to proto.
        com.example.grpc.proto.Product proto = productMapper.toProto(product);

        // Then: status is OUT_OF_STOCK in the proto message.
        assertThat(proto.getStatus()).isEqualTo(com.example.grpc.proto.ProductStatus.PRODUCT_STATUS_OUT_OF_STOCK);
        assertThat(proto.getStockQuantity()).isEqualTo(0);
    }

    @Test
    @DisplayName("maps DISCONTINUED status correctly")
    void mapsDiscontinuedStatus() {
        // Given: a discontinued product.
        Product product = buildProduct(3L, "Old Phone", "Outdated phone", "electronics", 1.00, 0, ProductStatus.DISCONTINUED);

        // When: converted to proto.
        com.example.grpc.proto.Product proto = productMapper.toProto(product);

        // Then: status is DISCONTINUED in the proto message.
        assertThat(proto.getStatus()).isEqualTo(com.example.grpc.proto.ProductStatus.PRODUCT_STATUS_DISCONTINUED);
    }

    // =========================================================================
    // Tests for toProtoStatus() and fromProtoStatus()
    // =========================================================================

    @Test
    @DisplayName("toProtoStatus: ACTIVE maps to PRODUCT_STATUS_ACTIVE")
    void activeToProtoStatus() {
        assertThat(productMapper.toProtoStatus(ProductStatus.ACTIVE))
                .isEqualTo(com.example.grpc.proto.ProductStatus.PRODUCT_STATUS_ACTIVE);
    }

    @Test
    @DisplayName("toProtoStatus: OUT_OF_STOCK maps to PRODUCT_STATUS_OUT_OF_STOCK")
    void outOfStockToProtoStatus() {
        assertThat(productMapper.toProtoStatus(ProductStatus.OUT_OF_STOCK))
                .isEqualTo(com.example.grpc.proto.ProductStatus.PRODUCT_STATUS_OUT_OF_STOCK);
    }

    @Test
    @DisplayName("toProtoStatus: DISCONTINUED maps to PRODUCT_STATUS_DISCONTINUED")
    void discontinuedToProtoStatus() {
        assertThat(productMapper.toProtoStatus(ProductStatus.DISCONTINUED))
                .isEqualTo(com.example.grpc.proto.ProductStatus.PRODUCT_STATUS_DISCONTINUED);
    }

    @Test
    @DisplayName("toProtoStatus: null maps to PRODUCT_STATUS_UNKNOWN")
    void nullToProtoStatusReturnsUnknown() {
        assertThat(productMapper.toProtoStatus(null))
                .isEqualTo(com.example.grpc.proto.ProductStatus.PRODUCT_STATUS_UNKNOWN);
    }

    @Test
    @DisplayName("fromProtoStatus: PRODUCT_STATUS_ACTIVE maps to ACTIVE")
    void protoActiveToJavaStatus() {
        assertThat(productMapper.fromProtoStatus(com.example.grpc.proto.ProductStatus.PRODUCT_STATUS_ACTIVE))
                .isEqualTo(ProductStatus.ACTIVE);
    }

    @Test
    @DisplayName("fromProtoStatus: PRODUCT_STATUS_OUT_OF_STOCK maps to OUT_OF_STOCK")
    void protoOutOfStockToJavaStatus() {
        assertThat(productMapper.fromProtoStatus(com.example.grpc.proto.ProductStatus.PRODUCT_STATUS_OUT_OF_STOCK))
                .isEqualTo(ProductStatus.OUT_OF_STOCK);
    }

    @Test
    @DisplayName("fromProtoStatus: PRODUCT_STATUS_DISCONTINUED maps to DISCONTINUED")
    void protoDiscontinuedToJavaStatus() {
        assertThat(productMapper.fromProtoStatus(com.example.grpc.proto.ProductStatus.PRODUCT_STATUS_DISCONTINUED))
                .isEqualTo(ProductStatus.DISCONTINUED);
    }

    @Test
    @DisplayName("fromProtoStatus: PRODUCT_STATUS_UNKNOWN maps to ACTIVE (safe default)")
    void protoUnknownToJavaStatusReturnsDefault() {
        assertThat(productMapper.fromProtoStatus(com.example.grpc.proto.ProductStatus.PRODUCT_STATUS_UNKNOWN))
                .isEqualTo(ProductStatus.ACTIVE);
    }

    // =========================================================================
    // Helper
    // =========================================================================

    /**
     * Build a {@link Product} with all fields populated via the constructor and
     * the ID set via reflection (JPA-managed field without a public setter).
     */
    private Product buildProduct(Long id, String name, String description, String category,
                                  Double price, Integer stock, ProductStatus status) {
        Product product = new Product(name, description, category, price, stock, status);
        try {
            var field = Product.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(product, id);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
        return product;
    }
}

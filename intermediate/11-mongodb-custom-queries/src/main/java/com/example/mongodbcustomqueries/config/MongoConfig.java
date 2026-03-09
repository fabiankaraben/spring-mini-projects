package com.example.mongodbcustomqueries.config;

import org.bson.types.Decimal128;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;

import java.math.BigDecimal;
import java.util.List;

/**
 * MongoDB custom conversion configuration.
 *
 * <p>By default, Spring Data MongoDB serialises {@link BigDecimal} fields as
 * plain strings (e.g. {@code "9.99"}). While strings round-trip correctly for
 * reads and writes, they are <em>not numeric</em> in MongoDB's type system, so
 * numeric comparison operators like {@code $lte}, {@code $gte}, {@code $lt},
 * and aggregation operators like {@code $sum}, {@code $avg} do not work correctly
 * on string-typed fields.
 *
 * <p>This configuration registers two custom converters that map:
 * <ul>
 *   <li>{@link BigDecimal} → {@link Decimal128} on write (INSERT / UPDATE):
 *       ensures the BSON document stores the value as a true numeric
 *       {@code Decimal128} type, enabling correct range queries and
 *       aggregation arithmetic.</li>
 *   <li>{@link Decimal128} → {@link BigDecimal} on read (FIND):
 *       converts the BSON {@code Decimal128} back to a Java {@code BigDecimal}
 *       with full precision when documents are deserialized from MongoDB.</li>
 * </ul>
 *
 * <p>{@link MongoCustomConversions} is the Spring Data hook that injects these
 * converters into the {@code MappingMongoConverter} used by all repository
 * and {@code MongoTemplate} operations.
 */
@Configuration
public class MongoConfig {

    /**
     * Register the custom converters with Spring Data MongoDB.
     *
     * <p>Spring Boot auto-detects this bean and uses it when building the
     * {@code MappingMongoConverter}. No additional wiring is required.
     *
     * @return a {@link MongoCustomConversions} instance containing both converters
     */
    @Bean
    public MongoCustomConversions mongoCustomConversions() {
        return new MongoCustomConversions(List.of(
                new BigDecimalToDecimal128Converter(),
                new Decimal128ToBigDecimalConverter()
        ));
    }

    // ── Writing converter: Java → BSON ────────────────────────────────────────────

    /**
     * Converts a Java {@link BigDecimal} to a BSON {@link Decimal128} for storage.
     *
     * <p>{@link WritingConverter} tells Spring Data MongoDB to use this converter
     * when serialising a {@code BigDecimal} field into a BSON document.
     *
     * <p>{@link Decimal128} is MongoDB's 128-bit IEEE 754-2008 decimal type; it
     * preserves the exact value and scale of the {@code BigDecimal}, making it
     * suitable for monetary values and enabling correct numeric comparisons and
     * aggregation operations.
     */
    @WritingConverter
    static class BigDecimalToDecimal128Converter implements Converter<BigDecimal, Decimal128> {
        @Override
        public Decimal128 convert(BigDecimal source) {
            // Decimal128 accepts a BigDecimal directly and creates an exact BSON numeric value.
            return new Decimal128(source);
        }
    }

    // ── Reading converter: BSON → Java ────────────────────────────────────────────

    /**
     * Converts a BSON {@link Decimal128} to a Java {@link BigDecimal} when reading.
     *
     * <p>{@link ReadingConverter} tells Spring Data MongoDB to use this converter
     * when deserialising a {@code Decimal128} BSON field into a Java object.
     *
     * <p>{@code Decimal128.bigDecimalValue()} returns the exact {@code BigDecimal}
     * equivalent of the stored BSON value, preserving scale and precision.
     */
    @ReadingConverter
    static class Decimal128ToBigDecimalConverter implements Converter<Decimal128, BigDecimal> {
        @Override
        public BigDecimal convert(Decimal128 source) {
            // bigDecimalValue() returns the exact BigDecimal representation
            return source.bigDecimalValue();
        }
    }
}

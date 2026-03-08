package com.fabiankaraben.spring.basic.jacksoncustomserializer.model;

import com.fabiankaraben.spring.basic.jacksoncustomserializer.serializer.MoneyDeserializer;
import com.fabiankaraben.spring.basic.jacksoncustomserializer.serializer.MoneySerializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.util.Objects;

public class Product {
    private String name;
    
    /**
     * Price in cents.
     * Custom serialization handles conversion to/from currency string.
     */
    @JsonSerialize(using = MoneySerializer.class)
    @JsonDeserialize(using = MoneyDeserializer.class)
    private Integer priceInCents;
    
    private String category;

    public Product() {
    }

    public Product(String name, Integer priceInCents, String category) {
        this.name = name;
        this.priceInCents = priceInCents;
        this.category = category;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getPriceInCents() {
        return priceInCents;
    }

    public void setPriceInCents(Integer priceInCents) {
        this.priceInCents = priceInCents;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Product product = (Product) o;
        return Objects.equals(name, product.name) &&
                Objects.equals(priceInCents, product.priceInCents) &&
                Objects.equals(category, product.category);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, priceInCents, category);
    }

    @Override
    public String toString() {
        return "Product{" +
                "name='" + name + '\'' +
                ", priceInCents=" + priceInCents +
                ", category='" + category + '\'' +
                '}';
    }
}

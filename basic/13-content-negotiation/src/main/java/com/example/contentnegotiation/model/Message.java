package com.example.contentnegotiation.model;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

/**
 * Message represents the data transferred via the REST API.
 * Using a Java Record (available since Java 14, standard in Java 16+)
 * provides a concise way to create immutable data carriers.
 *
 * The @JacksonXmlRootElement annotation explicitly names the root XML node
 * when this object is serialized to XML. If omitted, Jackson uses the class
 * name.
 */
@JacksonXmlRootElement(localName = "message")
public record Message(
        String id,
        String content) {
}

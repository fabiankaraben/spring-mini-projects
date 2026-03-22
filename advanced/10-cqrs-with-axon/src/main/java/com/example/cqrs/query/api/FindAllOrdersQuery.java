package com.example.cqrs.query.api;

/**
 * Query to retrieve all orders, optionally filtered by status.
 *
 * <p>When {@code status} is {@code null}, all orders are returned regardless of status.
 * When {@code status} is provided (e.g. "PLACED", "CONFIRMED", "CANCELLED"), only
 * orders with that status are returned.
 *
 * @param status optional status filter; {@code null} means "return all"
 */
public record FindAllOrdersQuery(String status) {}

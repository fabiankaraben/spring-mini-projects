package com.example.grpcclient.client;

/**
 * Application exception wrapping gRPC {@link io.grpc.StatusRuntimeException}.
 *
 * <p>Why wrap gRPC exceptions?
 * <ul>
 *   <li>Keeps the gRPC transport layer concern ({@code StatusRuntimeException}) from
 *       leaking into the REST controller and other application layers.</li>
 *   <li>Allows the REST exception handler to translate this into HTTP error responses
 *       without knowing anything about gRPC status codes.</li>
 *   <li>Makes the service interface cleaner — callers handle application exceptions,
 *       not transport-level exceptions.</li>
 * </ul>
 *
 * <p>The underlying {@link io.grpc.StatusRuntimeException} is preserved as the cause
 * so that logging and debugging still have access to the full gRPC status code and
 * description if needed.
 */
public class GrpcServiceException extends RuntimeException {

    /**
     * Construct a new exception with a descriptive message and the underlying cause.
     *
     * @param message a human-readable description of what operation failed
     * @param cause   the underlying {@link io.grpc.StatusRuntimeException} from the gRPC stub
     */
    public GrpcServiceException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Construct a new exception with only a descriptive message (no underlying cause).
     * Used when the error is detected at the application level rather than gRPC transport level.
     *
     * @param message a human-readable description of what failed
     */
    public GrpcServiceException(String message) {
        super(message);
    }
}

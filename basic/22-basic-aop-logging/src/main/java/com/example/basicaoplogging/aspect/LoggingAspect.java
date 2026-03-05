package com.example.basicaoplogging.aspect;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * Aspect class demonstrating Spring AOP logging mechanisms.
 * We can annotate any class with @Aspect to let Spring know it contains
 * pointcuts
 * and advices. We also annotate it with @Component so it becomes a managed
 * Bean.
 */
@Aspect
@Component
public class LoggingAspect {

    private static final Logger log = LoggerFactory.getLogger(LoggingAspect.class);

    /**
     * Declares a Pointcut focusing on all methods in the service package.
     * The expression matches any return type (first `*`), in any class inside
     * `com.example.basicaoplogging.service`, any method (second `*`),
     * and any number of arguments (`(..)`).
     */
    @Pointcut("execution(* com.example.basicaoplogging.service.*.*(..))")
    public void serviceMethods() {
        // Pointcut definition method
    }

    /**
     * Executed before the matched method is run.
     * Logs the method signature and its arguments.
     * 
     * @param joinPoint Allows access to the current state of execution.
     */
    @Before("serviceMethods()")
    public void logBefore(JoinPoint joinPoint) {
        log.info("🚀 [ENTRY] Entering Method: {} | Arguments: {}",
                joinPoint.getSignature().toShortString(),
                Arrays.toString(joinPoint.getArgs()));
    }

    /**
     * Executed successfully only if the matched method completes returning a value
     * without throwing an exception.
     * Logs the outgoing state / returned results.
     * 
     * @param joinPoint The execution point
     * @param result    The returned payload
     */
    @AfterReturning(pointcut = "serviceMethods()", returning = "result")
    public void logAfterReturning(JoinPoint joinPoint, Object result) {
        log.info("✅ [SUCCESS] Exiting Method: {} | Return Value: {}",
                joinPoint.getSignature().toShortString(),
                result);
    }

    /**
     * Executed only if an exception is thrown inside the targeted method.
     * 
     * @param joinPoint The execution point
     * @param error     The tracked exception
     */
    @AfterThrowing(pointcut = "serviceMethods()", throwing = "error")
    public void logAfterThrowing(JoinPoint joinPoint, Throwable error) {
        log.error("❌ [EXCEPTION] Exception inside Method: {} | Error Message: {}",
                joinPoint.getSignature().toShortString(),
                error.getMessage());
    }

    /**
     * Executed surrounding the method call, allowing timing profiling.
     * We capture when it started, execute the method `proceed()`, then capture
     * completion time.
     * 
     * @param joinPoint Must be ProceedingJoinPoint to invoke the actual target
     *                  method.
     * @return The target method's exact return response
     */
    @Around("serviceMethods()")
    public Object logExecutionTime(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.currentTimeMillis();

        // Let the actual method run
        Object proceed = joinPoint.proceed();

        long executionTime = System.currentTimeMillis() - start;
        log.info("⏱️ [PROFILER] Method {} executed in {} ms",
                joinPoint.getSignature().toShortString(),
                executionTime);

        return proceed;
    }
}

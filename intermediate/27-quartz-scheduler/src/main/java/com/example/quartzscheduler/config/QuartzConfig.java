package com.example.quartzscheduler.config;

import org.quartz.spi.TriggerFiredBundle;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.scheduling.quartz.SpringBeanJobFactory;
import org.springframework.stereotype.Component;

/**
 * Quartz configuration that bridges Spring's dependency injection with Quartz
 * job instantiation.
 *
 * <h2>The problem this solves</h2>
 * <p>Quartz creates job instances itself, bypassing the Spring application context.
 * This means that by default, {@code @Autowired} or constructor-injected fields
 * in job classes are {@code null} at execution time.
 *
 * <h2>The solution: AutowiringSpringBeanJobFactory</h2>
 * <p>By extending {@link SpringBeanJobFactory} and overriding
 * {@link #createJobInstance}, we intercept Quartz's object creation and hand it
 * to Spring's {@link AutowireCapableBeanFactory}.  The factory:
 * <ol>
 *   <li>Instantiates the job class (as Quartz requests).</li>
 *   <li>Autowires all Spring-managed dependencies into the new instance.</li>
 * </ol>
 * <p>This makes job beans fully Spring-aware without requiring them to look up
 * dependencies from the application context manually.
 *
 * <h2>Registration</h2>
 * <p>Spring Boot's {@code QuartzAutoConfiguration} automatically uses the first
 * {@link SpringBeanJobFactory} bean it finds in the context.  Because this
 * component extends {@link SpringBeanJobFactory}, it is automatically picked up
 * and used as the scheduler's job factory.
 */
@Component
public class QuartzConfig extends SpringBeanJobFactory implements ApplicationContextAware {

    /**
     * The Spring {@link AutowireCapableBeanFactory} used to inject dependencies
     * into Quartz job instances after creation.  Set via
     * {@link #setApplicationContext(ApplicationContext)}.
     */
    private AutowireCapableBeanFactory beanFactory;

    /**
     * Called by the Spring container when the application context is ready.
     * Stores the {@link AutowireCapableBeanFactory} for use in
     * {@link #createJobInstance}.
     *
     * @param applicationContext the Spring application context
     */
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        this.beanFactory = applicationContext.getAutowireCapableBeanFactory();
    }

    /**
     * Creates and autowires a new Quartz job instance.
     *
     * <p>This override:
     * <ol>
     *   <li>Delegates to the super class to let Quartz instantiate the raw object.</li>
     *   <li>Calls {@link AutowireCapableBeanFactory#autowireBean} to inject all
     *       {@code @Autowired} / constructor-injected fields into the instance.</li>
     * </ol>
     *
     * @param bundle the trigger-fired bundle provided by Quartz (contains the job detail)
     * @return a fully autowired job instance
     * @throws Exception if Quartz cannot instantiate the class or Spring cannot inject
     */
    @Override
    protected Object createJobInstance(TriggerFiredBundle bundle) throws Exception {
        // Let the super class do the normal Quartz instantiation
        Object jobInstance = super.createJobInstance(bundle);
        // Then let Spring inject all dependencies (@Autowired / constructor injection)
        beanFactory.autowireBean(jobInstance);
        return jobInstance;
    }
}

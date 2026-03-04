package com.example.propertiesconfiguration.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * POJO for binding properties prefixed with 'app'.
 * By annotating with @ConfigurationProperties(prefix = "app"), Spring Boot will
 * automatically bind matching properties from application.properties to the
 * fields.
 * Since we added @ConfigurationPropertiesScan in the main class, we do not
 * need @Component.
 */
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private String name;
    private String version;
    private String description;

    // Nested properties can be modeled using nested classes
    private final Developer developer = new Developer();
    private final Features features = new Features();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Developer getDeveloper() {
        return developer;
    }

    public Features getFeatures() {
        return features;
    }

    // Nested classes must be static to work gracefully, and their properties match
    // nested keys

    public static class Developer {
        private String name;
        private String email;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }
    }

    public static class Features {
        private boolean enabled;
        private int maxUsers;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getMaxUsers() {
            return maxUsers;
        }

        public void setMaxUsers(int maxUsers) {
            this.maxUsers = maxUsers;
        }
    }
}

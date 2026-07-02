package com.solux31.nubee_BE.global.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies that adding the 'spring-boot-starter-security' dependency in
 * build.gradle actually activates Spring Security's default auto-configuration
 * (a default SecurityFilterChain + UserDetailsService, and endpoints requiring
 * authentication) even though no explicit SecurityConfig class exists yet.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityAutoConfigurationTest {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void defaultSecurityFilterChainBeanIsRegistered() {
        assertTrue(context.getBeanNamesForType(SecurityFilterChain.class).length > 0,
                "Adding spring-boot-starter-security should auto-configure a SecurityFilterChain bean");
    }

    @Test
    void defaultUserDetailsServiceIsAutoConfigured() {
        assertTrue(context.getBeanNamesForType(UserDetailsService.class).length > 0,
                "Without a custom UserDetailsService, Spring Boot should auto-configure a default in-memory one");
    }

    @Test
    void unauthenticatedRequestIsRejectedByDefaultSecurity() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isUnauthorized());
    }
}
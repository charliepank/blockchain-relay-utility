package com.utility.chainservice

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(locations = ["classpath:application-test.yml"])
class UtilityAutoConfigurationTest {

    @Test
    fun `context loads successfully`() {
        // Test that Spring Boot context loads with our auto-configuration
        // This validates that all beans are properly configured
    }
}
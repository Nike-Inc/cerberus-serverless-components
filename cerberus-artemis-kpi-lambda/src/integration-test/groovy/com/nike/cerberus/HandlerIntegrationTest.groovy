package com.nike.cerberus

import org.junit.Before
import org.junit.Test

class HandlerIntegrationTest {

    CerberusMetricsHandler handler

    @Before
    void before() {
        handler = new CerberusMetricsHandler()
    }

    @Test
    void "run handle"() {
        handler.handle()
    }
}

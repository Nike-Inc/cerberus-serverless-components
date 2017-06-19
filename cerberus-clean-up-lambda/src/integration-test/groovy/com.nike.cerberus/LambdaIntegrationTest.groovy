package com.nike.cerberus

import org.junit.Before
import org.junit.Test

import static junit.framework.TestCase.assertEquals

class LambdaIntegrationTest {

    private CerberusCleanUpHandler handler

    @Before
    void before() {
        handler = new CerberusCleanUpHandler()
    }

    @Test
    void test_lambda() {
        def result = handler.runCleanUp()
        assertEquals("Failed to run cleanup did not run successfully.", "success", result.status)
    }

}

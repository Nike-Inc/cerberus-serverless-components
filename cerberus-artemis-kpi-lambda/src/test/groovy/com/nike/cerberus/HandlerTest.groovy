package com.nike.cerberus

import org.junit.Before
import org.junit.Test

class HandlerTest {

    Handler handler

    @Before
    void before() {
        handler = new Handler()
    }

    @Test
    void "run handle"() {
        handler.handle()
    }
}

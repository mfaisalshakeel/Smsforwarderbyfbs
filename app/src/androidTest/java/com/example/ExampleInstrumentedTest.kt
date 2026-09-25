package com.example

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Smoke test confirming the app under test is the one we expect. */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {

    @Test
    fun usesTheExpectedApplicationId() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.aistudio.smsforwarder.vknwpx", context.packageName)
    }
}

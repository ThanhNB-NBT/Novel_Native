package com.thanhnb.novelnative

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RangeTest {
    @Test fun parsesRangeInEitherOrder() {
        assertEquals(100..130, parseRange("100-130"))
        assertEquals(100..130, parseRange("130-100"))
    }

    @Test fun singleNumberAndJunk() {
        assertEquals(7..7, parseRange("7"))
        assertEquals(7..7, parseRange("7-"))
        assertNull(parseRange("-"))
        assertNull(parseRange(""))
    }
}

package com.finance.splitup.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PinSecurityTest {

    @Test
    fun `hash produces pbkdf2 format and verifies`() {
        val stored = PinSecurity.hash("1234")!!
        assertTrue(stored.startsWith("pbkdf2$"))
        assertTrue(PinSecurity.verify("1234", stored))
        assertFalse(PinSecurity.verify("9999", stored))
        assertFalse(PinSecurity.verify("", stored))
        assertFalse(PinSecurity.verify("1234", null))
    }

    @Test
    fun `same pin hashes differently each time (random salt)`() {
        val a = PinSecurity.hash("4321")!!
        val b = PinSecurity.hash("4321")!!
        assertNotEquals(a, b)
        assertTrue(PinSecurity.verify("4321", a))
        assertTrue(PinSecurity.verify("4321", b))
    }

    @Test
    fun `legacy unsalted sha256 hashes are verified and flagged for upgrade`() {
        val legacy = PinSecurity.legacyHash("5678")
        assertEquals(PinSecurity.legacyHash("5678"), legacy) // deterministic
        assertTrue(PinSecurity.verify("5678", legacy))
        assertTrue(PinSecurity.needsUpgrade(legacy))
        assertFalse(PinSecurity.needsUpgrade(PinSecurity.hash("5678")))
    }

    @Test
    fun `plaintext stored pins are still accepted (oldest installs)`() {
        assertTrue(PinSecurity.verify("1111", "1111"))
        assertTrue(PinSecurity.needsUpgrade("1111"))
    }

    @Test
    fun `hash returns null-safe failure and verify rejects malformed stored values`() {
        assertNull(PinSecurity.hash(""))
        assertFalse(PinSecurity.verify("1234", "pbkdf2\$garbage"))
        assertFalse(PinSecurity.verify("1234", "pbkdf2\$abc\$def\$ghi"))
    }
}

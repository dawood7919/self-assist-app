package com.dawood.orbit.tools.password

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordGeneratorTest {

    @Test
    fun `honours the requested length`() {
        listOf(8, 16, 32, 64).forEach { length ->
            val generated = PasswordGenerator.generate(PasswordGenerator.Options(length = length))
            assertNotNull(generated)
            assertEquals(length, generated!!.password.length)
        }
    }

    @Test
    fun `clamps lengths outside the supported range`() {
        val short = PasswordGenerator.generate(PasswordGenerator.Options(length = 1))!!
        assertEquals(PasswordGenerator.MIN_LENGTH, short.password.length)

        val long = PasswordGenerator.generate(PasswordGenerator.Options(length = 500))!!
        assertEquals(PasswordGenerator.MAX_LENGTH, long.password.length)
    }

    @Test
    fun `includes at least one character from every selected set`() {
        // Run repeatedly: this is the guarantee most generators quietly break.
        repeat(200) {
            val generated = PasswordGenerator.generate(
                PasswordGenerator.Options(length = 8, lowercase = true, uppercase = true, digits = true, symbols = true),
            )!!
            val password = generated.password
            assertTrue("no lowercase in $password", password.any { it.isLowerCase() })
            assertTrue("no uppercase in $password", password.any { it.isUpperCase() })
            assertTrue("no digit in $password", password.any { it.isDigit() })
            assertTrue("no symbol in $password", password.any { !it.isLetterOrDigit() })
        }
    }

    @Test
    fun `uses only the selected sets`() {
        repeat(100) {
            val generated = PasswordGenerator.generate(
                PasswordGenerator.Options(length = 20, lowercase = true, uppercase = false, digits = false, symbols = false),
            )!!
            assertTrue(generated.password.all { it.isLowerCase() })
        }
    }

    @Test
    fun `avoiding ambiguous characters removes them`() {
        val ambiguous = "Il1O0o|`'\""
        repeat(100) {
            val generated = PasswordGenerator.generate(
                PasswordGenerator.Options(length = 40, avoidAmbiguous = true),
            )!!
            assertTrue(
                "found an ambiguous character in ${generated.password}",
                generated.password.none { it in ambiguous },
            )
        }
    }

    @Test
    fun `returns null when every set is switched off`() {
        assertNull(
            PasswordGenerator.generate(
                PasswordGenerator.Options(lowercase = false, uppercase = false, digits = false, symbols = false),
            ),
        )
    }

    @Test
    fun `entropy grows with length and pool size`() {
        assertEquals(0, PasswordGenerator.entropyBits(10, 1))
        assertTrue(PasswordGenerator.entropyBits(20, 62) > PasswordGenerator.entropyBits(10, 62))
        assertTrue(PasswordGenerator.entropyBits(16, 94) > PasswordGenerator.entropyBits(16, 26))
        // 16 characters from a 94-character pool is about 105 bits.
        assertEquals(105, PasswordGenerator.entropyBits(16, 94))
    }

    @Test
    fun `successive passwords differ`() {
        val options = PasswordGenerator.Options(length = 24)
        val seen = (1..50).map { PasswordGenerator.generate(options)!!.password }.toSet()
        assertEquals("generator produced duplicates", 50, seen.size)
    }
}

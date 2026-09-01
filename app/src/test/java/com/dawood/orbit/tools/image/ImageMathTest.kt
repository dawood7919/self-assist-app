package com.dawood.orbit.tools.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ImageMathTest {

    private val landscape = ImageSize(4000, 3000)
    private val portrait = ImageSize(3000, 4000)
    private val square = ImageSize(2000, 2000)

    @Test
    fun `fitting inside a bigger box leaves the image alone`() {
        assertEquals(landscape, ImageMath.fitWithin(landscape, 8000, 8000))
    }

    @Test
    fun `fitting keeps the aspect ratio`() {
        assertEquals(ImageSize(1000, 750), ImageMath.fitWithin(landscape, 1000, 1000))
    }

    @Test
    fun `fitting uses the tighter of the two limits`() {
        assertEquals(ImageSize(400, 300), ImageMath.fitWithin(landscape, 400, 900))
        assertEquals(ImageSize(400, 300), ImageMath.fitWithin(landscape, 900, 300))
    }

    @Test
    fun `a zero-sized image is returned untouched`() {
        val empty = ImageSize(0, 0)
        assertEquals(empty, ImageMath.fitWithin(empty, 100, 100))
    }

    @Test
    fun `long edge scaling picks the longer side`() {
        assertEquals(ImageSize(1600, 1200), ImageMath.scaleToLongEdge(landscape, 1600))
        assertEquals(ImageSize(1200, 1600), ImageMath.scaleToLongEdge(portrait, 1600))
    }

    @Test
    fun `long edge scaling never enlarges`() {
        assertEquals(square, ImageMath.scaleToLongEdge(square, 5000))
    }

    @Test
    fun `percentage scaling halves both edges at fifty`() {
        assertEquals(ImageSize(2000, 1500), ImageMath.scaleByPercent(landscape, 50))
    }

    @Test
    fun `percentage scaling never produces a zero edge`() {
        val tiny = ImageMath.scaleByPercent(ImageSize(3, 3), 1)
        assertTrue(tiny.width >= 1 && tiny.height >= 1)
    }

    @Test
    fun `a square crop of a landscape image keeps the full height`() {
        val crop = ImageMath.centreCrop(landscape, 1, 1)
        assertEquals(3000, crop.width)
        assertEquals(3000, crop.height)
        assertEquals(500, crop.x)
        assertEquals(0, crop.y)
    }

    @Test
    fun `a square crop of a portrait image keeps the full width`() {
        val crop = ImageMath.centreCrop(portrait, 1, 1)
        assertEquals(3000, crop.width)
        assertEquals(3000, crop.height)
        assertEquals(0, crop.x)
        assertEquals(500, crop.y)
    }

    @Test
    fun `cropping to the ratio the image already has changes nothing`() {
        val crop = ImageMath.centreCrop(landscape, 4, 3)
        assertEquals(0, crop.x)
        assertEquals(0, crop.y)
        assertEquals(4000, crop.width)
        assertEquals(3000, crop.height)
    }

    @Test
    fun `a crop never leaves the image`() {
        listOf(landscape, portrait, square).forEach { size ->
            listOf(1 to 1, 16 to 9, 9 to 16, 3 to 2, 5 to 4).forEach { pair ->
                val crop = ImageMath.centreCrop(size, pair.first, pair.second)
                assertTrue(crop.x >= 0 && crop.y >= 0)
                assertTrue(crop.x + crop.width <= size.width)
                assertTrue(crop.y + crop.height <= size.height)
            }
        }
    }

    @Test
    fun `a crop has the ratio that was asked for`() {
        val crop = ImageMath.centreCrop(landscape, 16, 9)
        assertTrue(abs(crop.width.toDouble() / crop.height - 16.0 / 9.0) < 0.01)
    }

    @Test
    fun `a nonsense ratio falls back to the whole image`() {
        val crop = ImageMath.centreCrop(landscape, 0, 0)
        assertEquals(4000, crop.width)
        assertEquals(3000, crop.height)
    }

    @Test
    fun `a quarter turn swaps the edges`() {
        assertEquals(ImageSize(3000, 4000), ImageMath.rotated(landscape, 90))
        assertEquals(ImageSize(3000, 4000), ImageMath.rotated(landscape, 270))
        assertEquals(ImageSize(3000, 4000), ImageMath.rotated(landscape, -90))
    }

    @Test
    fun `a half turn keeps the edges`() {
        assertEquals(landscape, ImageMath.rotated(landscape, 180))
        assertEquals(landscape, ImageMath.rotated(landscape, 0))
        assertEquals(landscape, ImageMath.rotated(landscape, 360))
    }

    @Test
    fun `a lower jpeg quality estimates a smaller file`() {
        val high = ImageMath.estimateBytes(landscape, ImageFormat.Jpeg, 95)
        val low = ImageMath.estimateBytes(landscape, ImageFormat.Jpeg, 40)
        assertTrue(low < high)
    }

    @Test
    fun `png is estimated larger than jpeg at the same size`() {
        val png = ImageMath.estimateBytes(landscape, ImageFormat.Png, 100)
        val jpeg = ImageMath.estimateBytes(landscape, ImageFormat.Jpeg, 100)
        assertTrue(png > jpeg)
    }

    @Test
    fun `format capabilities match the encoders`() {
        assertFalse(ImageFormat.Png.usesQuality)
        assertTrue(ImageFormat.Jpeg.usesQuality)
        assertFalse(ImageFormat.Jpeg.keepsTransparency)
        assertTrue(ImageFormat.Png.keepsTransparency)
        assertTrue(ImageFormat.Webp.keepsTransparency)
    }

    @Test
    fun `megapixels are reported from the size`() {
        assertTrue(abs(landscape.megapixels - 12.0) < 0.001)
        assertEquals("4000 × 3000", landscape.label)
    }
}

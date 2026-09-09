package com.dawood.orbit.tools.calculator

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.sqrt

/**
 * Advanced maths behind the Matrix / Vector / Complex / Statistics modes.
 *
 * All pure Kotlin with no Android types so every operation is unit tested on
 * the JVM. The UI collects numbers and shows results; it never computes.
 */

data class Complex(val re: Double, val im: Double) {
    fun format(): String {
        fun part(v: Double): String = ScientificEngine.format(v)
        return when {
            im == 0.0 -> part(re)
            re == 0.0 -> "${part(im)}i"
            im < 0 -> "${part(re)}−${part(-im)}i"
            else -> "${part(re)}+${part(im)}i"
        }
    }
}

object ComplexMath {
    fun add(a: Complex, b: Complex) = Complex(a.re + b.re, a.im + b.im)
    fun sub(a: Complex, b: Complex) = Complex(a.re - b.re, a.im - b.im)
    fun mul(a: Complex, b: Complex) =
        Complex(a.re * b.re - a.im * b.im, a.re * b.im + a.im * b.re)

    fun div(a: Complex, b: Complex): Complex {
        val denom = b.re * b.re + b.im * b.im
        if (denom == 0.0) throw IllegalArgumentException("Cannot divide by zero")
        return Complex(
            (a.re * b.re + a.im * b.im) / denom,
            (a.im * b.re - a.re * b.im) / denom,
        )
    }

    fun abs(c: Complex): Double = sqrt(c.re * c.re + c.im * c.im)
    fun conj(c: Complex) = Complex(c.re, -c.im)
    fun arg(c: Complex, degrees: Boolean = true): Double {
        val rad = kotlin.math.atan2(c.im, c.re)
        return if (degrees) Math.toDegrees(rad) else rad
    }
}

object MatrixMath {
    fun validate(m: List<List<Double>>): Pair<Int, Int> {
        if (m.isEmpty() || m.any { it.isEmpty() }) throw IllegalArgumentException("Matrix is empty")
        val cols = m.first().size
        if (m.any { it.size != cols }) throw IllegalArgumentException("Rows have different lengths")
        if (m.size > 4 || cols > 4) throw IllegalArgumentException("Matrices are capped at 4×4")
        return m.size to cols
    }

    fun add(a: List<List<Double>>, b: List<List<Double>>): List<List<Double>> {
        val (ar, ac) = validate(a)
        val (br, bc) = validate(b)
        if (ar != br || ac != bc) throw IllegalArgumentException("Matrices must be the same size to add")
        return a.mapIndexed { r, row -> row.mapIndexed { c, v -> v + b[r][c] } }
    }

    fun multiply(a: List<List<Double>>, b: List<List<Double>>): List<List<Double>> {
        val (ar, ac) = validate(a)
        val (br, bc) = validate(b)
        if (ac != br) throw IllegalArgumentException("Need A.cols == B.rows to multiply")
        return List(ar) { r ->
            List(bc) { c ->
                var sum = 0.0
                for (k in 0 until ac) sum += a[r][k] * b[k][c]
                sum
            }
        }
    }

    fun transpose(m: List<List<Double>>): List<List<Double>> {
        val (r, c) = validate(m)
        return List(c) { col -> List(r) { row -> m[row][col] } }
    }

    fun determinant(m: List<List<Double>>): Double {
        val (n, cols) = validate(m)
        if (n != cols) throw IllegalArgumentException("Determinant needs a square matrix")
        if (n == 1) return m[0][0]
        if (n == 2) return m[0][0] * m[1][1] - m[0][1] * m[1][0]
        var det = 0.0
        for (c in 0 until n) {
            val minor = m.drop(1).map { row -> row.filterIndexed { i, _ -> i != c } }
            val sign = if (c % 2 == 0) 1.0 else -1.0
            det += sign * m[0][c] * determinant(minor)
        }
        return det
    }

    fun inverse(m: List<List<Double>>): List<List<Double>> {
        val (n, cols) = validate(m)
        if (n != cols) throw IllegalArgumentException("Inverse needs a square matrix")
        val det = determinant(m)
        if (abs(det) < 1e-12) throw IllegalArgumentException("Matrix is singular — no inverse")
        if (n == 1) return listOf(listOf(1.0 / m[0][0]))
        if (n == 2) {
            return listOf(
                listOf(m[1][1] / det, -m[0][1] / det),
                listOf(-m[1][0] / det, m[0][0] / det),
            )
        }
        // Adjugate / determinant for 3×3 and 4×4.
        val cof = List(n) { r ->
            List(n) { c ->
                val minor = m.filterIndexed { i, _ -> i != r }
                    .map { row -> row.filterIndexed { j, _ -> j != c } }
                val sign = if ((r + c) % 2 == 0) 1.0 else -1.0
                sign * determinant(minor)
            }
        }
        val adj = transpose(cof)
        return adj.map { row -> row.map { it / det } }
    }

    fun format(m: List<List<Double>>): String =
        m.joinToString("\n") { row -> row.joinToString("\t") { ScientificEngine.format(it) } }
}

object VectorMath {
    fun dot(a: List<Double>, b: List<Double>): Double {
        if (a.size != b.size || a.isEmpty() || a.size > 4) {
            throw IllegalArgumentException("Vectors must match in size (2–4)")
        }
        return a.zip(b).sumOf { (x, y) -> x * y }
    }

    fun cross3(a: List<Double>, b: List<Double>): List<Double> {
        if (a.size != 3 || b.size != 3) throw IllegalArgumentException("Cross product needs 3D vectors")
        return listOf(
            a[1] * b[2] - a[2] * b[1],
            a[2] * b[0] - a[0] * b[2],
            a[0] * b[1] - a[1] * b[0],
        )
    }

    fun norm(a: List<Double>): Double {
        if (a.isEmpty()) throw IllegalArgumentException("Vector is empty")
        return sqrt(a.sumOf { it * it })
    }

    fun angleDeg(a: List<Double>, b: List<Double>): Double {
        val denom = norm(a) * norm(b)
        if (denom == 0.0) throw IllegalArgumentException("Zero vector has no angle")
        val cos = (dot(a, b) / denom).coerceIn(-1.0, 1.0)
        return Math.toDegrees(acos(cos))
    }
}

object StatsMath {
    data class Summary(
        val count: Int,
        val sum: Double,
        val mean: Double,
        val median: Double,
        val variance: Double,
        val stdDev: Double,
        val min: Double,
        val max: Double,
    )

    fun summarize(values: List<Double>): Summary {
        if (values.isEmpty()) throw IllegalArgumentException("Add at least one value")
        if (values.size > 10_000) throw IllegalArgumentException("Capped at 10,000 values")
        val sorted = values.sorted()
        val n = values.size
        val sum = values.sum()
        val mean = sum / n
        val median = if (n % 2 == 1) sorted[n / 2] else (sorted[n / 2 - 1] + sorted[n / 2]) / 2.0
        val variance = if (n < 2) 0.0 else values.sumOf { (it - mean) * (it - mean) } / (n - 1)
        return Summary(
            count = n,
            sum = sum,
            mean = mean,
            median = median,
            variance = variance,
            stdDev = sqrt(variance),
            min = sorted.first(),
            max = sorted.last(),
        )
    }
}

/**
 * Numeric calculus over [ScientificEngine]: derivative (central difference),
 * definite integral (Simpson) and single-variable solving (bisection +
 * secant fallback). Pure — the UI supplies the expression with `x`.
 */
object NumericCalc {
    fun derivativeAt(expression: String, x: Double, base: ScientificEngine.Context): Double {
        val h = 1e-6 * maxOf(1.0, abs(x))
        fun f(v: Double): Double {
            val r = ScientificEngine.evaluate(expression, base.copy(variables = mapOf("x" to v)))
            val value = (r as? ScientificEngine.Result.Value)?.number
                ?: throw IllegalArgumentException("Cannot differentiate that expression")
            return value
        }
        return (f(x + h) - f(x - h)) / (2 * h)
    }

    fun integrate(expression: String, a: Double, b: Double, base: ScientificEngine.Context, steps: Int = 512): Double {
        if (a == b) return 0.0
        fun f(v: Double): Double {
            val r = ScientificEngine.evaluate(expression, base.copy(variables = mapOf("x" to v)))
            val value = (r as? ScientificEngine.Result.Value)?.number
                ?: throw IllegalArgumentException("Cannot integrate that expression")
            if (!value.isFinite()) throw IllegalArgumentException("Integral hits a non-finite value")
            return value
        }
        var n = steps.coerceIn(8, 4096)
        if (n % 2 == 1) n += 1
        val h = (b - a) / n
        var sum = f(a) + f(b)
        for (i in 1 until n) {
            val x = a + i * h
            sum += if (i % 2 == 0) 2 * f(x) else 4 * f(x)
        }
        return sum * h / 3.0
    }

    fun solve(expression: String, base: ScientificEngine.Context): Double {
        fun f(v: Double): Double {
            val r = ScientificEngine.evaluate(expression, base.copy(variables = mapOf("x" to v)))
            return (r as? ScientificEngine.Result.Value)?.number
                ?: throw IllegalArgumentException("Cannot solve that expression")
        }
        // Expand a bracket until the sign changes, then bisect.
        var lo = -10.0
        var hi = 10.0
        var flo = f(lo)
        var fhi = f(hi)
        var grows = 0
        while (flo.isFinite() && fhi.isFinite() && flo * fhi > 0 && grows < 24) {
            lo *= 2
            hi *= 2
            flo = f(lo)
            fhi = f(hi)
            grows++
        }
        if (!flo.isFinite() || !fhi.isFinite() || flo * fhi > 0) {
            throw IllegalArgumentException("No sign change found — no root in range")
        }
        repeat(200) {
            val mid = (lo + hi) / 2
            val fm = f(mid)
            if (!fm.isFinite()) throw IllegalArgumentException("Solver hit a non-finite value")
            if (abs(fm) < 1e-12 || (hi - lo) < 1e-12) return mid
            if (flo * fm <= 0) {
                hi = mid
                fhi = fm
            } else {
                lo = mid
                flo = fm
            }
        }
        return (lo + hi) / 2
    }
}

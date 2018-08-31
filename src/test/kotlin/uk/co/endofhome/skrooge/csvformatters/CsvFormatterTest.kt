package uk.co.endofhome.skrooge.csvformatters

import org.junit.Assume.assumeFalse
import org.junit.Before

abstract class CsvFormatterTest {

    @Before
    fun setup() {
        val isRunningOnCi: Boolean = System.getenv("CI") == "true"
        assumeFalse(isRunningOnCi)
    }
}

package uk.co.endofhome.skrooge

import java.math.BigDecimal
import java.math.RoundingMode

fun BigDecimal.format(): BigDecimal = this.setScale(2, RoundingMode.HALF_UP)

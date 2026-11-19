package br.com.itau.notafiscal.domain.model

import java.math.BigDecimal
import java.math.RoundingMode

const val ESCALA_MONETARIA = 2
val ARREDONDAMENTO_MONETARIO: RoundingMode = RoundingMode.HALF_EVEN

fun BigDecimal.comEscalaMonetaria(): BigDecimal = setScale(ESCALA_MONETARIA, ARREDONDAMENTO_MONETARIO)

fun BigDecimal.temNoMaximoDuasCasas(): Boolean = stripTrailingZeros().scale() <= ESCALA_MONETARIA

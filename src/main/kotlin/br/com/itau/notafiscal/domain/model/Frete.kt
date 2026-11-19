package br.com.itau.notafiscal.domain.model

import java.math.BigDecimal

data class Frete(
    val regiao: Regiao,
    val valorBase: BigDecimal,
    val percentual: BigDecimal,
    val valor: BigDecimal,
)

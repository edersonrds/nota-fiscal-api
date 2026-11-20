package br.com.itau.notafiscal.domain.service

import br.com.itau.notafiscal.domain.model.Frete
import br.com.itau.notafiscal.domain.model.Pedido
import br.com.itau.notafiscal.domain.model.Regiao
import br.com.itau.notafiscal.domain.model.comEscalaMonetaria
import java.math.BigDecimal

class CalculadoraFrete {
    fun calcular(pedido: Pedido): Frete {
        val regiao = pedido.cliente.enderecoDeEntrega().regiao
        val percentual = percentualDe(regiao)
        val valorBase = pedido.valorFrete.comEscalaMonetaria()
        val valor = (valorBase * (BigDecimal.ONE + percentual)).comEscalaMonetaria()
        return Frete(regiao, valorBase, percentual, valor)
    }

    private fun percentualDe(regiao: Regiao): BigDecimal =
        when (regiao) {
            Regiao.NORTE -> BigDecimal("0.08")
            Regiao.NORDESTE -> BigDecimal("0.085")
            Regiao.CENTRO_OESTE -> BigDecimal("0.07")
            Regiao.SUDESTE -> BigDecimal("0.048")
            Regiao.SUL -> BigDecimal("0.06")
        }
}

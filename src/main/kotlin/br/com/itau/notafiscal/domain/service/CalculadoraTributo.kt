package br.com.itau.notafiscal.domain.service

import br.com.itau.notafiscal.domain.exception.RegimeTributarioInvalidoException
import br.com.itau.notafiscal.domain.model.Cliente
import br.com.itau.notafiscal.domain.model.ItemTributado
import br.com.itau.notafiscal.domain.model.Pedido
import br.com.itau.notafiscal.domain.model.PessoaFisica
import br.com.itau.notafiscal.domain.model.PessoaJuridica
import br.com.itau.notafiscal.domain.model.RegimeTributario
import br.com.itau.notafiscal.domain.model.Tributacao
import br.com.itau.notafiscal.domain.model.comEscalaMonetaria
import java.math.BigDecimal

class CalculadoraTributo {
    fun calcular(pedido: Pedido): Tributacao {
        val aliquota = faixasDe(pedido.cliente).aliquotaPara(pedido.totalItens)
        val itens =
            pedido.itens.map { item ->
                ItemTributado(item, (item.valorTotal * aliquota).comEscalaMonetaria())
            }
        return Tributacao(aliquota, itens)
    }

    private fun faixasDe(cliente: Cliente): List<Faixa> =
        when (cliente) {
            is PessoaFisica -> FAIXAS_PESSOA_FISICA
            is PessoaJuridica ->
                when (cliente.regimeTributario) {
                    RegimeTributario.SIMPLES_NACIONAL -> FAIXAS_SIMPLES_NACIONAL
                    RegimeTributario.LUCRO_REAL -> FAIXAS_LUCRO_REAL
                    RegimeTributario.LUCRO_PRESUMIDO -> FAIXAS_LUCRO_PRESUMIDO
                    RegimeTributario.OUTROS, null -> throw RegimeTributarioInvalidoException(cliente.regimeTributario)
                }
        }

    private fun List<Faixa>.aliquotaPara(totalItens: BigDecimal): BigDecimal =
        first { faixa -> faixa.ate == null || totalItens <= faixa.ate }.aliquota

    private class Faixa(
        val ate: BigDecimal?,
        val aliquota: BigDecimal,
    )

    private companion object {
        fun faixa(
            ate: String,
            aliquota: String,
        ) = Faixa(BigDecimal(ate), BigDecimal(aliquota))

        fun acimaDisso(aliquota: String) = Faixa(null, BigDecimal(aliquota))

        val FAIXAS_PESSOA_FISICA =
            listOf(
                faixa(ate = "499.99", aliquota = "0.00"),
                faixa(ate = "2000.00", aliquota = "0.12"),
                faixa(ate = "3500.00", aliquota = "0.15"),
                acimaDisso(aliquota = "0.17"),
            )

        fun faixasPessoaJuridica(
            ate999: String,
            ate2000: String,
            ate5000: String,
            acima5000: String,
        ) = listOf(
            faixa(ate = "999.99", aliquota = ate999),
            faixa(ate = "2000.00", aliquota = ate2000),
            faixa(ate = "5000.00", aliquota = ate5000),
            acimaDisso(aliquota = acima5000),
        )

        val FAIXAS_SIMPLES_NACIONAL = faixasPessoaJuridica("0.03", "0.07", "0.13", "0.19")
        val FAIXAS_LUCRO_REAL = faixasPessoaJuridica("0.03", "0.09", "0.15", "0.20")
        val FAIXAS_LUCRO_PRESUMIDO = faixasPessoaJuridica("0.03", "0.09", "0.16", "0.20")
    }
}

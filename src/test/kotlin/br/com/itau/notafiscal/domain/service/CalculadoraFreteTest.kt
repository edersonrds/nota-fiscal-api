package br.com.itau.notafiscal.domain.service

import br.com.itau.notafiscal.domain.exception.EnderecoEntregaAusenteException
import br.com.itau.notafiscal.domain.model.Finalidade
import br.com.itau.notafiscal.domain.model.Frete
import br.com.itau.notafiscal.domain.model.ModoFrete
import br.com.itau.notafiscal.domain.model.Regiao
import br.com.itau.notafiscal.domain.model.umEndereco
import br.com.itau.notafiscal.domain.model.umPedido
import br.com.itau.notafiscal.domain.model.umaPessoaFisica
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.EnumSource
import java.math.BigDecimal

class CalculadoraFreteTest {
    private fun pedidoPara(
        regiao: Regiao,
        valorFrete: String = "50.00",
    ) = umPedido(
        cliente = umaPessoaFisica(enderecos = listOf(umEndereco(regiao = regiao))),
        valorFrete = valorFrete,
    )

    @ParameterizedTest(name = "{0}, {1}: 50.00 → {2}")
    @CsvSource(
        "ACRESCIMO,  NORTE,        54.00",
        "ACRESCIMO,  NORDESTE,     54.25",
        "ACRESCIMO,  CENTRO_OESTE, 53.50",
        "ACRESCIMO,  SUDESTE,      52.40",
        "ACRESCIMO,  SUL,          53.00",
        "PERCENTUAL, NORTE,        4.00",
        "PERCENTUAL, NORDESTE,     4.25",
        "PERCENTUAL, CENTRO_OESTE, 3.50",
        "PERCENTUAL, SUDESTE,      2.40",
        "PERCENTUAL, SUL,          3.00",
    )
    fun `frete aplica o percentual da região conforme o modo`(
        modo: ModoFrete,
        regiao: Regiao,
        esperado: String,
    ) {
        val frete = CalculadoraFrete(modo).calcular(pedidoPara(regiao))

        assertThat(frete.valor).isEqualTo(BigDecimal(esperado))
    }

    @ParameterizedTest(name = "{0}: 1.00 no NORDESTE → {1}")
    @CsvSource(
        "ACRESCIMO,  1.08",
        "PERCENTUAL, 0.08",
    )
    fun `meio centavo é arredondado para o par (HALF_EVEN)`(
        modo: ModoFrete,
        esperado: String,
    ) {
        val frete = CalculadoraFrete(modo).calcular(pedidoPara(Regiao.NORDESTE, valorFrete = "1.00"))

        assertThat(frete.valor).isEqualTo(BigDecimal(esperado))
    }

    @ParameterizedTest
    @EnumSource(ModoFrete::class)
    fun `frete zero continua zero com 2 casas`(modo: ModoFrete) {
        val frete = CalculadoraFrete(modo).calcular(pedidoPara(Regiao.NORTE, valorFrete = "0"))

        assertThat(frete.valor).isEqualTo(BigDecimal("0.00"))
    }

    @Test
    fun `frete expõe região, valor base e percentual aplicados`() {
        val frete = CalculadoraFrete(ModoFrete.ACRESCIMO).calcular(pedidoPara(Regiao.SUDESTE, valorFrete = "50"))

        assertThat(frete).isEqualTo(
            Frete(
                regiao = Regiao.SUDESTE,
                valorBase = BigDecimal("50.00"),
                percentual = BigDecimal("0.048"),
                valor = BigDecimal("52.40"),
            ),
        )
    }

    @Test
    fun `região vem do endereço de entrega, não do de cobrança`() {
        val cliente =
            umaPessoaFisica(
                enderecos =
                    listOf(
                        umEndereco(Finalidade.COBRANCA, Regiao.NORTE),
                        umEndereco(Finalidade.ENTREGA, Regiao.SUL),
                    ),
            )

        val frete = CalculadoraFrete(ModoFrete.ACRESCIMO).calcular(umPedido(cliente = cliente))

        assertThat(frete.regiao).isEqualTo(Regiao.SUL)
    }

    @Test
    fun `pedido sem endereço de entrega não tem frete calculado`() {
        val cliente = umaPessoaFisica(enderecos = listOf(umEndereco(Finalidade.COBRANCA)))

        assertThatThrownBy { CalculadoraFrete(ModoFrete.ACRESCIMO).calcular(umPedido(cliente = cliente)) }
            .isInstanceOf(EnderecoEntregaAusenteException::class.java)
    }
}

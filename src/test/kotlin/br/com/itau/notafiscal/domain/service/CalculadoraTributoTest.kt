package br.com.itau.notafiscal.domain.service

import br.com.itau.notafiscal.domain.exception.RegimeTributarioInvalidoException
import br.com.itau.notafiscal.domain.model.RegimeTributario
import br.com.itau.notafiscal.domain.model.umItem
import br.com.itau.notafiscal.domain.model.umPedido
import br.com.itau.notafiscal.domain.model.umaPessoaFisica
import br.com.itau.notafiscal.domain.model.umaPessoaJuridica
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.math.BigDecimal

class CalculadoraTributoTest {
    private val calculadora = CalculadoraTributo()

    @Nested
    inner class `faixas de pessoa física` {
        @ParameterizedTest(name = "total {0} → alíquota {1}")
        @CsvSource(
            "0.01,    0.00",
            "499.99,  0.00",
            "500.00,  0.12",
            "2000.00, 0.12",
            "2000.01, 0.15",
            "3500.00, 0.15",
            "3500.01, 0.17",
        )
        fun `alíquota respeita os limites de cada faixa`(
            total: String,
            aliquotaEsperada: String,
        ) {
            val pedido = umPedido(cliente = umaPessoaFisica(), itens = listOf(umItem(valorUnitario = total)))

            assertThat(calculadora.calcular(pedido).aliquota).isEqualTo(BigDecimal(aliquotaEsperada))
        }
    }

    @Nested
    inner class `faixas de pessoa jurídica` {
        @ParameterizedTest(name = "{0}, total {1} → alíquota {2}")
        @CsvSource(
            "SIMPLES_NACIONAL, 999.99,  0.03",
            "SIMPLES_NACIONAL, 1000.00, 0.07",
            "SIMPLES_NACIONAL, 2000.00, 0.07",
            "SIMPLES_NACIONAL, 2000.01, 0.13",
            "SIMPLES_NACIONAL, 5000.00, 0.13",
            "SIMPLES_NACIONAL, 5000.01, 0.19",
            "LUCRO_REAL,       999.99,  0.03",
            "LUCRO_REAL,       1000.00, 0.09",
            "LUCRO_REAL,       2000.00, 0.09",
            "LUCRO_REAL,       2000.01, 0.15",
            "LUCRO_REAL,       5000.00, 0.15",
            "LUCRO_REAL,       5000.01, 0.20",
            "LUCRO_PRESUMIDO,  999.99,  0.03",
            "LUCRO_PRESUMIDO,  1000.00, 0.09",
            "LUCRO_PRESUMIDO,  2000.00, 0.09",
            "LUCRO_PRESUMIDO,  2000.01, 0.16",
            "LUCRO_PRESUMIDO,  5000.00, 0.16",
            "LUCRO_PRESUMIDO,  5000.01, 0.20",
        )
        fun `alíquota respeita os limites de cada faixa e regime`(
            regime: RegimeTributario,
            total: String,
            aliquotaEsperada: String,
        ) {
            val pedido = umPedido(cliente = umaPessoaJuridica(regime), itens = listOf(umItem(valorUnitario = total)))

            assertThat(calculadora.calcular(pedido).aliquota).isEqualTo(BigDecimal(aliquotaEsperada))
        }

        @Test
        fun `pessoa jurídica sem regime não pode ter nota gerada`() {
            val pedido = umPedido(cliente = umaPessoaJuridica(regimeTributario = null))

            assertThatThrownBy { calculadora.calcular(pedido) }
                .isInstanceOf(RegimeTributarioInvalidoException::class.java)
                .hasMessageContaining("sem regime")
        }

        @Test
        fun `pessoa jurídica com regime OUTROS não pode ter nota gerada`() {
            val pedido = umPedido(cliente = umaPessoaJuridica(regimeTributario = RegimeTributario.OUTROS))

            assertThatThrownBy { calculadora.calcular(pedido) }
                .isInstanceOf(RegimeTributarioInvalidoException::class.java)
                .hasMessageContaining("OUTROS")
        }
    }

    @Nested
    inner class `cálculo por item` {
        @Test
        fun `faixa é definida pelo total do pedido, não pelo valor de cada item`() {
            val pedido =
                umPedido(
                    cliente = umaPessoaFisica(),
                    itens = listOf(umItem("A", valorUnitario = "300.00"), umItem("B", valorUnitario = "300.00")),
                )

            val tributacao = calculadora.calcular(pedido)

            assertThat(tributacao.aliquota).isEqualTo(BigDecimal("0.12"))
            assertThat(tributacao.itens)
                .extracting<BigDecimal> { it.valorTributo }
                .containsExactly(BigDecimal("36.00"), BigDecimal("36.00"))
        }

        @Test
        fun `tributo de cada item é calculado sobre valor unitário vezes quantidade`() {
            val pedido =
                umPedido(
                    cliente = umaPessoaJuridica(RegimeTributario.LUCRO_PRESUMIDO),
                    itens = listOf(umItem(quantidade = 2, valorUnitario = "1500.00")),
                )

            val tributacao = calculadora.calcular(pedido)

            assertThat(tributacao.itens.single().valorTributo).isEqualTo(BigDecimal("480.00"))
            assertThat(tributacao.totalTributos).isEqualTo(BigDecimal("480.00"))
        }

        @Test
        fun `tributo é arredondado por item e o total é a soma dos tributos arredondados`() {
            val pedido =
                umPedido(
                    cliente = umaPessoaFisica(),
                    itens = List(3) { umItem("SKU-$it", valorUnitario = "166.71") },
                )

            val tributacao = calculadora.calcular(pedido)

            assertThat(tributacao.itens)
                .extracting<BigDecimal> { it.valorTributo }
                .containsOnly(BigDecimal("20.01"))
            assertThat(tributacao.totalTributos).isEqualTo(BigDecimal("60.03"))
        }

        @Test
        fun `meio centavo é arredondado para o par (HALF_EVEN)`() {
            val pedido = umPedido(cliente = umaPessoaFisica(), itens = listOf(umItem(valorUnitario = "2000.30")))

            assertThat(
                calculadora
                    .calcular(pedido)
                    .itens
                    .single()
                    .valorTributo,
            ).isEqualTo(BigDecimal("300.04"))
        }

        @Test
        fun `pedido isento tem tributo zero com 2 casas`() {
            val pedido = umPedido(cliente = umaPessoaFisica(), itens = listOf(umItem(valorUnitario = "100.00")))

            val tributacao = calculadora.calcular(pedido)

            assertThat(tributacao.itens.single().valorTributo).isEqualTo(BigDecimal("0.00"))
            assertThat(tributacao.totalTributos).isEqualTo(BigDecimal("0.00"))
        }
    }

    @Test
    fun `calculadora não guarda estado entre cálculos`() {
        val primeiro = calculadora.calcular(umPedido(itens = listOf(umItem("A"))))
        val segundo = calculadora.calcular(umPedido(itens = listOf(umItem("B"))))

        assertThat(primeiro.itens).extracting<String> { it.item.idItem }.containsExactly("A")
        assertThat(segundo.itens).extracting<String> { it.item.idItem }.containsExactly("B")
    }
}

package br.com.itau.notafiscal.domain.model

import br.com.itau.notafiscal.domain.service.CalculadoraFrete
import br.com.itau.notafiscal.domain.service.CalculadoraTributo
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.OffsetDateTime

class NotaFiscalTest {
    private val calculadoraTributo = CalculadoraTributo()
    private val calculadoraFrete = CalculadoraFrete(ModoFrete.ACRESCIMO)
    private val dataEmissao = OffsetDateTime.parse("2026-11-18T15:30:00-03:00")

    private fun emitir(
        pedido: Pedido,
        numero: Long = 1,
    ) = NotaFiscal(
        numero = numero,
        dataEmissao = dataEmissao,
        pedido = pedido,
        tributacao = calculadoraTributo.calcular(pedido),
        frete = calculadoraFrete.calcular(pedido),
    )

    @Test
    fun `nota do exemplo do contrato fecha em 3532,40`() {
        val pedido =
            umPedido(
                cliente = umaPessoaJuridica(RegimeTributario.LUCRO_PRESUMIDO),
                itens = listOf(umItem(quantidade = 2, valorUnitario = "1500.00")),
                valorFrete = "50.00",
            )

        val nota = emitir(pedido)

        assertThat(nota.totalItens).isEqualTo(BigDecimal("3000.00"))
        assertThat(nota.totalTributos).isEqualTo(BigDecimal("480.00"))
        assertThat(nota.frete.valor).isEqualTo(BigDecimal("52.40"))
        assertThat(nota.totalNota).isEqualTo(BigDecimal("3532.40"))
    }

    @Test
    fun `total da nota fecha com a soma das partes arredondadas`() {
        val pedido =
            umPedido(
                cliente = umaPessoaFisica(),
                itens = List(3) { umItem("SKU-$it", valorUnitario = "166.71") },
                valorFrete = "50.00",
            )

        val nota = emitir(pedido)

        assertThat(nota.totalItens).isEqualTo(BigDecimal("500.13"))
        assertThat(nota.totalTributos).isEqualTo(BigDecimal("60.03"))
        assertThat(nota.totalNota).isEqualTo(BigDecimal("612.56"))
        assertThat(nota.totalNota).isEqualTo(nota.totalItens + nota.totalTributos + nota.frete.valor)
    }

    @Test
    fun `pedido isento com frete grátis tem total igual ao dos itens`() {
        val pedido = umPedido(cliente = umaPessoaFisica(), itens = listOf(umItem(valorUnitario = "100.00")), valorFrete = "0")

        assertThat(emitir(pedido).totalNota).isEqualTo(BigDecimal("100.00"))
    }

    @Test
    fun `número da nota deve ser positivo`() {
        assertThatThrownBy { emitir(umPedido(), numero = 0) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `nota não aceita tributação calculada para outro pedido`() {
        val pedido = umPedido(itens = listOf(umItem("A")))
        val outroPedido = umPedido(itens = listOf(umItem("A"), umItem("B")))

        assertThatThrownBy {
            NotaFiscal(1, dataEmissao, pedido, calculadoraTributo.calcular(outroPedido), calculadoraFrete.calcular(pedido))
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("tributação")
    }

    @Test
    fun `nota não aceita frete calculado para outro pedido`() {
        val pedido = umPedido(valorFrete = "50.00")
        val outroPedido = umPedido(valorFrete = "80.00")

        assertThatThrownBy {
            NotaFiscal(1, dataEmissao, pedido, calculadoraTributo.calcular(pedido), calculadoraFrete.calcular(outroPedido))
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("frete")
    }
}

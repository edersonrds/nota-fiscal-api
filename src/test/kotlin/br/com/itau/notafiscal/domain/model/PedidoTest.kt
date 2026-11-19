package br.com.itau.notafiscal.domain.model

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.math.BigDecimal

class PedidoTest {
    @Test
    fun `valor total do item é valor unitário vezes quantidade, com 2 casas`() {
        val item = umItem(quantidade = 3, valorUnitario = "33.33")

        assertThat(item.valorTotal).isEqualTo(BigDecimal("99.99"))
    }

    @Test
    fun `valor unitário com zeros extras é aceito e normalizado`() {
        val item = umItem(quantidade = 1, valorUnitario = "10.000")

        assertThat(item.valorTotal).isEqualTo(BigDecimal("10.00"))
    }

    @Test
    fun `total dos itens é a soma dos totais de cada item`() {
        val pedido =
            umPedido(
                itens =
                    listOf(
                        umItem("A", quantidade = 2, valorUnitario = "1500.00"),
                        umItem("B", quantidade = 3, valorUnitario = "0.10"),
                    ),
            )

        assertThat(pedido.totalItens).isEqualTo(BigDecimal("3000.30"))
    }

    @Test
    fun `centavos não somem nem sobram ao somar muitos itens`() {
        val pedido = umPedido(itens = List(10) { umItem("SKU-$it", valorUnitario = "0.10") })

        assertThat(pedido.totalItens).isEqualTo(BigDecimal("1.00"))
    }

    @Test
    fun `pedido sem itens não é criado`() {
        assertThatThrownBy { umPedido(itens = emptyList()) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("ao menos um item")
    }

    @Test
    fun `frete zero é aceito`() {
        assertThat(umPedido(valorFrete = "0").valorFrete).isEqualByComparingTo("0")
    }

    @Test
    fun `frete negativo não é aceito`() {
        assertThatThrownBy { umPedido(valorFrete = "-0.01") }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @ParameterizedTest
    @ValueSource(strings = ["10.005", "0.001", "1500.999"])
    fun `valor unitário com mais de 2 casas é rejeitado, não arredondado`(valor: String) {
        assertThatThrownBy { umItem(valorUnitario = valor) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("2 casas")
    }

    @ParameterizedTest
    @ValueSource(ints = [0, -1])
    fun `quantidade precisa ser maior que zero`(quantidade: Int) {
        assertThatThrownBy { umItem(quantidade = quantidade) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `cópias do pedido não compartilham itens adicionados depois`() {
        val original = umPedido(itens = listOf(umItem("A")))
        val outro = original.copy(itens = original.itens + umItem("B"))

        assertThat(original.itens).extracting<String> { it.idItem }.containsExactly("A")
        assertThat(outro.itens).extracting<String> { it.idItem }.containsExactly("A", "B")
    }
}

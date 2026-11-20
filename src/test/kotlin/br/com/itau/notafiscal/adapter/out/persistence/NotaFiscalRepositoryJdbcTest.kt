package br.com.itau.notafiscal.adapter.out.persistence

import br.com.itau.notafiscal.PostgresDeTeste
import br.com.itau.notafiscal.application.exception.PedidoJaEmitidoException
import br.com.itau.notafiscal.domain.model.NotaFiscal
import br.com.itau.notafiscal.domain.model.umItem
import br.com.itau.notafiscal.domain.model.umPedido
import br.com.itau.notafiscal.domain.service.CalculadoraFrete
import br.com.itau.notafiscal.domain.service.CalculadoraTributo
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import java.time.OffsetDateTime
import java.util.UUID
import java.util.concurrent.Executors

@SpringBootTest
@Import(PostgresDeTeste::class)
class NotaFiscalRepositoryJdbcTest(
    @Autowired private val repositorio: NotaFiscalRepositoryJdbc,
    @Autowired private val gerador: GeradorNumeroNotaPostgres,
    @Autowired private val jdbc: JdbcClient,
) {
    private fun umaNota(idPedido: String = "PED-${UUID.randomUUID()}"): NotaFiscal {
        val pedido = umPedido(idPedido = idPedido, itens = listOf(umItem("A"), umItem("B", quantidade = 2)))
        return NotaFiscal(
            numero = gerador.proximo(),
            dataEmissao = OffsetDateTime.parse("2026-11-18T15:30:00-03:00"),
            pedido = pedido,
            tributacao = CalculadoraTributo().calcular(pedido),
            frete = CalculadoraFrete().calcular(pedido),
        )
    }

    @Test
    fun `nota salva é encontrada pelo id do pedido, com seus itens`() {
        val nota = umaNota()

        repositorio.salvar(nota)

        assertThat(repositorio.numeroDaNotaDoPedido(nota.pedido.idPedido)).isEqualTo(nota.numero)
        val itens =
            jdbc
                .sql("select count(*) from item_nota_fiscal where numero_nota = :numero")
                .param("numero", nota.numero)
                .query(Long::class.javaObjectType)
                .single()
        assertThat(itens).isEqualTo(2)
    }

    @Test
    fun `pedido sem nota não tem número`() {
        assertThat(repositorio.numeroDaNotaDoPedido("PED-INEXISTENTE")).isNull()
    }

    @Test
    fun `segunda nota para o mesmo pedido é recusada com o número da primeira`() {
        val primeira = umaNota()
        repositorio.salvar(primeira)
        val segunda = umaNota(idPedido = primeira.pedido.idPedido)

        assertThatThrownBy { repositorio.salvar(segunda) }
            .isInstanceOfSatisfying(PedidoJaEmitidoException::class.java) {
                assertThat(it.numeroNota).isEqualTo(primeira.numero)
            }
        assertThat(repositorio.numeroDaNotaDoPedido(primeira.pedido.idPedido)).isEqualTo(primeira.numero)
    }

    @Test
    fun `emissões concorrentes não repetem número de nota`() {
        val numeros =
            Executors.newFixedThreadPool(16).use { executor ->
                List(200) { executor.submit<Long> { gerador.proximo() } }.map { it.get() }
            }

        assertThat(numeros).hasSize(200).doesNotHaveDuplicates()
    }
}

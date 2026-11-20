package br.com.itau.notafiscal.application.usecase

import br.com.itau.notafiscal.application.MetricasEmMemoria
import br.com.itau.notafiscal.application.exception.PedidoJaEmitidoException
import br.com.itau.notafiscal.application.port.out.GeradorNumeroNota
import br.com.itau.notafiscal.application.port.out.NotaFiscalRepository
import br.com.itau.notafiscal.application.port.out.SistemaEntrega
import br.com.itau.notafiscal.application.port.out.SistemaEstoque
import br.com.itau.notafiscal.application.port.out.SistemaFinanceiro
import br.com.itau.notafiscal.application.port.out.SistemaRegistro
import br.com.itau.notafiscal.application.service.NotificadorSistemas
import br.com.itau.notafiscal.domain.exception.RegimeTributarioInvalidoException
import br.com.itau.notafiscal.domain.model.ModoFrete
import br.com.itau.notafiscal.domain.model.NotaFiscal
import br.com.itau.notafiscal.domain.model.RegimeTributario
import br.com.itau.notafiscal.domain.model.umItem
import br.com.itau.notafiscal.domain.model.umPedido
import br.com.itau.notafiscal.domain.model.umaPessoaJuridica
import br.com.itau.notafiscal.domain.service.CalculadoraFrete
import br.com.itau.notafiscal.domain.service.CalculadoraTributo
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicLong

class EmitirNotaFiscalServiceTest {
    private class RepositorioEmMemoria : NotaFiscalRepository {
        val notas = ConcurrentHashMap<String, NotaFiscal>()

        override fun salvar(nota: NotaFiscal) {
            val existente = notas.putIfAbsent(nota.pedido.idPedido, nota)
            if (existente != null) throw PedidoJaEmitidoException(existente.pedido.idPedido, existente.numero)
        }

        override fun numeroDaNotaDoPedido(idPedido: String) = notas[idPedido]?.numero
    }

    private val ultimoNumero = AtomicLong()
    private val repositorio = RepositorioEmMemoria()
    private val notasNotificadas = ConcurrentLinkedQueue<Long>()
    private val metricas = MetricasEmMemoria()
    private val notificador =
        NotificadorSistemas(
            estoque = SistemaEstoque { _, _ -> },
            registro = SistemaRegistro { notasNotificadas.add(it.numero) },
            entrega = SistemaEntrega { },
            financeiro = SistemaFinanceiro { },
            metricas = metricas,
        )
    private val relogio = Clock.fixed(Instant.parse("2026-11-18T18:30:00.123Z"), ZoneId.of("America/Sao_Paulo"))
    private val service =
        EmitirNotaFiscalService(
            calculadoraTributo = CalculadoraTributo(),
            calculadoraFrete = CalculadoraFrete(ModoFrete.ACRESCIMO),
            geradorNumeroNota = GeradorNumeroNota { ultimoNumero.incrementAndGet() },
            notaFiscalRepository = repositorio,
            notificadorSistemas = notificador,
            metricas = metricas,
            relogio = relogio,
        )

    @Test
    fun `nota emitida notifica os sistemas`() {
        val nota = service.emitir(umPedido())

        assertThat(notasNotificadas).containsExactly(nota.numero)
    }

    @Test
    fun `nota emitida é contada na métrica e pedido recusado não é`() {
        val nota = service.emitir(umPedido(idPedido = "PED-M1"))
        assertThatThrownBy { service.emitir(umPedido(idPedido = "PED-M1")) }

        assertThat(metricas.emitidas).containsExactly(nota.numero)
    }

    @Test
    fun `pedido rejeitado ou repetido não notifica os sistemas`() {
        service.emitir(umPedido(idPedido = "PED-X"))
        notasNotificadas.clear()

        assertThatThrownBy { service.emitir(umPedido(idPedido = "PED-X")) }
        assertThatThrownBy { service.emitir(umPedido(cliente = umaPessoaJuridica(RegimeTributario.OUTROS), idPedido = "PED-Y")) }
        assertThat(notasNotificadas).isEmpty()
    }

    @Test
    fun `nota recebe o número do gerador e a data do relógio, sem fração de segundo`() {
        val nota = service.emitir(umPedido())

        assertThat(nota.numero).isEqualTo(1)
        assertThat(nota.dataEmissao).isEqualTo(OffsetDateTime.parse("2026-11-18T15:30:00-03:00"))
    }

    @Test
    fun `nota emitida é salva`() {
        val nota = service.emitir(umPedido())

        assertThat(repositorio.notas).containsEntry(nota.pedido.idPedido, nota)
    }

    @Test
    fun `pedido rejeitado não consome número de nota nem é salvo`() {
        val pedidoInvalido = umPedido(cliente = umaPessoaJuridica(RegimeTributario.OUTROS), idPedido = "PED-INVALIDO")

        assertThatThrownBy { service.emitir(pedidoInvalido) }
            .isInstanceOf(RegimeTributarioInvalidoException::class.java)
        assertThat(repositorio.notas).isEmpty()
        assertThat(service.emitir(umPedido()).numero).isEqualTo(1)
    }

    @Test
    fun `pedido já emitido é recusado com o número original e não consome número`() {
        val primeira = service.emitir(umPedido(idPedido = "PED-1"))

        assertThatThrownBy { service.emitir(umPedido(idPedido = "PED-1")) }
            .isInstanceOfSatisfying(PedidoJaEmitidoException::class.java) {
                assertThat(it.numeroNota).isEqualTo(primeira.numero)
            }
        assertThat(ultimoNumero.get()).isEqualTo(1)
    }

    @Test
    fun `segunda emissão não acumula itens da anterior`() {
        val primeira = service.emitir(umPedido(idPedido = "PED-A", itens = listOf(umItem("A"))))
        val segunda = service.emitir(umPedido(idPedido = "PED-B", itens = listOf(umItem("B"), umItem("C"))))

        assertThat(primeira.tributacao.itens).extracting<String> { it.item.idItem }.containsExactly("A")
        assertThat(segunda.tributacao.itens).extracting<String> { it.item.idItem }.containsExactly("B", "C")
        assertThat(listOf(primeira.numero, segunda.numero)).containsExactly(1L, 2L)
    }
}

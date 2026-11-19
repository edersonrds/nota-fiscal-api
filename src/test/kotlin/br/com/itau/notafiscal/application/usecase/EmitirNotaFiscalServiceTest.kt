package br.com.itau.notafiscal.application.usecase

import br.com.itau.notafiscal.application.port.out.GeradorNumeroNota
import br.com.itau.notafiscal.domain.exception.RegimeTributarioInvalidoException
import br.com.itau.notafiscal.domain.model.ModoFrete
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
import java.util.concurrent.atomic.AtomicLong

class EmitirNotaFiscalServiceTest {
    private val ultimoNumero = AtomicLong()
    private val relogio = Clock.fixed(Instant.parse("2026-11-18T18:30:00.123Z"), ZoneId.of("America/Sao_Paulo"))
    private val service =
        EmitirNotaFiscalService(
            calculadoraTributo = CalculadoraTributo(),
            calculadoraFrete = CalculadoraFrete(ModoFrete.ACRESCIMO),
            geradorNumeroNota = GeradorNumeroNota { ultimoNumero.incrementAndGet() },
            relogio = relogio,
        )

    @Test
    fun `nota recebe o número do gerador e a data do relógio, sem fração de segundo`() {
        val nota = service.emitir(umPedido())

        assertThat(nota.numero).isEqualTo(1)
        assertThat(nota.dataEmissao).isEqualTo(OffsetDateTime.parse("2026-11-18T15:30:00-03:00"))
    }

    @Test
    fun `pedido rejeitado não consome número de nota`() {
        val pedidoInvalido = umPedido(cliente = umaPessoaJuridica(RegimeTributario.OUTROS))

        assertThatThrownBy { service.emitir(pedidoInvalido) }
            .isInstanceOf(RegimeTributarioInvalidoException::class.java)
        assertThat(service.emitir(umPedido()).numero).isEqualTo(1)
    }

    @Test
    fun `segunda emissão não acumula itens da anterior`() {
        val primeira = service.emitir(umPedido(itens = listOf(umItem("A"))))
        val segunda = service.emitir(umPedido(itens = listOf(umItem("B"), umItem("C"))))

        assertThat(primeira.tributacao.itens).extracting<String> { it.item.idItem }.containsExactly("A")
        assertThat(segunda.tributacao.itens).extracting<String> { it.item.idItem }.containsExactly("B", "C")
        assertThat(listOf(primeira.numero, segunda.numero)).containsExactly(1L, 2L)
    }
}

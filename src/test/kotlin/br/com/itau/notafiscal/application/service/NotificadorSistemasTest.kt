package br.com.itau.notafiscal.application.service

import br.com.itau.notafiscal.application.port.out.SistemaEntrega
import br.com.itau.notafiscal.application.port.out.SistemaEstoque
import br.com.itau.notafiscal.application.port.out.SistemaFinanceiro
import br.com.itau.notafiscal.application.port.out.SistemaRegistro
import br.com.itau.notafiscal.domain.model.ModoFrete
import br.com.itau.notafiscal.domain.model.NotaFiscal
import br.com.itau.notafiscal.domain.model.umItem
import br.com.itau.notafiscal.domain.model.umPedido
import br.com.itau.notafiscal.domain.service.CalculadoraFrete
import br.com.itau.notafiscal.domain.service.CalculadoraTributo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.OffsetDateTime
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.time.measureTime
import kotlin.time.toKotlinDuration

class NotificadorSistemasTest {
    private val latencia = Duration.ofMillis(300)
    private val chamadas = ConcurrentLinkedQueue<String>()
    private val threadsVirtuais = ConcurrentLinkedQueue<Boolean>()

    private fun chamar(destino: String) {
        threadsVirtuais.add(Thread.currentThread().isVirtual)
        Thread.sleep(latencia)
        chamadas.add(destino)
    }

    private val notificador =
        NotificadorSistemas(
            estoque = SistemaEstoque { _, item -> chamar("estoque:${item.item.idItem}") },
            registro = SistemaRegistro { chamar("registro") },
            entrega = SistemaEntrega { chamar("entrega") },
            financeiro = SistemaFinanceiro { chamar("financeiro") },
        )

    private fun notaCom(quantidadeDeItens: Int): NotaFiscal {
        val pedido = umPedido(itens = List(quantidadeDeItens) { umItem("SKU-$it") })
        return NotaFiscal(
            numero = 1,
            dataEmissao = OffsetDateTime.parse("2026-11-18T15:30:00-03:00"),
            pedido = pedido,
            tributacao = CalculadoraTributo().calcular(pedido),
            frete = CalculadoraFrete(ModoFrete.ACRESCIMO).calcular(pedido),
        )
    }

    @Test
    fun `notifica os quatro sistemas, com uma baixa de estoque por item`() {
        notificador.notificar(notaCom(quantidadeDeItens = 2))

        assertThat(chamadas).containsExactlyInAnyOrder("estoque:SKU-0", "estoque:SKU-1", "registro", "entrega", "financeiro")
    }

    @Test
    fun `pedido com mais de 6 itens demora o mesmo que pedido com 1 item`() {
        val comUmItem = measureTime { notificador.notificar(notaCom(quantidadeDeItens = 1)) }
        val comDezItens = measureTime { notificador.notificar(notaCom(quantidadeDeItens = 10)) }

        val emSequencia = latencia.multipliedBy(13).toKotlinDuration()
        assertThat(comDezItens).isLessThan(latencia.multipliedBy(2).toKotlinDuration())
        assertThat(comDezItens).isLessThan(emSequencia / 4)
        assertThat(comDezItens - comUmItem).isLessThan(latencia.toKotlinDuration())
    }

    @Test
    fun `latência simulada de cada sistema é mantida`() {
        val tempo = measureTime { notificador.notificar(notaCom(quantidadeDeItens = 1)) }

        assertThat(tempo).isGreaterThanOrEqualTo(latencia.toKotlinDuration())
    }

    @Test
    fun `cada notificação roda numa virtual thread`() {
        notificador.notificar(notaCom(quantidadeDeItens = 3))

        assertThat(threadsVirtuais).hasSize(6).containsOnly(true)
    }

    @Test
    fun `falha de um sistema não impede a notificação dos outros`() {
        val notificadorComFalha =
            NotificadorSistemas(
                estoque = SistemaEstoque { _, item -> chamar("estoque:${item.item.idItem}") },
                registro = SistemaRegistro { error("registro fora do ar") },
                entrega = SistemaEntrega { chamar("entrega") },
                financeiro = SistemaFinanceiro { chamar("financeiro") },
            )

        notificadorComFalha.notificar(notaCom(quantidadeDeItens = 1))

        assertThat(chamadas).containsExactlyInAnyOrder("estoque:SKU-0", "entrega", "financeiro")
    }
}

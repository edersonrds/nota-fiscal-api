package br.com.itau.notafiscal.application.usecase

import br.com.itau.notafiscal.application.exception.PedidoJaEmitidoException
import br.com.itau.notafiscal.application.port.`in`.EmitirNotaFiscalUseCase
import br.com.itau.notafiscal.application.port.out.GeradorNumeroNota
import br.com.itau.notafiscal.application.port.out.NotaFiscalRepository
import br.com.itau.notafiscal.application.service.NotificadorSistemas
import br.com.itau.notafiscal.domain.model.NotaFiscal
import br.com.itau.notafiscal.domain.model.Pedido
import br.com.itau.notafiscal.domain.service.CalculadoraFrete
import br.com.itau.notafiscal.domain.service.CalculadoraTributo
import java.time.Clock
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

class EmitirNotaFiscalService(
    private val calculadoraTributo: CalculadoraTributo,
    private val calculadoraFrete: CalculadoraFrete,
    private val geradorNumeroNota: GeradorNumeroNota,
    private val notaFiscalRepository: NotaFiscalRepository,
    private val notificadorSistemas: NotificadorSistemas,
    private val relogio: Clock,
) : EmitirNotaFiscalUseCase {
    override fun emitir(pedido: Pedido): NotaFiscal {
        val numeroExistente = notaFiscalRepository.numeroDaNotaDoPedido(pedido.idPedido)
        if (numeroExistente != null) throw PedidoJaEmitidoException(pedido.idPedido, numeroExistente)

        val tributacao = calculadoraTributo.calcular(pedido)
        val frete = calculadoraFrete.calcular(pedido)
        val nota =
            NotaFiscal(
                numero = geradorNumeroNota.proximo(),
                dataEmissao = OffsetDateTime.now(relogio).truncatedTo(ChronoUnit.SECONDS),
                pedido = pedido,
                tributacao = tributacao,
                frete = frete,
            )
        notaFiscalRepository.salvar(nota)
        notificadorSistemas.notificar(nota)
        return nota
    }
}

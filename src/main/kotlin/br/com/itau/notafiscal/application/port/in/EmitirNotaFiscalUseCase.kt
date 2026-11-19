package br.com.itau.notafiscal.application.port.`in`

import br.com.itau.notafiscal.domain.model.NotaFiscal
import br.com.itau.notafiscal.domain.model.Pedido

interface EmitirNotaFiscalUseCase {
    fun emitir(pedido: Pedido): NotaFiscal
}

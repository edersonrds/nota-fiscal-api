package br.com.itau.notafiscal.application.exception

class PedidoJaEmitidoException(
    val idPedido: String,
    val numeroNota: Long,
) : RuntimeException("Pedido $idPedido já teve a nota fiscal $numeroNota emitida; nenhuma nota nova foi gerada.")

package br.com.itau.notafiscal.domain.exception

sealed class RegraNegocioException(
    val codigo: String,
    mensagem: String,
) : RuntimeException(mensagem)

class EnderecoEntregaAusenteException :
    RegraNegocioException(
        codigo = "ENDERECO_ENTREGA_AUSENTE",
        mensagem = "Pedido sem endereço de entrega (finalidade ENTREGA ou COBRANCA_ENTREGA) não pode ter nota gerada.",
    )

class EnderecoEntregaAmbiguoException(
    quantidade: Int,
) : RegraNegocioException(
        codigo = "ENDERECO_ENTREGA_AMBIGUO",
        mensagem = "Pedido com $quantidade endereços de entrega (ENTREGA ou COBRANCA_ENTREGA); informe exatamente um.",
    )

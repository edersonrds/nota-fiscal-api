package br.com.itau.notafiscal.domain.exception

import br.com.itau.notafiscal.domain.model.RegimeTributario

sealed class RegraNegocioException(
    val codigo: String,
    mensagem: String,
) : RuntimeException(mensagem)

class RegimeTributarioInvalidoException(
    regime: RegimeTributario?,
) : RegraNegocioException(
        codigo = "REGIME_TRIBUTARIO_INVALIDO",
        mensagem =
            if (regime == null) {
                "Pessoa jurídica sem regime de tributação informado não pode ter nota gerada."
            } else {
                "Pessoa jurídica com regime de tributação $regime não pode ter nota gerada."
            },
    )

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

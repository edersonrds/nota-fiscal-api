package br.com.itau.notafiscal.domain.model

import br.com.itau.notafiscal.domain.exception.EnderecoEntregaAmbiguoException
import br.com.itau.notafiscal.domain.exception.EnderecoEntregaAusenteException

sealed interface Cliente {
    val documento: String
    val nome: String
    val enderecos: List<Endereco>

    fun enderecoDeEntrega(): Endereco {
        val candidatos = enderecos.filter { it.finalidade.defineEntrega }
        return when (candidatos.size) {
            0 -> throw EnderecoEntregaAusenteException()
            1 -> candidatos.single()
            else -> throw EnderecoEntregaAmbiguoException(candidatos.size)
        }
    }
}

data class PessoaFisica(
    override val documento: String,
    override val nome: String,
    override val enderecos: List<Endereco>,
) : Cliente {
    init {
        require(documento.length == 11 && documento.all { it.isDigit() }) { "CPF deve ter 11 dígitos" }
        require(nome.isNotBlank()) { "nome é obrigatório" }
    }
}

data class PessoaJuridica(
    override val documento: String,
    override val nome: String,
    override val enderecos: List<Endereco>,
    val regimeTributario: RegimeTributario?,
) : Cliente {
    init {
        require(documento.length == 14 && documento.all { it.isDigit() }) { "CNPJ deve ter 14 dígitos" }
        require(nome.isNotBlank()) { "nome é obrigatório" }
    }
}

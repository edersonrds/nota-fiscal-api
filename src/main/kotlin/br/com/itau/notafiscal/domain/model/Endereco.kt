package br.com.itau.notafiscal.domain.model

data class Endereco(
    val finalidade: Finalidade,
    val regiao: Regiao,
    val logradouro: String,
    val numero: String,
    val cidade: String,
    val estado: String,
    val cep: String,
)

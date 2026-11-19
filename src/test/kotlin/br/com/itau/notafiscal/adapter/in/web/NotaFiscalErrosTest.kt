package br.com.itau.notafiscal.adapter.`in`.web

import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.post
import tools.jackson.databind.json.JsonMapper
import java.math.BigDecimal
import java.util.stream.Stream

typealias Json = MutableMap<String, Any?>

@SpringBootTest
@AutoConfigureMockMvc
class NotaFiscalErrosTest(
    @Autowired private val mockMvc: MockMvc,
) {
    private val jsonMapper = JsonMapper.builder().build()

    private fun enviar(ajuste: Json.() -> Unit): ResultActionsDsl =
        mockMvc.post("/notas-fiscais") {
            contentType = MediaType.APPLICATION_JSON
            content = jsonMapper.writeValueAsString(pedidoValido().apply(ajuste))
        }

    @Nested
    inner class `400 - entrada inválida` {
        @ParameterizedTest(name = "{0}")
        @MethodSource("br.com.itau.notafiscal.adapter.in.web.NotaFiscalErrosTest#entradasInvalidas")
        fun `rejeita a entrada apontando o campo`(
            descricao: String,
            ajuste: Json.() -> Unit,
            campo: String,
            mensagem: String,
        ) {
            enviar(ajuste).andExpect {
                status { isBadRequest() }
                content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
                jsonPath("$.codigo") { value("REQUISICAO_INVALIDA") }
                jsonPath("$.campos.length()") { value(1) }
                jsonPath("$.campos[0].campo") { value(campo) }
                jsonPath("$.campos[0].mensagem") { value(containsString(mensagem)) }
            }
        }

        @Test
        fun `JSON malformado devolve 400 sem lista de campos`() {
            mockMvc
                .post("/notas-fiscais") {
                    contentType = MediaType.APPLICATION_JSON
                    content = "{ \"id_pedido\": "
                }.andExpect {
                    status { isBadRequest() }
                    jsonPath("$.codigo") { value("REQUISICAO_INVALIDA") }
                    jsonPath("$.detail") { value("O corpo da requisição não é um JSON válido.") }
                    jsonPath("$.campos") { isEmpty() }
                }
        }

        @Test
        fun `vários campos inválidos são listados juntos`() {
            enviar {
                itens()[0]["quantidade"] = 0
                this["valor_frete"] = BigDecimal("-1.00")
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.campos[*].campo") { value(contains("itens[0].quantidade", "valor_frete")) }
            }
        }
    }

    @Nested
    inner class `422 - regra de negócio` {
        @ParameterizedTest(name = "{0}")
        @MethodSource("br.com.itau.notafiscal.adapter.in.web.NotaFiscalErrosTest#regrasDeNegocio")
        fun `rejeita o pedido com o código da regra`(
            descricao: String,
            ajuste: Json.() -> Unit,
            codigo: String,
        ) {
            enviar(ajuste).andExpect {
                status { isUnprocessableContent() }
                content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
                jsonPath("$.codigo") { value(codigo) }
                jsonPath("$.title") { value("Regra de negócio violada") }
                jsonPath("$.detail") { isNotEmpty() }
            }
        }
    }

    @Test
    fun `pessoa física com regime OUTROS é emitida, porque o regime é ignorado`() {
        enviar { cliente()["regime_tributacao"] = "OUTROS" }.andExpect {
            status { isCreated() }
        }
    }

    companion object {
        private fun caso(
            descricao: String,
            campo: String,
            mensagem: String,
            ajuste: Json.() -> Unit,
        ) = Arguments.of(descricao, ajuste, campo, mensagem)

        @JvmStatic
        fun entradasInvalidas(): Stream<Arguments> =
            Stream.of(
                caso("id_pedido ausente", "id_pedido", "é obrigatório") { remove("id_pedido") },
                caso("id_pedido em branco", "id_pedido", "é obrigatório") { this["id_pedido"] = "  " },
                caso("data em formato inválido", "data", "formato inválido") { this["data"] = "18/11/2026" },
                caso("cliente ausente", "cliente", "é obrigatório") { remove("cliente") },
                caso("tipo_pessoa desconhecido", "cliente.tipo_pessoa", "aceitos: FISICA, JURIDICA") {
                    cliente()["tipo_pessoa"] = "EMPRESA"
                },
                caso("CPF com 10 dígitos", "cliente.documento", "11 (CPF)") { cliente()["documento"] = "1234567890" },
                caso("CPF formatado", "cliente.documento", "só dígitos") { cliente()["documento"] = "123.456.789-01" },
                caso("pessoa jurídica com CPF", "cliente.documento", "14 (CNPJ)") {
                    cliente()["tipo_pessoa"] = "JURIDICA"
                    cliente()["regime_tributacao"] = "LUCRO_REAL"
                },
                caso("nome em branco", "cliente.nome", "é obrigatório") { cliente()["nome"] = "" },
                caso("regime_tributacao desconhecido", "cliente.regime_tributacao", "aceitos:") {
                    cliente()["regime_tributacao"] = "MEI"
                },
                caso("região desconhecida", "cliente.enderecos[0].regiao", "aceitos: NORTE") {
                    enderecos()[0]["regiao"] = "LESTE"
                },
                caso("finalidade desconhecida", "cliente.enderecos[0].finalidade", "aceitos: ENTREGA") {
                    enderecos()[0]["finalidade"] = "RESIDENCIAL"
                },
                caso("itens ausente", "itens", "é obrigatório") { remove("itens") },
                caso("itens vazio", "itens", "ao menos um item") { this["itens"] = mutableListOf<Json>() },
                caso("quantidade zero", "itens[0].quantidade", "maior que zero") { itens()[0]["quantidade"] = 0 },
                caso("quantidade fracionada", "itens[0].quantidade", "formato inválido") {
                    itens()[0]["quantidade"] = BigDecimal("1.5")
                },
                caso("valor_unitario zero", "itens[0].valor_unitario", "maior que zero") {
                    itens()[0]["valor_unitario"] = BigDecimal("0.00")
                },
                caso("valor_unitario com 3 casas", "itens[0].valor_unitario", "2 casas") {
                    itens()[0]["valor_unitario"] = BigDecimal("10.005")
                },
                caso("valor_frete ausente", "valor_frete", "é obrigatório") { remove("valor_frete") },
                caso("valor_frete negativo", "valor_frete", "negativo") { this["valor_frete"] = BigDecimal("-0.01") },
                caso("valor_frete com 3 casas", "valor_frete", "2 casas") { this["valor_frete"] = BigDecimal("1.001") },
            )

        private fun regra(
            descricao: String,
            codigo: String,
            ajuste: Json.() -> Unit,
        ) = Arguments.of(descricao, ajuste, codigo)

        @JvmStatic
        fun regrasDeNegocio(): Stream<Arguments> =
            Stream.of(
                regra("pessoa jurídica sem regime", "REGIME_TRIBUTARIO_INVALIDO") { viraPessoaJuridica(regime = null) },
                regra("pessoa jurídica com regime OUTROS", "REGIME_TRIBUTARIO_INVALIDO") { viraPessoaJuridica(regime = "OUTROS") },
                regra("só endereço de cobrança", "ENDERECO_ENTREGA_AUSENTE") { enderecos()[0]["finalidade"] = "COBRANCA" },
                regra("nenhum endereço", "ENDERECO_ENTREGA_AUSENTE") { cliente()["enderecos"] = mutableListOf<Json>() },
                regra("dois endereços de entrega", "ENDERECO_ENTREGA_AMBIGUO") {
                    enderecos().add(umEndereco(finalidade = "COBRANCA_ENTREGA", regiao = "NORTE"))
                },
            )

        private fun Json.viraPessoaJuridica(regime: String?) {
            cliente()["tipo_pessoa"] = "JURIDICA"
            cliente()["documento"] = "12345678000199"
            if (regime == null) cliente().remove("regime_tributacao") else cliente()["regime_tributacao"] = regime
        }

        @Suppress("UNCHECKED_CAST")
        private fun Json.cliente() = this["cliente"] as Json

        @Suppress("UNCHECKED_CAST")
        private fun Json.enderecos() = cliente()["enderecos"] as MutableList<Json>

        @Suppress("UNCHECKED_CAST")
        private fun Json.itens() = this["itens"] as MutableList<Json>

        private fun umEndereco(
            finalidade: String = "ENTREGA",
            regiao: String = "SUL",
        ): Json =
            mutableMapOf(
                "finalidade" to finalidade,
                "regiao" to regiao,
                "logradouro" to "Rua A",
                "numero" to "10",
                "cidade" to "Curitiba",
                "estado" to "PR",
                "cep" to "80000000",
            )

        private fun pedidoValido(): Json =
            mutableMapOf(
                "id_pedido" to "PED-3001",
                "data" to "2026-11-18",
                "cliente" to
                    mutableMapOf(
                        "tipo_pessoa" to "FISICA",
                        "documento" to "12345678901",
                        "nome" to "Maria Silva",
                        "enderecos" to mutableListOf(umEndereco()),
                    ),
                "itens" to
                    mutableListOf(
                        mutableMapOf(
                            "id_item" to "SKU-1",
                            "descricao" to "Livro",
                            "quantidade" to 1,
                            "valor_unitario" to BigDecimal("100.00"),
                        ),
                    ),
                "valor_frete" to BigDecimal("10.00"),
            )
    }
}

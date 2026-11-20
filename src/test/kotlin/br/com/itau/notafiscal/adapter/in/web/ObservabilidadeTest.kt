package br.com.itau.notafiscal.adapter.`in`.web

import br.com.itau.notafiscal.PostgresDeTeste
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.matchesPattern
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresDeTeste::class)
class ObservabilidadeTest(
    @Autowired private val mockMvc: MockMvc,
) {
    private fun pedido(
        tipoPessoa: String = "FISICA",
        documento: String = "12345678901",
        regime: String = "",
    ) = """
        {
          "id_pedido": "PED-${UUID.randomUUID()}",
          "data": "2026-11-18",
          "cliente": {
            "tipo_pessoa": "$tipoPessoa", "documento": "$documento", "nome": "Cliente"$regime,
            "enderecos": [ { "finalidade": "ENTREGA", "regiao": "NORDESTE", "logradouro": "Rua A", "numero": "1", "cidade": "Recife", "estado": "PE", "cep": "50000000" } ]
          },
          "itens": [ { "id_item": "SKU-1", "descricao": "Livro", "quantidade": 1, "valor_unitario": 100.00 } ],
          "valor_frete": 10.00
        }
    """

    private fun emitir(
        corpo: String,
        correlationId: String? = null,
    ) = mockMvc.post("/notas-fiscais") {
        contentType = MediaType.APPLICATION_JSON
        content = corpo
        if (correlationId != null) header("X-Correlation-Id", correlationId)
    }

    private fun prometheus(): String =
        mockMvc
            .get("/actuator/prometheus")
            .andExpect { status { isOk() } }
            .andReturn()
            .response.contentAsString

    @Test
    fun `health, liveness e readiness respondem UP com o banco`() {
        listOf("/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness").forEach { caminho ->
            mockMvc.get(caminho).andExpect {
                status { isOk() }
                jsonPath("$.status") { value("UP") }
            }
        }
    }

    @Test
    fun `info expõe nome e versão da aplicação`() {
        mockMvc.get("/actuator/info").andExpect {
            status { isOk() }
            jsonPath("$.build.artifact") { value("nota-fiscal-api") }
            jsonPath("$.build.version") { isNotEmpty() }
        }
    }

    @Test
    fun `nota emitida aparece na métrica de negócio com tipo de pessoa e região`() {
        emitir(pedido()).andExpect { status { isCreated() } }

        assertThat(prometheus()).containsPattern(
            "notafiscal_emitidas_total\\{application=\"nota-fiscal-api\",regiao_entrega=\"NORDESTE\",tipo_pessoa=\"FISICA\"} \\d",
        )
    }

    @Test
    fun `recusas aparecem na métrica com o código do motivo`() {
        emitir(pedido(tipoPessoa = "JURIDICA", documento = "12345678000199", regime = ", \"regime_tributacao\": \"OUTROS\""))
            .andExpect { status { isUnprocessableContent() } }
        emitir("{}").andExpect { status { isBadRequest() } }

        assertThat(prometheus())
            .contains("notafiscal_recusadas_total{application=\"nota-fiscal-api\",codigo=\"REGIME_TRIBUTARIO_INVALIDO\"}")
            .contains("notafiscal_recusadas_total{application=\"nota-fiscal-api\",codigo=\"REQUISICAO_INVALIDA\"}")
    }

    @Test
    fun `latência do endpoint é medida automaticamente`() {
        emitir(pedido()).andExpect { status { isCreated() } }

        assertThat(prometheus()).contains("http_server_requests_seconds_count{application=\"nota-fiscal-api\"")
            .containsPattern("uri=\"/notas-fiscais\"")
    }

    @Test
    fun `correlation id recebido é devolvido no cabeçalho e no erro`() {
        emitir("{}", correlationId = "pedido-abc-123").andExpect {
            header { string("X-Correlation-Id", "pedido-abc-123") }
            jsonPath("$.correlation_id") { value("pedido-abc-123") }
        }
    }

    @Test
    fun `sem correlation id, um novo é gerado`() {
        emitir(pedido()).andExpect {
            header { string("X-Correlation-Id", matchesPattern("[0-9a-f-]{36}")) }
        }
    }

    @Test
    fun `correlation id com caracteres inválidos é substituído, evitando injeção no log`() {
        emitir(pedido(), correlationId = "abc\r\nnivel=ERROR falso").andExpect {
            header { string("X-Correlation-Id", matchesPattern("[0-9a-f-]{36}")) }
        }
    }
}

package br.com.itau.notafiscal.domain.model

import br.com.itau.notafiscal.domain.exception.EnderecoEntregaAmbiguoException
import br.com.itau.notafiscal.domain.exception.EnderecoEntregaAusenteException
import br.com.itau.notafiscal.domain.model.Finalidade.COBRANCA
import br.com.itau.notafiscal.domain.model.Finalidade.COBRANCA_ENTREGA
import br.com.itau.notafiscal.domain.model.Finalidade.ENTREGA
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class ClienteTest {
    @Nested
    inner class `endereço de entrega` {
        @Test
        fun `único endereço COBRANCA_ENTREGA é o de entrega`() {
            val cliente = umaPessoaFisica(enderecos = listOf(umEndereco(COBRANCA_ENTREGA, Regiao.SUL)))

            assertThat(cliente.enderecoDeEntrega().regiao).isEqualTo(Regiao.SUL)
        }

        @Test
        fun `endereços COBRANCA não contam como entrega`() {
            val cliente =
                umaPessoaFisica(
                    enderecos =
                        listOf(
                            umEndereco(COBRANCA, Regiao.NORTE),
                            umEndereco(ENTREGA, Regiao.NORDESTE),
                            umEndereco(COBRANCA, Regiao.SUL),
                        ),
                )

            assertThat(cliente.enderecoDeEntrega().regiao).isEqualTo(Regiao.NORDESTE)
        }

        @Test
        fun `pedido sem endereço de entrega é rejeitado`() {
            val cliente = umaPessoaFisica(enderecos = listOf(umEndereco(COBRANCA)))

            assertThatThrownBy { cliente.enderecoDeEntrega() }
                .isInstanceOf(EnderecoEntregaAusenteException::class.java)
        }

        @Test
        fun `pedido sem nenhum endereço é rejeitado`() {
            val cliente = umaPessoaJuridica(enderecos = emptyList())

            assertThatThrownBy { cliente.enderecoDeEntrega() }
                .isInstanceOf(EnderecoEntregaAusenteException::class.java)
        }

        @Test
        fun `ENTREGA e COBRANCA_ENTREGA juntos são ambíguos`() {
            val cliente = umaPessoaFisica(enderecos = listOf(umEndereco(ENTREGA), umEndereco(COBRANCA_ENTREGA)))

            assertThatThrownBy { cliente.enderecoDeEntrega() }
                .isInstanceOf(EnderecoEntregaAmbiguoException::class.java)
                .hasMessageContaining("2 endereços")
        }

        @Test
        fun `dois endereços ENTREGA são ambíguos`() {
            val cliente = umaPessoaFisica(enderecos = listOf(umEndereco(ENTREGA), umEndereco(ENTREGA)))

            assertThatThrownBy { cliente.enderecoDeEntrega() }
                .isInstanceOf(EnderecoEntregaAmbiguoException::class.java)
        }
    }

    @Nested
    inner class documento {
        @Test
        fun `pessoa física exige CPF com 11 dígitos`() {
            assertThatThrownBy { PessoaFisica("123", "Maria", listOf(umEndereco())) }
                .isInstanceOf(IllegalArgumentException::class.java)
        }

        @Test
        fun `pessoa jurídica exige CNPJ com 14 dígitos`() {
            assertThatThrownBy { PessoaJuridica("12.345.678/0001-99", "Empresa", listOf(umEndereco()), null) }
                .isInstanceOf(IllegalArgumentException::class.java)
        }

        @Test
        fun `pessoa jurídica sem regime pode ser criada, a recusa é do cálculo do tributo`() {
            assertThat(umaPessoaJuridica(regimeTributario = null).regimeTributario).isNull()
        }
    }
}

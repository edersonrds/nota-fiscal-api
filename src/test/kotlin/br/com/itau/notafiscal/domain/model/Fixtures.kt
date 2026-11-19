package br.com.itau.notafiscal.domain.model

import java.math.BigDecimal
import java.time.LocalDate

fun umEndereco(
    finalidade: Finalidade = Finalidade.COBRANCA_ENTREGA,
    regiao: Regiao = Regiao.SUDESTE,
) = Endereco(
    finalidade = finalidade,
    regiao = regiao,
    logradouro = "Av. Paulista",
    numero = "1000",
    cidade = "São Paulo",
    estado = "SP",
    cep = "01310100",
)

fun umaPessoaFisica(enderecos: List<Endereco> = listOf(umEndereco())) =
    PessoaFisica(documento = "12345678901", nome = "Maria Silva", enderecos = enderecos)

fun umaPessoaJuridica(
    regimeTributario: RegimeTributario? = RegimeTributario.LUCRO_PRESUMIDO,
    enderecos: List<Endereco> = listOf(umEndereco()),
) = PessoaJuridica(
    documento = "12345678000199",
    nome = "Empresa Exemplo Ltda",
    enderecos = enderecos,
    regimeTributario = regimeTributario,
)

fun umItem(
    idItem: String = "SKU-1",
    quantidade: Int = 1,
    valorUnitario: String = "100.00",
) = Item(idItem = idItem, descricao = "Produto $idItem", quantidade = quantidade, valorUnitario = BigDecimal(valorUnitario))

fun umPedido(
    cliente: Cliente = umaPessoaFisica(),
    itens: List<Item> = listOf(umItem()),
    valorFrete: String = "50.00",
    idPedido: String = "PED-1001",
) = Pedido(
    idPedido = idPedido,
    data = LocalDate.of(2026, 11, 18),
    cliente = cliente,
    itens = itens,
    valorFrete = BigDecimal(valorFrete),
)

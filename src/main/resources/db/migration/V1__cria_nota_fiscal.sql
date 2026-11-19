create sequence nota_fiscal_numero_seq start with 1 increment by 1;

create table nota_fiscal (
    numero            bigint         not null,
    id_pedido         text           not null,
    data_pedido       date           not null,
    data_emissao      timestamptz    not null,
    tipo_pessoa       text           not null,
    documento_cliente text           not null,
    nome_cliente      text           not null,
    regime_tributario text,
    aliquota          numeric(5, 4)  not null,
    regiao_entrega    text           not null,
    frete_valor_base  numeric(15, 2) not null,
    frete_percentual  numeric(5, 4)  not null,
    frete_valor       numeric(15, 2) not null,
    total_itens       numeric(15, 2) not null,
    total_tributos    numeric(15, 2) not null,
    total_nota        numeric(15, 2) not null,
    constraint pk_nota_fiscal primary key (numero),
    constraint uk_nota_fiscal_id_pedido unique (id_pedido)
);

create table item_nota_fiscal (
    numero_nota    bigint         not null,
    sequencia      int            not null,
    id_item        text           not null,
    descricao      text           not null,
    quantidade     int            not null,
    valor_unitario numeric(15, 2) not null,
    valor_total    numeric(15, 2) not null,
    valor_tributo  numeric(15, 2) not null,
    constraint pk_item_nota_fiscal primary key (numero_nota, sequencia),
    constraint fk_item_nota_fiscal_nota foreign key (numero_nota) references nota_fiscal (numero)
);

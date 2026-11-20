# Plano de deploy e rollback

Como uma versão da API Nota Fiscal chega à produção, como se confirma que está saudável e como se volta atrás quando não está.

> **Leia isto primeiro.** O que existe hoje neste repositório é o **CI** (lint, testes e cobertura) e a **imagem Docker** que roda localmente. **Nada do que vem depois do CI foi provisionado ou executado**: a publicação da imagem, os ambientes e o deploy na AWS são **arquitetura-alvo**, desenhada em [arquitetura-aws.md](arquitetura-aws.md). Os valores numéricos (limiares, tempos) são **propostas** a calibrar com a linha de base real.

## Sumário

- [Princípios](#princípios)
- [Implementado e alvo](#implementado-e-alvo)
- [Pipeline de entrega](#pipeline-de-entrega)
- [Estratégia de deploy](#estratégia-de-deploy)
- [Banco de dados e migrações](#banco-de-dados-e-migrações)
- [Feature toggles](#feature-toggles)
- [Checklist de mudança](#checklist-de-mudança)
- [Validação pós-deploy](#validação-pós-deploy)
- [Critérios de rollback](#critérios-de-rollback)
- [Como fazer o rollback](#como-fazer-o-rollback)
- [Hotfix](#hotfix)
- [Versionamento e rastreabilidade](#versionamento-e-rastreabilidade)

## Princípios

1. **Um artefato só.** A imagem aprovada no CI é a mesma que sobe em staging e em produção; só a configuração muda. Nada é recompilado entre ambientes.
2. **Imagem imutável e rastreável.** A tag da imagem é o **hash do commit**, nunca `latest`. Voltar atrás é subir a tag anterior.
3. **Deploy não é release.** Subir o código não precisa ligar o comportamento novo: o que for arriscado entra desligado, atrás de um *feature toggle*.
4. **Rollback rápido e ensaiado.** Voltar atrás tem de levar minutos, sem depender de reverter dado.
5. **Banco compatível com duas versões.** Durante o deploy, a versão antiga e a nova convivem; toda migração precisa servir às duas.
6. **Retentativa segura.** O `id_pedido` é idempotente (409 com o número da nota já emitida), então um cliente que repita a chamada durante um deploy não gera nota duplicada.

## Implementado e alvo

| Etapa | Estado | Observação |
|---|---|---|
| Lint, testes (146) e cobertura mínima de 90% no GitHub Actions | **Implementado** | Roda em todo PR e push na `master` |
| Proteção da `master` (PR obrigatório e check `build`) | **Implementado** | Configurado como *ruleset* no GitHub |
| Imagem Docker construída localmente | **Implementado** | Dockerfile em duas etapas, usuário não-root |
| Build e publicação da imagem no ECR pelo CI | **Alvo** | Não está no workflow |
| Ambiente de staging | **Alvo** | Só desenhado |
| Deploy canário no ECS Fargate | **Alvo** | Só desenhado |
| Alarmes de rollback, AppConfig, Datadog | **Alvo** | A API só expõe o endpoint de métricas |
| Graceful shutdown | **Implementado** | Padrão do Spring Boot 4.1.2 (veja [Estratégia de deploy](#estratégia-de-deploy)) |

## Pipeline de entrega

```
PR  →  CI  →  merge na master  →  imagem (tag = commit)  →  ECR  →  staging  →  validação  →  aprovação da mudança  →  produção (canário)  →  validação  →  100%
```

| # | Etapa | O que acontece | Barra o avanço se |
|---|---|---|---|
| 1 | **Pull request** | Mudança revisada na branch, CI roda no PR | O check `build` falhar (lint, testes ou cobertura abaixo de 90%) |
| 2 | **Merge na `master`** | A `master` só recebe PR com CI verde | Proteção de branch não satisfeita |
| 3 | **Build da imagem** | Imagem com tag = hash do commit, publicada no ECR | O build da imagem falhar |
| 4 | **Staging** | A mesma imagem sobe num ambiente igual ao de produção, com banco próprio | A migração ou a subida falharem |
| 5 | **Validação em staging** | Smoke test completo (emite nota de verdade) e testes de regressão | Qualquer teste falhar |
| 6 | **Aprovação da mudança** | Registro de mudança aprovado (veja o [checklist](#checklist-de-mudança)) | Falta de aprovação ou de janela |
| 7 | **Produção, canário** | Parte pequena do tráfego vai para a versão nova | Alarmes disparam (rollback automático) |
| 8 | **Promoção** | Se o canário passar, 100% do tráfego migra | Alarmes ou decisão manual |
| 9 | **Pós-deploy** | Janela de observação e encerramento da mudança | Critério de rollback atingido |

Os passos 1 e 2 já funcionam. Os demais são o alvo.

## Estratégia de deploy

### Comparação

| Estratégia | Como funciona | Rollback | Custo | Risco |
|---|---|---|---|---|
| **Rolling** | Troca as tarefas aos poucos | Reimplantar a versão anterior | Baixo | Versões convivem sem controle de tráfego |
| **Blue/green** | Sobe a versão nova ao lado e troca todo o tráfego de uma vez | Voltar o tráfego para a antiga (rápido) | 2× as tarefas durante o deploy | Se estiver errada, **todos** os usuários sentem |
| **Canário** | Manda uma fatia pequena do tráfego à versão nova, observa e só então promove | Voltar a fatia (rápido) | 2× as tarefas durante o deploy | Só a fatia é afetada |
| **Linear** | Aumenta a fatia em passos iguais, com tempo de espera entre eles | Voltar a fatia | 2× as tarefas durante o deploy | Exposição crescente |

### Escolha: canário

Recomendo o **canário** para produção, porque a API emite um documento fiscal: um erro de cálculo, mesmo pequeno, é um problema para o cliente, e o canário limita quem é exposto enquanto se observa o comportamento real.

- **Mecanismo:** o ECS tem estratégias **blue/green, canário e linear embutidas**, com ou sem CodeDeploy. O tráfego é dividido por pesos de *target groups* no load balancer, e o rollback é acionado pelo *deployment circuit breaker* e por **alarmes do CloudWatch**.
- **Onde o tráfego é dividido:** no ALB, **atrás** do API Gateway. O gateway não percebe a troca.
- **Parâmetros sugeridos:** 10% do tráfego por 10 minutos (tempo de observação), depois 100%. É um ponto de partida; o tamanho da fatia e o tempo de espera devem ser calibrados com o volume real, porque com pouco tráfego 10 minutos podem não trazer nenhuma requisição.
- **Custo:** durante o deploy, roda-se **o dobro de tarefas**, temporariamente.
- **Rolling com circuit breaker** fica como alternativa mais barata e mais simples para o ambiente de staging.

### Desligamento ordenado (graceful shutdown)

Quando uma tarefa é substituída, as requisições em andamento precisam terminar.

- No Spring Boot 4.1.2 o `server.shutdown` já é **`graceful`** por padrão, e o tempo de espera por fase (`spring.lifecycle.timeout-per-shutdown-phase`) é **30 s**. Conferi isso nos metadados do jar do projeto, então **não há configuração a acrescentar**.
- Uma emissão leva cerca de meio segundo (a integração mais lenta tem 400 ms), muito abaixo de 30 s.
- O ponto a alinhar na infraestrutura: o **`stopTimeout` do ECS** (padrão de 30 s) deve ficar **um pouco acima** do tempo do Spring, por exemplo 45 s, senão o ECS mata o processo antes de ele terminar. O *deregistration delay* do ALB deve ser da mesma ordem. Esses números vêm de um guia da comunidade AWS e devem ser conferidos na documentação oficial antes de aplicar.

## Banco de dados e migrações

As migrações são **Flyway** e rodam na inicialização da aplicação. Isso exige cuidado, porque no canário a versão antiga continua rodando com o **esquema novo**.

- **Expandir e contrair (expand/contract).** Toda migração deve ser compatível com a versão anterior da aplicação: adicionar coluna nula ou com valor padrão, criar tabela, criar índice. **Nunca**, na mesma versão, remover ou renomear algo que a versão antiga ainda usa. A remoção vem numa versão posterior, depois que a antiga saiu de circulação.
- **Sem migração de volta (down).** O rollback é da **aplicação**; o esquema não volta. Se uma migração errada for para produção, a correção é uma nova migração (*forward fix*).
- **Antes de uma migração arriscada:** snapshot do RDS. A restauração por ponto no tempo é o último recurso e perde o que foi gravado depois.
- **A numeração não volta.** Um rollback da aplicação não devolve números da *sequence*: pode haver lacunas, como já está documentado. A unicidade continua garantida.
- **Duas tarefas iniciando juntas:** o Flyway usa um bloqueio no próprio banco, então só uma aplica a migração e as outras esperam.

## Feature toggles

O **AppConfig** permite ligar e desligar comportamento sem novo deploy, com liberação gradual.

- **Hoje a aplicação não tem nenhum toggle.** O do frete foi removido de propósito.
- **Quando usar:** para comportamento novo e arriscado. O exemplo natural é trocar a notificação síncrona pelo **outbox** (veja a arquitetura-alvo): entra desligado, é ligado para uma parte do tráfego e, se algo der errado, **desliga-se o toggle** sem rollback de versão.
- **Regras de higiene:** cada toggle tem dono, data de remoção e um critério de "pode apagar". Depois de totalmente liberado e estável, o toggle e o caminho antigo saem do código. Foi exatamente a lição do modo de frete: um toggle que sobra vira dívida técnica.
- **Kill switch:** um toggle que desliga uma integração problemática (por exemplo, parar de notificar um sistema que está fora do ar) sem derrubar a emissão.
- **O que não vira toggle:** regra tributária. Mudar uma alíquota é mudança de código revisada e testada, nunca um interruptor.

## Checklist de mudança

Estilo de um registro de mudança (por exemplo, no ServiceNow). Cada mudança tem os campos abaixo e passa pelas fases do checklist.

### Campos do registro

| Campo | O que preencher |
|---|---|
| **Título** | Resumo curto: o que muda e em qual versão |
| **Tipo** | Padrão (rotina, já aprovada), Normal (precisa de aprovação) ou Emergencial (hotfix) |
| **Risco e impacto** | Baixo, médio ou alto, e quem pode ser afetado |
| **Janela** | Data, horário e duração prevista |
| **Responsável e substituto** | Quem executa e quem decide o rollback |
| **Descrição e justificativa** | O que muda e por quê |
| **Versão** | Hash do commit e tag da imagem |
| **Dependências** | Migrações, toggles, outros sistemas afetados |
| **Plano de implantação** | Os passos (abaixo) |
| **Plano de validação** | Os sinais e testes (abaixo) |
| **Plano de rollback** | Os critérios e passos (abaixo) |
| **Comunicação** | Quem é avisado antes, durante e depois |
| **Evidências** | Links do CI, do deploy e dos painéis |

### Fases

**1. Preparação**

- [ ] O CI do PR está verde (lint, testes e cobertura de pelo menos 90%).
- [ ] A imagem foi construída com a tag do commit e publicada no ECR.
- [ ] A mesma imagem passou pelo staging, com o smoke test e os testes de regressão verdes.
- [ ] Migrações revisadas: compatíveis com a versão anterior, sem remoção nem renome (veja [Banco de dados e migrações](#banco-de-dados-e-migrações)).
- [ ] Snapshot do banco feito, se houver migração arriscada.
- [ ] Toggles necessários criados e **desligados**.
- [ ] Plano de rollback escrito e responsáveis definidos.
- [ ] Janela escolhida, com baixa movimentação, e partes interessadas avisadas.
- [ ] Alarmes e painéis conferidos e funcionando.
- [ ] Registro de mudança aprovado.

**2. Implantação**

- [ ] Anotar a versão que está no ar (`/actuator/info`), para saber para onde voltar.
- [ ] Iniciar o deploy canário (10% do tráfego).
- [ ] Acompanhar a subida das novas tarefas: o *readiness* deve ficar saudável.
- [ ] Confirmar que a migração foi aplicada, se houver.
- [ ] Esperar o tempo de observação do canário.

**3. Validação**

- [ ] `/actuator/health` e `/actuator/health/readiness` em `UP` nas tarefas novas.
- [ ] `/actuator/info` mostra a versão esperada nas tarefas novas.
- [ ] Taxa de erros 5xx e `ERRO_INTERNO` dentro do normal (veja [Validação pós-deploy](#validação-pós-deploy)).
- [ ] Latência do `POST /notas-fiscais` próxima da linha de base.
- [ ] Notas sendo emitidas no canário (a métrica `notafiscal_emitidas` sobe).
- [ ] Sem aumento de falhas de notificação nem de recusas inesperadas.

**4. Testes regressivos**

- [ ] **Em staging, antes:** a suíte de sintomas do legado e os testes automatizados rodam no CI; o smoke test emite uma nota completa, confere o total e repete o pedido para ver o 409.
- [ ] **Em produção, depois:** conferir, nas notas reais do canário, que o total fecha com a soma das partes e que a numeração segue sequencial.
- [ ] Caso o comportamento tributário tenha mudado, comparar o resultado com os casos de limite de faixa.

**5. Critérios de rollback**

- [ ] Os limiares da [tabela de critérios](#critérios-de-rollback) estão definidos e os alarmes ligados.
- [ ] Quem decide o rollback manual e como acioná-lo está claro.

**6. Pós-implantação**

- [ ] Promover para 100% e monitorar durante a janela combinada (sugestão: 30 a 60 minutos).
- [ ] Confirmar que as tarefas antigas foram encerradas.
- [ ] Ligar o toggle, se for o caso, de forma gradual.

**7. Encerramento**

- [ ] Fechar o registro de mudança com as evidências.
- [ ] Avisar as partes interessadas.
- [ ] Se houve rollback ou incidente, abrir a análise do ocorrido.
- [ ] Remover toggles que já cumpriram o papel.

### Exemplo preenchido (ilustrativo)

| Campo | Exemplo |
|---|---|
| **Título** | Publicar a versão que adiciona o outbox (toggle desligado) |
| **Tipo** | Normal |
| **Risco** | Médio (inclui migração de esquema) |
| **Janela** | Terça, 14h, 30 minutos |
| **Versão** | Commit `a1b2c3d`, imagem com a mesma tag |
| **Dependências** | Migração `V2`: nova tabela de outbox (compatível com a versão anterior); toggle `outbox-habilitado` criado e desligado |
| **Rollback** | Voltar o tráfego para a versão anterior (a migração é compatível, a tabela nova só fica sem uso) |
| **Validação** | Critérios da tabela abaixo; o toggle só é ligado depois de 24 h estáveis |

Este exemplo é ilustrativo: o outbox **não está implementado**.

## Validação pós-deploy

O que a aplicação já expõe e pode ser usado para decidir se a versão nova está boa:

| Sinal | Onde olhar | Estado |
|---|---|---|
| Saúde da tarefa | `/actuator/health/readiness` e `/liveness` | Implementado |
| Versão no ar | `/actuator/info` | Implementado, mas hoje mostra `0.0.1-SNAPSHOT` (veja [Versionamento](#versionamento-e-rastreabilidade)) |
| Taxa de 5xx e latência por endpoint | `http_server_requests_seconds` | Implementado (métrica); alarme é alvo |
| Erros inesperados | `notafiscal_recusadas_total{codigo="ERRO_INTERNO"}` | Implementado (métrica); alarme é alvo |
| Notas emitidas | `notafiscal_emitidas_total` | Implementado (métrica) |
| Falhas de notificação por sistema | `notafiscal_notificacoes_falhas_total` | Implementado (métrica) |
| Conexões esperando no banco | `hikaricp_connections_pending` | Implementado (métrica) |
| Mensagens paradas nas DLQs | Profundidade das DLQs | Alvo, depende do outbox |
| Logs da requisição | Correlation id nos logs | Implementado |

**Smoke test em produção: cuidado.** Um `POST /notas-fiscais` de teste em produção **emitiria uma nota fiscal de verdade** e consumiria um número da sequência. Por isso, o smoke test completo (que emite, confere o total e repete o pedido) roda **em staging**. Em produção o canário se valida por **saúde, versão, métricas e pelas notas reais** que ele atende. Se uma transação sintética em produção for necessária, ela precisa de um cliente de teste identificado e de uma decisão de negócio, o que está **fora do escopo** deste plano.

## Critérios de rollback

Valores **sugeridos**, a calibrar com a linha de base. Os primeiros devem ser **automáticos**, ligados a alarmes do CloudWatch.

| Gatilho | Limiar sugerido | Ação |
|---|---|---|
| Tarefas novas não ficam saudáveis | Não atingem *readiness* na janela de subida | Rollback automático (circuit breaker) |
| Taxa de 5xx | Acima de 1% por 2 minutos seguidos no canário | Rollback automático |
| `ERRO_INTERNO` | Qualquer ocorrência sustentada no canário | Rollback automático |
| Latência média do `POST /notas-fiscais` | Mais que o dobro da linha de base por 5 minutos | Rollback automático |
| Conexões pendentes no pool | Acima de zero de forma sustentada | Investigar; rollback se persistir |
| Falhas de notificação | Aumento claro por sistema, sem causa externa | Investigar; considerar o kill switch |
| Resultado fiscal errado | Total ou tributo divergente do esperado em nota real | **Rollback manual imediato** |
| Inconsistência de dado | Notas duplicadas, sem itens ou sem frete | **Rollback manual imediato** |
| Falha de segurança | Vulnerabilidade ou exposição | **Rollback manual imediato** |

A API **não publica o percentil p99** (só contagem, soma e máximo), então os critérios de latência usam **média** e **máximo**. Habilitar o histograma de latência é uma mudança de configuração que permitiria usar o p99 depois.

Um canário com **pouco tráfego** pode não acusar problemas raros. Se a fatia receber poucas requisições na janela, vale **estender** a observação em vez de promover só pelo relógio.

## Como fazer o rollback

Do mais simples para o mais invasivo:

1. **Desligar o toggle.** Se o comportamento novo está atrás de um toggle, desligá-lo resolve em segundos, sem tocar na versão.
2. **Cancelar o canário.** O tráfego volta 100% para a versão anterior, que ainda está rodando. É o caminho normal e leva minutos.
3. **Reimplantar a versão anterior.** Se a promoção já aconteceu, sobe-se de novo a imagem com a tag anterior, pelo mesmo pipeline.
4. **Banco: correção para frente.** O esquema não volta. Como as migrações são compatíveis com a versão anterior, o rollback da aplicação funciona sem desfazer a migração. Se a migração em si estiver errada, corrige-se com uma nova migração.
5. **Restauração do banco (último recurso).** Restauração por ponto no tempo a partir do snapshot. Perde dado gravado depois dele e exige decisão explícita.
6. **Eventos.** Com o outbox, mensagens que falharam estão nas DLQs e podem ser reprocessadas; os consumidores são idempotentes.

Depois de qualquer rollback: avisar as partes interessadas, registrar o que aconteceu no registro de mudança e abrir uma análise do ocorrido. A causa é corrigida em um novo PR, que passa de novo por todo o pipeline.

## Hotfix

Uma correção urgente para um problema em produção.

1. **Quando é hotfix:** impacto fiscal ou financeiro, indisponibilidade, falha de segurança. O que pode esperar o ciclo normal **não** é hotfix.
2. **Primeiro, estancar:** se existir, desligar o toggle ou fazer o rollback para a versão anterior. O hotfix **não substitui** o rollback.
3. **Branch** a partir da versão que está em produção (a tag do commit no ar), com a **menor mudança possível** e **um teste que reproduz o problema**.
4. **PR e CI completos.** O hotfix passa pelo mesmo `build` (lint, testes e cobertura). **Não há atalho** para o CI nem bypass da proteção de branch.
5. **Mudança emergencial:** registro do tipo Emergencial, com aprovação acelerada (e formalização depois, se a política permitir).
6. **Deploy pelo mesmo pipeline.** Pode usar uma janela de observação mais curta no canário, mas **continua sendo canário** com os mesmos alarmes. Em último caso, e só para estancar um dano em curso, usa-se o rolling com circuit breaker.
7. **Depois:** garantir que a correção também está na `master` (se a branch saiu de uma tag antiga), e abrir a análise do ocorrido.

## Versionamento e rastreabilidade

- **Tag da imagem = hash do commit.** Dá para saber exatamente que código está no ar e voltar a ele.
- **`/actuator/info` hoje mostra `0.0.1-SNAPSHOT`**, porque a versão do Maven nunca é alterada. Para a rastreabilidade funcionar, o build deveria injetar o hash do commit (por exemplo, nas informações do build), de modo que o `/actuator/info` o mostrasse. **Isso não está implementado.**
- **Tags no Git** para cada versão publicada e um registro de mudanças curto (o que entrou, o que mudou no esquema).
- **Correlation id** liga uma requisição aos logs de todas as tarefas, o que ajuda a comparar a versão nova com a antiga durante o canário.

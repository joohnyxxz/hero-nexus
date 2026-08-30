# Hero Nexus — Guia de Setup

Este projeto foi gerado a partir do `Hero_Nexus_Documento_de_Projeto.md`. Ele já vem com toda a
arquitetura, telas e mecânicas do MVP implementadas em Kotlin + XML (sem Jetpack Compose):

- **Cadastro/Login** — Firebase Authentication (e-mail/senha, com suporte a login por nome de usuário).
- **Coleção de personagens** — Comic Vine API (`characters`, filtrado por publisher Marvel) com cache
  local em Room, grid de cards, filtros (todos/desbloqueados/bloqueados/heróis/anti-heróis/vilões),
  busca e ordenação por poder/nome.
- **Ficha do personagem e comparação** — atributos de combate calculados a partir dos dados da Comic
  Vine (ver `PowerCalculator.kt`, já que a API não fornece números de Força/Velocidade/etc.).
- **Time/Deck** — seleção de até 3 personagens, salvos no Firestore.
- **Batalha** — motor próprio (`BattleEngine`/`BattleView`, Canvas puro, sem assets externos) no
  estilo beat 'em up: joystick virtual + botão de ataque, inimigos, caixas/barris destrutíveis, troca
  automática de personagem ao morrer, chefe (vilão) ao final da fase.
- **Progressão** — XP, moedas, nível, baús, missões e skins cosméticas (que não alteram atributos).

## 1. Antes de compilar: configurar o Firebase (obrigatório)

O projeto foi montado para usar **Firebase Authentication + Cloud Firestore** como "banco de dados da
aplicação" (seção 5 do documento). Sem isso, o Gradle **não vai sincronizar**, porque o plugin
`com.google.gms.google-services` exige um arquivo `google-services.json` dentro de `app/`.

Passo a passo:

1. Acesse [console.firebase.google.com](https://console.firebase.google.com) e crie um projeto novo
   (pode ser gratuito, plano Spark).
2. Dentro do projeto, clique em **Adicionar app → Android**.
3. No campo *nome do pacote Android*, use exatamente: `com.app.hero_nexus`
4. Baixe o arquivo `google-services.json` gerado e coloque-o em:
   `hero-nexus/app/google-services.json` (na raiz do módulo `app`, ao lado de `build.gradle.kts`).
5. No menu lateral do Firebase, vá em **Build → Authentication → Sign-in method** e ative o provedor
   **E-mail/senha**.
6. Vá em **Build → Firestore Database → Criar banco de dados**. Pode começar em modo de teste para
   desenvolver; antes de publicar, troque para regras como as sugeridas abaixo.
7. Abra o projeto no Android Studio, deixe o Gradle sincronizar e rode o app.

### Regras de segurança sugeridas para o Firestore

Cada usuário só deve ler/escrever o próprio progresso:

```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /users/{userId}/{document=**} {
      allow read, write: if request.auth != null && request.auth.uid == userId;
    }
    // Necessário para permitir login por nome de usuário (seção 6.2).
    match /usernames/{username} {
      allow read: if true;
      allow create: if request.auth != null;
    }
    // Catálogo global de missões (seção 22) — não é por usuário, o app lê e (na primeira vez,
    // se estiver vazio) semeia sozinho com os valores padrão.
    match /missions_catalog/{missionId} {
      allow read: if true;
      allow write: if request.auth != null;
    }
  }
}
```

> Se você já publicou as regras acima (ou saiu do "modo de teste") antes desta atualização, **adicione o
> bloco `missions_catalog` e publique de novo** — sem ele, a tela de Missões vai falhar com
> "permission denied" ao tentar ler/semear o catálogo.

## 2. Comic Vine API

A chave fica em `local.properties`, na raiz do módulo (**não vai para o controle de versão** — já
está no `.gitignore`):

```
comicVineApiKey=SUA_CHAVE_AQUI
```

Ela é lida em `app/build.gradle.kts` e exposta como `BuildConfig.COMIC_VINE_API_KEY`. Se quiser trocar
a chave depois, basta editar essa linha — não precisa mexer em nenhum arquivo `.kt`.

O app usa `filter=publisher:31` (ID da Marvel na Comic Vine) e busca até ~200 personagens mais
populares (por `count_of_issue_appearances`), guardando tudo em cache local (Room) para não estourar
o limite de requisições da API (seção 26 do documento).

## 3. O que ainda é "MVP" / próximos passos sugeridos

- **Arte**: a batalha desenha os personagens como círculos coloridos com o nome (sem sprites/animações).
  Dá pra evoluir trocando `BattleView.onDraw` para desenhar bitmaps/spritesheets.
- **Classificação Herói/Anti-herói/Vilão**: a Comic Vine não expõe esse dado, então
  `CharacterCategorizer.kt` usa uma lista curada dos vilões/anti-heróis Marvel mais conhecidos —
  vale revisar e expandir essa lista conforme os personagens que aparecerem no seu cache.
- **Atributos de combate**: gerados de forma determinística por `PowerCalculator.kt` (seed = id do
  personagem na Comic Vine + bônus de popularidade), já que a API não fornece esses números.
- **Nível do personagem**: hoje é só um contador armazenado no Firestore; ele não altera os atributos
  de combate ainda (o documento também não define essa fórmula) — dá pra plugar depois.
- **Ícone do app / logo**: ainda é o ícone padrão do Android Studio. As ideias de logo estão na seção
  35 do documento de projeto, caso queiram desenhar um ícone customizado depois.

## 4. Estrutura de pastas (pacote `com.app.hero_nexus`)

```
data/
  remote/     -> Retrofit + DTOs da Comic Vine
  local/      -> Room (cache de personagens)
  model/      -> modelos de domínio (Character, UserProfile, Mission, Chest, Skin, BattleResult...)
  repository/ -> CharacterRepository (API+cache) e UserRepository (Firebase Auth+Firestore)
ui/
  auth/       -> Login / Cadastro
  collection/ -> Coleção (grid, filtros, busca)
  detail/     -> Ficha do personagem
  compare/    -> Comparação entre 2 personagens
  team/       -> Seleção de time/deck
  battle/     -> Motor de batalha (Fighter, BattleEngine, BattleView, BattleActivity)
  result/     -> Tela de vitória/derrota
  missions/   -> Missões
  chests/     -> Baús
  store/      -> Loja de skins
  common/     -> MainNavActivity (barra superior + navegação inferior compartilhadas)
util/         -> Constants, PowerCalculator, Resource, Extensions
```

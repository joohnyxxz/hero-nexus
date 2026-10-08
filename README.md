# 🦸‍♂️ Hero Nexus

![Android](https://img.shields.io/badge/Platform-Android-3DDC84?style=flat&logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Language-Kotlin-7F52FF?style=flat&logo=kotlin&logoColor=white)
![Architecture](https://img.shields.io/badge/Architecture-MVVM-blue?style=flat)
![API](https://img.shields.io/badge/API-Comic_Vine-red?style=flat)

**Hero Nexus** é um aplicativo Android focado no universo de heróis (focado na Marvel/Quadrinhos). O aplicativo permite aos usuários explorar um vasto catálogo de personagens, montar sua equipe dos sonhos com 3 heróis e levá-los para uma **Batalha 2D no estilo Beat 'em up**.

Colete cartas, abra baús, complete missões, desbloqueie skins e prove que sua equipe é a mais forte do Nexus!

---

## ✨ Principais Funcionalidades

*   🗂️ **Coleção de Heróis:** Explore informações detalhadas dos personagens usando dados reais consumidos da API do *Comic Vine*.
*   ⚔️ **Motor de Batalha Personalizado (Beat 'em up):** Um campo de batalha interativo (`BattleView` e `BattleEngine`) onde seus 3 personagens escolhidos enfrentam inimigos com ataques básicos, especiais e movimentação livre via Joystick na tela.
*   🛡️ **Montagem de Equipe:** Selecione estrategicamente 3 personagens da sua coleção para formar sua *Strike Team*.
*   ⚖️ **Comparador de Heróis:** Compare os atributos de força de diferentes personagens antes de levá-los para a luta.
*   📦 **Sistema de Baús e Recompensas:** Ganhe e abra baús para desbloquear novos personagens, moedas e comida (para recuperar vida).
*   👕 **Loja de Skins:** Personalize seus heróis favoritos com skins alternativas compradas com moeda do jogo.
*   📜 **Missões Diárias:** Cumpra objetivos para ganhar experiência (XP) e evoluir seu perfil.
*   🌍 **Tradução Dinâmica:** Integração com a API *MyMemory* para traduzir descrições dos heróis para o idioma do usuário.
*   🖼️ **Cenários Dinâmicos:** Integração com a API do *Pexels* para gerar fundos de batalha baseados em contexto.

---

## 🛠️ Tecnologias e Arquitetura

O projeto foi construído utilizando as melhores práticas do desenvolvimento Android moderno:

*   **Linguagem:** Kotlin
*   **Arquitetura:** MVVM (Model-View-ViewModel) + Clean Architecture (Repository Pattern).
*   **Interface (UI):** XML Views nativas com Componentes Customizados (ex: `BattleView` com renderização em Canvas para o Beat 'em up).
*   **Comunicação de Rede:** [Retrofit2](https://square.github.io/retrofit/) para requisições REST.
*   **Assincronismo:** Kotlin Coroutines & Flow.

### 🔌 APIs Utilizadas
1.  **[Comic Vine API](https://comicvine.gamespot.com/api/):** Fonte principal de dados, imagens e atributos dos personagens.
2.  **[MyMemory API](https://mymemory.translated.net/doc/spec.php):** Utilizada para tradução automática de textos (inglês para pt-BR).
3.  **[Pexels API](https://www.pexels.com/api/):** Utilizada para buscar imagens de fundo dinâmicas para as telas ou cenários.

---

## 📂 Estrutura do Projeto

A arquitetura do projeto está dividida para facilitar a manutenção e escalabilidade:

```text
com.app.hero_nexus
 ┣ 📂 data              # Camada de Dados
 ┃ ┣ 📂 local           # Room Database, Daos e Entities
 ┃ ┣ 📂 model           # Data Classes (Character, Chest, Team, etc.)
 ┃ ┣ 📂 remote          # Configuração Retrofit e interfaces das APIs
 ┃ ┗ 📂 repository      # Repositórios (Fonte de verdade dos dados)
 ┣ 📂 ui                # Camada de Apresentação (Activities, Adapters, ViewModels)
 ┃ ┣ 📂 auth            # Login e Registro
 ┃ ┣ 📂 battle          # Motor de jogo (BattleActivity, BattleEngine, BattleView)
 ┃ ┣ 📂 collection      # Listagem de cartas
 ┃ ┣ 📂 detail          # Detalhes do personagem
 ┃ ┣ 📂 team            # Seleção do esquadrão
 ┃ ┗ 📂 ...             # (Store, Missions, Chests, etc.)
 ┗ 📂 util              # Constantes, Extensions e Calculadoras de Poder
```

---

## 🚀 Como executar o projeto

### Pré-requisitos
* Android Studio (Versão mais recente recomendada).

### Passo a passo

1. **Clone o repositório:**
   ```bash
   git clone [https://github.com/joohnyxxz/hero-nexus.git](https://github.com/joohnyxxz/hero-nexus.git)
   ```

2. **Abra o projeto** no Android Studio.

3. **Sincronize o Gradle** clicando em *Sync Now*.

4. **Execute o aplicativo** em um emulador ou dispositivo físico clicando em *Run* (Shift + F10).

---

## 🥊 O Motor Beat 'em Up (BattleEngine)

O grande diferencial deste projeto é o `BattleView` e `BattleEngine`. Ao invés de uma batalha em turnos comum, o projeto utiliza uma view customizada do Android para desenhar um loop de jogo em tempo real (60fps).

* **Fighter.kt:** Representa os personagens e inimigos no campo de batalha, controlando estados de animação (Idle, Walk, Punch, Hurt, Death), hitboxes e cooldowns.
* **Inputs:** Um joystick virtual desenhado na tela (`bg_joystick_base.xml`) permite movimentação 2D, aliado a botões de ataque e especial.

---

## 📄 Licença

Este projeto está licenciado sob a licença MIT - veja o arquivo LICENSE para detalhes.

*Nota: Todas as imagens, nomes de personagens e dados do universo Marvel/DC pertencem aos seus respectivos detentores de direitos autorais. Este é um projeto de cunho educacional.*

---
Feito com ⚡ por joohnyxxz

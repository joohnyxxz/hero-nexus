package com.app.hero_nexus.util

/**
 * Constantes globais do Hero Nexus.
 * Valores de jogo (XP, moedas, tamanho do time) seguem o Documento de Projeto.
 */
object Constants {

    // Comic Vine API (seção 4 do documento)
    const val COMIC_VINE_BASE_URL = "https://comicvine.gamespot.com/api/"
    const val COMIC_VINE_USER_AGENT = "HeroNexusApp/1.0 (Android; contato: maldonadojoao326@gmail.com)"

    /** ID do publisher "Marvel" na Comic Vine (comicvine.gamespot.com/marvel/4010-31/). */
    const val MARVEL_PUBLISHER_ID = 31

    /** Quantos personagens buscar por página NA COMIC VINE, em cada requisição individual
     * (máximo permitido pela API é 100). Não é o total exibido de uma vez pro usuário -- ver
     * INITIAL_PAGE_TARGET/LOAD_MORE_BATCH_TARGET abaixo. */
    const val CHARACTERS_PAGE_SIZE = 50

    /** Rodada 15, parte 13 (30/09/2026): listagem "por partes" de novo, agora em cima da base
     * simples que já provou funcionar (sem cursor em SharedPreferences, sem rodapé dentro do
     * RecyclerView -- ver comentário em CharacterRepository.kt e CollectionActivity.kt pros
     * motivos). Quantos personagens Marvel a Coleção tenta ter prontos ANTES de sair da splash
     * / pintar a primeira tela. */
    const val INITIAL_PAGE_TARGET = 20

    /** Quantos personagens Marvel NOVOS uma leva de "carregar mais" (scroll até o fim) busca. */
    const val LOAD_MORE_BATCH_TARGET = 20

    /** Teto de requisições à Comic Vine POR leva (inicial ou "carregar mais") -- o filtro
     * `publisher:` do lado do servidor da própria Comic Vine é furado (bug documentado desde a
     * segunda rodada), então às vezes é preciso varrer mais de uma página pra achar Marvel de
     * verdade; este teto evita ficar preso nisso indefinidamente numa faixa pobre do catálogo. */
    const val MAX_REQUESTS_PER_BATCH = 8

    /** Teto de tempo pra UMA leva (inicial ou "carregar mais") inteira, cabendo até
     * MAX_REQUESTS_PER_BATCH chamadas sequenciais. Rodada 15, parte 14 (30/09/2026): corrigido
     * de 15s pra 25s -- o valor antigo era MENOR que o timeout de uma única chamada (20s, em
     * NetworkModule), então numa rede mais lenta uma chamada sozinha já estourava o teto do
     * conjunto inteiro. Agora tem espaço de verdade pra mais de uma chamada terminar; além
     * disso, CharacterRepository.fetchBatch() não descarta mais o que já foi buscado se o tempo
     * acabar no meio do caminho -- ver comentário lá. */
    const val BATCH_LOAD_TIMEOUT_MILLIS = 25_000L

    /** Teto de tempo extra que a splash espera pela primeira leva antes de navegar mesmo assim
     * (nunca trava o app numa rede ruim/fora do ar -- a Coleção lida com cache vazio/erro do
     * jeito de sempre nesse caso). */
    const val SPLASH_PREFETCH_TIMEOUT_MS = 6000L

    /** Cache local: depois desse tempo os dados podem ser atualizados (seção 26 - evitar chamadas desnecessárias). */
    const val CACHE_TTL_MILLIS = 24L * 60L * 60L * 1000L // 24h

    // Regras de time / deck (seção 11)
    const val MAX_TEAM_SIZE = 3

    // Quantos personagens um jogador novo já recebe desbloqueados (precisa dar pra montar 1 time completo).
    const val STARTER_CHARACTER_COUNT = MAX_TEAM_SIZE

    // XP (seção 19)
    const val XP_PER_ENEMY = 25
    const val XP_PER_BOSS = 500
    const val XP_PER_MISSION = 200

    // Moedas (seção 20)
    const val COINS_PER_ENEMY = 10
    const val COINS_PER_BOSS = 200
    const val COINS_PER_CRATE = 5

    // Progressão de nível: XP necessário para o próximo nível cresce linearmente.
    const val XP_PER_LEVEL = 1000

    // Firestore (seção 5 - banco de dados da aplicação / dados do jogador)
    const val COL_USERS = "users"
    const val COL_USER_CHARACTERS = "characters"
    const val COL_DECK = "deck"
    const val COL_MISSIONS = "missions"
    const val COL_SKINS = "skins"
    const val COL_BATTLE_HISTORY = "battle_history"
    /** Catálogo global de missões (não é por usuário) — fica no banco em vez de mocado no app. */
    const val COL_MISSIONS_CATALOG = "missions_catalog"

    const val ROOM_DB_NAME = "hero_nexus_cache.db"

    // Tradução PT-BR (documento revisado — a Comic Vine só retorna texto em inglês).
    // MyMemory foi escolhido de propósito por NÃO exigir API key: depois do incidente de
    // segurança, evitamos qualquer novo segredo pra guardar/vazar.
    const val TRANSLATION_BASE_URL = "https://api.mymemory.translated.net/"
    const val TRANSLATION_LANG_PAIR = "en|pt-BR"
    /** E-mail de contato (não é segredo — o mesmo já usado no User-Agent da Comic Vine acima);
     *  só serve pra liberar uma cota diária maior no MyMemory. */
    const val TRANSLATION_CONTACT_EMAIL = "maldonadojoao326@gmail.com"
    /** MyMemory limita ~500 bytes por texto enviado numa conta anônima; ficamos com folga. */
    const val TRANSLATION_MAX_CHARS = 450

    /** Tamanho máximo do texto "sobre" quando não há "deck" curto e é preciso cortar a description longa. */
    const val ABOUT_MAX_CHARS = 420

    // Pexels (imagens de fundo temáticas — seção 27 do documento: "outras APIs só quando
    // houver necessidade real"). Opcional: sem chave, o app não busca fundo nenhum.
    const val PEXELS_BASE_URL = "https://api.pexels.com/"
    const val PEXELS_LOGIN_BG_QUERY = "comic book superhero action dramatic"
}

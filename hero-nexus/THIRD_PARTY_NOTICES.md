# Avisos de terceiros

## Heroicons

Os icones em `app/src/main/res/drawable/ic_back.xml`, `ic_character_placeholder.xml`,
`ic_chest.xml`, `ic_coin.xml`, `ic_food.xml`, `ic_lock.xml`, `ic_logout.xml`,
`ic_nav_chest.xml`, `ic_nav_collection.xml`, `ic_nav_missions.xml`, `ic_nav_store.xml`,
`ic_nav_team.xml`, `ic_search.xml`, `ic_sort.xml`, `ic_special.xml`, `ic_star.xml` e
`ic_xp.xml` foram convertidos a partir dos arquivos SVG do estilo "Outline" (24x24, contorno/linha) da
biblioteca Heroicons (https://heroicons.com / https://github.com/tailwindlabs/heroicons,
mantida pela equipe do Tailwind CSS), trocados em 29/09/2026: primeiro no lugar dos ícones da Phosphor Icons de uma rodada anterior
(o usuário achou o resultado da Phosphor não "clean" o suficiente), e na mesma rodada, do estilo
"Solid" (preenchido) da própria Heroicons pro estilo "Outline" (contorno), a pedido explícito do
usuário pra padronizar tudo num único estilo mais limpo.

Licenca: MIT

```
MIT License

Copyright (c) Tailwind Labs, Inc.

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

Nota: `ic_sword.xml` (icone de poder/ataque nos cards) continua sendo um desenho
proprio, nao faz parte da Heroicons.

## Inter

A fonte em `app/src/main/res/font/inter_regular.ttf`, `inter_medium.ttf`, `inter_semibold.ttf`
e `inter_bold.ttf` (agrupadas em `app/src/main/res/font/inter.xml`) e' a Inter
(https://rsms.me/inter/ / https://github.com/rsms/inter), do The Inter Project Authors.
Adicionada em 29/09/2026 a pedido do usuario, como fonte padrao de leitura do app (o app antes
usava o Roboto do Material3 por padrao, nunca escolhido de proposito - ver comentario na rodada
13 em `themes.xml`). Nao substitui a Bebas Neue: ela continua reservada pros pontos de "impacto"
ja estabelecidos (titulo de tela, wordmark, cabecalho do card de personagem, tela de resultado de
batalha, flash de miniboss).

Licenca: SIL Open Font License, Version 1.1

```
Copyright (c) 2016 The Inter Project Authors (https://github.com/rsms/inter)

This Font Software is licensed under the SIL Open Font License, Version 1.1.
This license is copied below, and is also available with a FAQ at:
http://scripts.sil.org/OFL

-----------------------------------------------------------
SIL OPEN FONT LICENSE Version 1.1 - 26 February 2007
-----------------------------------------------------------

PREAMBLE
The goals of the Open Font License (OFL) are to stimulate worldwide
development of collaborative font projects, to support the font creation
efforts of academic and linguistic communities, and to provide a free and
open framework in which fonts may be shared and improved in partnership
with others.

The OFL allows the licensed fonts to be used, studied, modified and
redistributed freely as long as they are not sold by themselves. The
fonts, including any derivative works, can be bundled, embedded,
redistributed and/or sold with any software provided that any reserved
names are not used by derivative works. The fonts and derivatives,
however, cannot be released under any other type of license. The
requirement for fonts to remain under this license does not apply
to any document created using the fonts or their derivatives.

DEFINITIONS
"Font Software" refers to the set of files released by the Copyright
Holder(s) under this license and clearly marked as such. This may
include source files, build scripts and documentation.

"Reserved Font Name" refers to any names specified as such after the
copyright statement(s).

"Original Version" refers to the collection of Font Software components as
distributed by the Copyright Holder(s).

Modified Version refers to any derivative made by adding to, deleting,
or substituting -- in part or in whole -- any of the components of the
Original Version, by changing formats or by porting the Font Software to a
new environment.

"Author" refers to any designer, engineer, programmer, technical
writer or other person who contributed to the Font Software.

PERMISSION & CONDITIONS
Permission is hereby granted, free of charge, to any person obtaining
a copy of the Font Software, to use, study, copy, merge, embed, modify,
redistribute, and sell modified and unmodified copies of the Font
Software, subject to the following conditions:

1) Neither the Font Software nor any of its individual components,
in Original or Modified Versions, may be sold by itself.

2) Original or Modified Versions of the Font Software may be bundled,
redistributed and/or sold with any software, provided that each copy
contains the above copyright notice and this license. These can be
included either as stand-alone text files, human-readable headers or
in the appropriate machine-readable metadata fields within text or
binary files as long as those fields can be easily viewed by the user.

3) No Modified Version of the Font Software may use the Reserved Font
Name(s) unless explicit written permission is granted by the corresponding
Copyright Holder. This restriction only applies to the primary font name as
presented to the users.

4) The name(s) of the Copyright Holder(s) or the Author(s) of the Font
Software shall not be used to promote, endorse or advertise any
Modified Version, except to acknowledge the contribution(s) of the
Copyright Holder(s) and the Author(s) or with their explicit written
permission.

5) The Font Software, modified or unmodified, in part or in whole,
must be distributed entirely under this license, and must not be
distributed under any other license. The requirement for fonts to
remain under this license does not apply to any document created
using the Font Software.

TERMINATION
This license becomes null and void if any of the above conditions are
not met.

DISCLAIMER
THE FONT SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO ANY WARRANTIES OF
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT
OF COPYRIGHT, PATENT, TRADEMARK, OR OTHER RIGHT. IN NO EVENT SHALL THE
COPYRIGHT HOLDER BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
INCLUDING ANY GENERAL, SPECIAL, INDIRECT, INCIDENTAL, OR CONSEQUENTIAL
DAMAGES, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
FROM, OUT OF THE USE OR INABILITY TO USE THE FONT SOFTWARE OR FROM
OTHER DEALINGS IN THE FONT SOFTWARE.
```

Nota: a Bebas Neue (`app/src/main/res/font/bebas_neue.otf`, usada nos pontos de "impacto"
citados acima) ja estava no projeto antes deste avisos de terceiros existir e nunca tinha
entrado aqui - nao mexido agora pra nao fugir do escopo desta rodada, mas fica registrado
como pendencia de documentacao.
## Cenario de batalha: "Free Pixel Art Street 2D Backgrounds" (CraftPix)

Rodada 15, parte 18 (30/09/2026): 4 imagens de rua (City1-4, paleta "Bright") usadas como fundo
das arenas de batalha, em `app/src/main/res/drawable-nodpi/img_battle_scenery_city1.png` a
`img_battle_scenery_city4.png` (recorte "Bright" do pack original, sem modificacoes).

Fonte: https://craftpix.net/file-licenses/ (licenca gratuita padrao do site). Termos conferidos:
atribuicao NAO obrigatoria ("No attribution or link back to this site is required, however any
credit will be highly appreciated"); uso comercial e pessoal permitido, incluindo vender/distribuir
jogos com os assets; proibido revender os arquivos de arte originais (PNG/JPG/etc.) ou uma versao
pouco modificada deles como produto separado, ou redistribui-los de um jeito que outro usuario final
consiga extrai-los pelo app -- nenhuma dessas duas restricoes se aplica ao uso feito aqui (assets
embutidos no APK como parte do jogo).

Substituem o pack anterior (Kenney "Background Elements", CC0, rodada 7-10) -- os 6 arquivos antigos
(`img_battle_scenery_sky/ground.png`, `_temple_sky/ground.png`, `_forest_sky/ground.png`) ficaram
orfaos no projeto (sem nenhuma referencia em codigo, confirmado por grep), nao apagados do disco.

Rodada 15, parte 19 (30/09/2026): mais 9 recortes do MESMO pack (mesma licenca acima, mesmos termos),
desta vez das camadas individuais (nao a cena composta) de cada cidade -- usados como sprites de
objeto, nao mais fundo:

- `img_battle_prop_crate.png` (recorte de `City1/Bright/boxes&container.png`): corpo da caixa
  destrutivel (`DestructibleObject.Kind.CAIXA`), no lugar da forma geometrica desenhada a mao.
- `img_battle_prop_barrel.png` (recorte de `City1/Bright/wheels&hydrant.png`, a pilha de pneus):
  corpo do barril destrutivel (`DestructibleObject.Kind.BARRIL`) -- o pack nao tem um barril de
  madeira de verdade nas cenas baixadas, a pilha de pneus faz as vezes (le como objeto cilindrico
  destrutivel igual um barril leria).
- `img_battle_deco_hydrant.png`, `img_battle_deco_dumpster.png` (`City1/Bright/...`),
  `img_battle_deco_kiosk.png`, `img_battle_deco_callbox.png` (`City2/Bright/minishop&callbox.png`),
  `img_battle_deco_cafe_table.png`, `img_battle_deco_policebox.png` (`City4/Bright/umbrella&policebox.png`),
  `img_battle_deco_fountain.png` (`City4/Bright/fountain&bush.png`): decoracao fixa e sem colisao,
  espalhada pelo mundo inteiro (`BattleEngine.sceneryProps`) so pra dar variedade visual ao fundo
  em loop -- pedido do usuario ("deixar o cenario menos possivel infinito... pra nao ficar
  cansativo jogar").

Todos recortados da camada original (transparencia preservada, sem redesenhar/modificar a arte em
si) via PIL, cortando so a bounding box de cada objeto dentro da camada de 1920x1080.

## Cenario de batalha: "Pixel Art Battlegrounds" + "Postapocalypse Backgrounds" (CraftPix)

Rodada 15, parte 32 (02/10/2026): mais 8 imagens de fundo (paleta "Bright", mesmo criterio ja usado
pro pack de ruas), somadas como novas variantes de cenario de batalha (`SCENERY_VARIANT_COUNT`
subiu de 4 pra 12 em `BattleEngine.kt`):

- `img_battle_scenery_ruins1.png` (`Battleground1/Bright/Battleground1.png`): ruinas antigas com
  estatua, campo de grama.
- `img_battle_scenery_throne2.png` (`Battleground2/Bright/Battleground2.png`): salao do trono com
  estatua de dragao, piso de pedra.
- `img_battle_scenery_jungle3.png` (`Battleground3/Bright/Battleground3.png`): selva com arvore de
  rosto esculpido, trilha de terra.
- `img_battle_scenery_crypt4.png` (`Battleground4/Bright/Battleground4.png`): cripta/cemiterio,
  piso de pedra com ossos.
- `img_battle_scenery_apoc1.png` a `img_battle_scenery_apoc4.png`
  (`Postapocalypce1-4/Bright/postapocalypseN.png`): 4 cenas pos-apocalipticas (casas destruidas,
  cidade tomada por vegetacao, deserto, estacao de trem abandonada).

Mesma fonte/licenca do pack de ruas acima: https://craftpix.net/file-licenses/ -- atribuicao NAO
obrigatoria, uso comercial permitido, so nao pode revender os arquivos de arte originais como
produto separado (nao se aplica aqui). Imagens usadas sem modificacao (recorte "Bright" direto do
pack original).

Nota de direcao visual: diferente do pack de ruas (escolhido especificamente por combinar com a
estetica urbana Marvel, rodada 10/18), esses 2 packs novos sao de genero fantasia/pos-apocaliptico
generico (sem nenhuma referencia Marvel) -- registrado aqui pra o usuario ter essa informacao, caso
queira revisar a escolha depois.

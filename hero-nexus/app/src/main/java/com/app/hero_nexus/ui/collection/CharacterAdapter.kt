package com.app.hero_nexus.ui.collection

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import com.google.android.material.card.MaterialCardView
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.app.hero_nexus.R
import com.app.hero_nexus.data.model.Character
import com.app.hero_nexus.data.model.Rarity
import com.app.hero_nexus.databinding.ItemCharacterCardBinding
import com.app.hero_nexus.util.loadCharacterImage
import com.app.hero_nexus.util.visibleIf

class CharacterAdapter(
    private val onClick: (Character) -> Unit
) : ListAdapter<Character, CharacterAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemCharacterCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    inner class VH(private val binding: ItemCharacterCardBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(character: Character) {
            val ctx = binding.root.context
            binding.textName.text = character.name
            binding.imageCharacter.loadCharacterImage(character.imageUrl)
            binding.imageLock.visibleIf(!character.unlocked)
            binding.textLevel.visibleIf(character.unlocked)
            // Rodada 15, parte 61 (07/10/2026): troquei visibleIf (GONE) por INVISIBLE aqui --
            // some da tela mas continua ocupando o mesmo espaço, senão um card bloqueado ficava
            // mais baixo que um desbloqueado na mesma linha da grade (a barra de XP só existe
            // num dos dois), desalinhando a grade inteira verticalmente.
            binding.progressXp.visibility = if (character.unlocked) android.view.View.VISIBLE else android.view.View.INVISIBLE
            // Mesma lógica pro ícone de espada do chip de poder: não faz sentido mostrar um
            // ícone de "poder de combate" junto de um valor que não existe ainda.
            binding.iconPower.visibility = if (character.unlocked) android.view.View.VISIBLE else android.view.View.INVISIBLE
            binding.viewLockedScrim.visibleIf(!character.unlocked)

            if (character.unlocked) {
                binding.imageCharacter.colorFilter = null
                binding.textLevel.text = ctx.getString(R.string.level_short, character.level)
                binding.textPower.text = character.stats.overallPower.toString()
                binding.textPower.setTextColor(ContextCompat.getColor(ctx, R.color.reward_gold))
                binding.progressXp.progress = (character.xp % 1000) / 10
            } else {
                val grayscale = ColorMatrix().apply { setSaturation(0f) }
                binding.imageCharacter.colorFilter = ColorMatrixColorFilter(grayscale)
                binding.textPower.text = ctx.getString(R.string.locked_hint)
                binding.textPower.setTextColor(ContextCompat.getColor(ctx, R.color.text_muted))
            }

            // Mesmo cuidado do parágrafo abaixo (não entregar a raridade de algo ainda
            // bloqueado): o NOME da raridade também não pode aparecer -- só a cor já seria
            // pouco (cinza por cima de cinza passa fácil), mas o texto escrito "LENDARIO" bem
            // visível no cabeçalho continuaria contando tudo mesmo com a carta "selada".
            binding.textRarity.text = if (character.unlocked) character.rarity.name else "???"

            // Rodada 15, parte 61 (07/10/2026): pedido do usuário -- "a carta cinza se nao tiver
            // desbloqueado". Até aqui, SÓ a foto virava cinza (ColorMatrix acima); moldura,
            // cabeçalho e selo de raridade continuavam na cor cheia da raridade real, entregando
            // de bandeja qual seria a raridade de um personagem ainda bloqueado.
            //
            // Rodada 15, parte 64 (07/10/2026): a parte 61 foi longe demais na direção oposta --
            // usuário pediu em seguida pra "a carta cinza de alguma forma falar a raridade do
            // card" de volta. Em vez de voltar pra cor cheia (que de novo entregaria tudo) ou
            // ficar 100% cinza neutro (que não fala nada), misturo as duas: um personagem
            // bloqueado usa a cor REAL da raridade, só que bem puxada pra cinza (ColorUtils.
            // blendARGB, 32% da cor de verdade / 68% cinza neutro) -- dá pra notar um "LENDARIO"
            // tem um quê de dourado acinzentado contra um "COMUM" cinza puro, mas sem acender a
            // cor plena/óbvia antes de desbloquear de verdade. O texto da raridade continua
            // "???" (ver acima) -- a dica fica só na cor, nunca escrito.
            val trueRarityColor = ContextCompat.getColor(ctx, rarityColorRes(character.rarity))
            val rarityColor = if (character.unlocked) {
                trueRarityColor
            } else {
                ColorUtils.blendARGB(
                    ContextCompat.getColor(ctx, R.color.rarity_locked),
                    trueRarityColor,
                    LOCKED_RARITY_HINT_RATIO
                )
            }

            // Rodada 12 (01/09): a raridade e o nivel viraram uma unica faixa colorida no topo
            // do card (bg_card_header, tintada com a cor da raridade) em vez de dois chips soltos
            // nos cantos -- texto escuro fixo (bg_deep_blue, ja definido no XML) pra ficar legivel
            // em cima de qualquer uma das 4 cores de raridade.
            binding.headerBar.backgroundTintList = android.content.res.ColorStateList.valueOf(rarityColor)

            // Rodada 10 (01/09): antes a borda do card era sempre a mesma cor neutra
            // (card_stroke) pra todo mundo, a raridade so aparecia no textinho pequeno no
            // canto. Agora a moldura inteira do card usa a cor da raridade, o que da muito
            // mais variedade visual pra grade inteira (feedback: "a visualizacao dos card
            // poderia ser melhor").
            val card = binding.root as MaterialCardView
            card.strokeColor = rarityColor

            // Rodada 15, parte 61 (07/10/2026): pedido do usuário -- "queria que fosse mais uma
            // cartinha de RPG". Moldura mais espessa pra LENDARIO (destaque de "carta rara de
            // verdade" bem na borda, sem precisar abrir o personagem pra notar) -- qualquer
            // outra raridade (ou bloqueado) usa a espessura normal.
            card.strokeWidth = ctx.resources.getDimensionPixelSize(
                if (character.unlocked && character.rarity == Rarity.LENDARIO) {
                    R.dimen.card_stroke_width_legendary
                } else {
                    R.dimen.card_stroke_width_normal
                }
            )

            binding.dividerStats.setBackgroundColor(rarityColor)

            // Selo de raridade (círculo no canto da ilustração, perto do nome) -- mesma cor da
            // moldura/cabeçalho, pra reforçar visualmente de qual raridade é o card sem
            // depender só do texto pequeno em cima.
            // Rodada 15, parte 64 (07/10/2026): o selo também fica visível bloqueado (antes
            // ficava escondido) -- é só mais um lugar onde a cor (já borrada pra cinza acima)
            // reforça a mesma dica, em vez de sumir informação por completo.
            binding.imageRarityGem.backgroundTintList = android.content.res.ColorStateList.valueOf(rarityColor)

            binding.root.setOnClickListener { onClick(character) }
        }

        private fun rarityColorRes(rarity: Rarity): Int = when (rarity) {
            Rarity.COMUM -> R.color.rarity_common
            Rarity.RARO -> R.color.rarity_rare
            Rarity.EPICO -> R.color.rarity_epic
            Rarity.LENDARIO -> R.color.rarity_legendary
        }
    }

    companion object {
        /** Quanto da cor REAL da raridade entra na mistura com cinza pra um card bloqueado
         * (ver comentário em bind() acima) -- baixo de propósito, só uma dica, não a cor cheia. */
        private const val LOCKED_RARITY_HINT_RATIO = 0.32f

        private val DIFF = object : DiffUtil.ItemCallback<Character>() {
            override fun areItemsTheSame(oldItem: Character, newItem: Character) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: Character, newItem: Character) = oldItem == newItem
        }
    }
}

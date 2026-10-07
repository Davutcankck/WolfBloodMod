package com.example;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

import java.util.UUID;

/**
 * Kaos İksiri: Kaos dalının katalizörü (şimdilik test eşyası).
 *
 * Koşullar (hepsi SUNUCUDA kontrol edilir):
 *  - oyuncu enfekte olmalı
 *  - yetenek ağacında "Alfa Kurdu" düğümü açık olmalı
 *  - Kaos dalı daha önce uyandırılmamış olmalı
 *
 * Koşullar sağlanmıyorsa iksir içilmeye başlanmaz ve TÜKETİLMEZ; oyuncuya action bar'da sebep gösterilir.
 * Başarılı olursa InfectionData'ya kaydedilir (kalıcı) ve istemciye senkronlanır.
 */
public class KaosIksiriItem extends Item {

    private static final int ICME_SURESI_TICK = 32; // vanilla iksirle aynı

    public KaosIksiriItem(Properties properties) {
        super(properties);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.DRINK;
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return ICME_SURESI_TICK;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true; // büyülü eşya parıltısı
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!level.isClientSide && player instanceof ServerPlayer sunucuOyuncusu) {
            InfectionData data = InfectionData.get(sunucuOyuncusu.server);
            String sebep = reddetmeSebebi(data, sunucuOyuncusu.getUUID());
            if (sebep != null) {
                sunucuOyuncusu.displayClientMessage(Component.literal(sebep), true);
                return InteractionResultHolder.fail(stack);
            }
        }

        // İstemci tarafı koşulları bilmez; sunucu reddederse eşya envanter senkronuyla geri düzelir.
        return ItemUtils.startUsingInstantly(level, player, hand);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide && entity instanceof ServerPlayer oyuncu) {
            InfectionData data = InfectionData.get(oyuncu.server);
            UUID uuid = oyuncu.getUUID();

            // İçme süresi boyunca durum değişmiş olabilir, tekrar doğrula
            if (reddetmeSebebi(data, uuid) != null) {
                return stack;
            }

            data.setChaosUnlocked(uuid, true);
            WolfProgression.senkronla(oyuncu, data);

            efektOynat(oyuncu);
            oyuncu.sendSystemMessage(Component.literal("Boşluğun fısıltısı damarlarına karıştı... Kaos dalı uyandı."));
            oyuncu.awardStat(Stats.ITEM_USED.get(this));

            if (!oyuncu.getAbilities().instabuild) {
                stack.shrink(1);
            }
        }
        return stack;
    }

    /** İksir içilebilir mi? İçilemiyorsa oyuncuya gösterilecek sebep, içilebiliyorsa null. */
    private static String reddetmeSebebi(InfectionData data, UUID uuid) {
        if (!data.isInfected(uuid)) {
            return "Damarlarında kurt kanı yok; bu iksir seni sadece zehirler.";
        }
        if (data.isChaosUnlocked(uuid)) {
            return "Kaos zaten damarlarında uyanık.";
        }
        if (!WolfSkill.ALFA_KURDU.acikMi(data.getSkillIds(uuid))) {
            return "Alfa Kurdu olmadan bu güce dayanamazsın.";
        }
        return null;
    }

    private static void efektOynat(ServerPlayer oyuncu) {
        ServerLevel dunya = oyuncu.serverLevel();
        dunya.playSound(null, oyuncu.blockPosition(),
                SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 0.6f, 0.8f);
        dunya.sendParticles(ParticleTypes.REVERSE_PORTAL,
                oyuncu.getX(), oyuncu.getY() + 1.0, oyuncu.getZ(),
                80, 0.4, 0.8, 0.4, 0.1);
    }
}
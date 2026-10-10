package com.example;

import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;

import java.util.UUID;

public class WolfItem {


    public static final Item KURT_OTU = new Item(new Item.Properties()
            .food(new FoodProperties.Builder()
                    .nutrition(1)
                    .effect(new MobEffectInstance( MobEffects.MOVEMENT_SPEED,20 * 15,1),1.0f)
                    .saturationMod(0.1f)
                    .alwaysEat()
                    .build()
            ));

    public static final Item KAOS_IKSIRI = new KaosIksiriItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC));




    public static final Item KURT_DISI = new Item(new Item.Properties());

    public  static final Item KURT_KANI = new Item(new Item.Properties().stacksTo(1));

    public static final Item GUMUS = new Item(new Item.Properties());

    public  static  final Item PROBIS = new Item(new Item.Properties()
            .food(new FoodProperties.Builder()
                    .nutrition(3)
                    .effect(new MobEffectInstance(MobEffects.ABSORPTION, 20 * 10, 1),1.0f)
                    .saturationMod(1.0f)
                    .build()
            ));

    public static void register() {
        Registry.register(BuiltInRegistries.ITEM, new ResourceLocation(ExampleMod.MOD_ID, "kurt_otu"), KURT_OTU);
        Registry.register(BuiltInRegistries.ITEM, new ResourceLocation(ExampleMod.MOD_ID, "kurt_disi"), KURT_DISI);
        Registry.register(BuiltInRegistries.ITEM, new ResourceLocation(ExampleMod.MOD_ID, "kurt_kani_iksiri"), KURT_KANI);
        Registry.register(BuiltInRegistries.ITEM, new ResourceLocation(ExampleMod.MOD_ID, "gumus"), GUMUS);
        Registry.register(BuiltInRegistries.ITEM, new ResourceLocation(ExampleMod.MOD_ID, "probis"), PROBIS);
        Registry.register(BuiltInRegistries.ITEM, new ResourceLocation(ExampleMod.MOD_ID, "kaos_iksiri"), KAOS_IKSIRI);

        CreativeModeTab wolfTab = FabricItemGroup.builder()
                .title(Component.translatable("WolfBlood"))
                .icon(() -> new ItemStack(KURT_DISI))
                .displayItems((parameters, output) -> {
                    output.accept(KURT_OTU);
                    output.accept(KURT_DISI);
                    output.accept(KURT_KANI);
                    output.accept(GUMUS);
                    output.accept(PROBIS);
                    output.accept(KAOS_IKSIRI);
                })
                .build();

        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB,
                new ResourceLocation(ExampleMod.MOD_ID, "main"), wolfTab);
    }

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
    public static class KaosIksiriItem extends Item {

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
            return false; // büyülü eşya parıltısı
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
}

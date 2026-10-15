package com.example;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Kurt Adam Yetenek Ağacındaki tüm pasif ve aktif mekanikleri oyuna bağlayan sınıf:
 *  - Canavar öldürüldüğünde Kurt XP kazanımı ve seviye atlama
 *  - Dört dala ait tüm yeteneklerin dövüş, hız, can çalma, sıyrılma ve direnç etkileri
 */
public class WolfSkillPassives {

    // Attribute modifier UUID'leri (WolfTransform UUID'leri ile çakışmaz)
    private static final UUID YETENEK_CAN_ID   = UUID.fromString("b1111111-0000-0000-0000-000000000001");
    private static final UUID YETENEK_HIZ_ID   = UUID.fromString("b2222222-0000-0000-0000-000000000002");
    private static final UUID YETENEK_HASAR_ID = UUID.fromString("b3333333-0000-0000-0000-000000000003");
    private static final UUID YETENEK_ZIRH_ID  = UUID.fromString("b4444444-0000-0000-0000-000000000004");

    // Yetenek bekleme süreleri
    private static final Map<UUID, Long> GOLGE_SIYRILMA_BEKLEME = new HashMap<>();

    public static void register() {
        // ================= CANAVAR ÖLDÜRME: XP & KAN BANYOSU =================
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (damageSource.getEntity() instanceof ServerPlayer killer) {
                onEntityKilledByPlayer(killer, entity);
            }
        });

        // ================= HASAR ALMA / VERME =================
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            // 1. Oyuncu Hasar Alırken (Savunma / Sıyrılma yetenekleri)
            if (entity instanceof ServerPlayer player) {
                if (!onPlayerTakeDamage(player, source, amount)) {
                    return false; // hasarı tamamen iptal et (ör. sıyrılma)
                }
            }

            // 2. Oyuncu Hasar Verirken (Saldırı / Can çalma / Kanama / Kaos)
            if (source.getEntity() instanceof ServerPlayer attacker) {
                onPlayerDealDamage(attacker, entity, amount);
            }

            return true;
        });

        // ================= PERİYODİK ETKİLER (Her saniye) =================
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 20 != 0) return;
            onServerTick(server);
        });
    }

    // ================= XP KAZANIMI & SEVİYE ATLAMA =================

    private static void onEntityKilledByPlayer(ServerPlayer killer, LivingEntity victim) {
        InfectionData data = InfectionData.get(killer.server);
        UUID uuid = killer.getUUID();

        if (!data.isInfected(uuid)) {
            return;
        }

        Set<String> skills = data.getSkillIds(uuid);

        // XP Miktarı
        int xp = 15;
        if (victim instanceof EnderDragon || victim instanceof WitherBoss) {
            xp = 500;
        } else if (victim instanceof Enemy || victim instanceof Monster) {
            xp = 25;
        } else if (victim instanceof ServerPlayer) {
            xp = 80;
        } else {
            xp = 6; // pasif hayvanlar vs.
        }

        // Alfa Kurdu bonusu: +%25 daha fazla XP
        if (WolfSkill.ALFA_KURDU.acikMi(skills)) {
            xp = (int) (xp * 1.25);
        }

        // Dolunayda veya kurt adam formundayken bonus
        if (data.isTransformed(uuid)) {
            xp = (int) (xp * 1.20);
        }

        int eskiSeviye = data.getSeviye(uuid);
        data.xpEkle(uuid, xp);
        int yeniSeviye = data.getSeviye(uuid);

        // Seviye atlandı mı?
        if (yeniSeviye > eskiSeviye) {
            WolfRutbe rutbe = WolfRutbe.seviyeyeGore(yeniSeviye);
            killer.sendSystemMessage(Component.literal("§6§l[KURT KANI] §eSeviye atladın! §f" + rutbe.getAd()
                    + " (Sv." + yeniSeviye + ") §erütbesine ulaştın! (§a+1 Yetenek Puanı§e)"));
            killer.serverLevel().playSound(null, killer.blockPosition(),
                    SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0f, 1.0f);
            uygulaBonuslar(killer, data);
        }

        WolfProgression.senkronla(killer, data);

        // Kan Banyosu Yeteneği (blood_bath): Öldürünce yenilenme ve hız
        if (skills.contains("blood_bath")) {
            killer.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 1, false, true, true));
            killer.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 100, 1, false, true, true));
            killer.serverLevel().sendParticles(ParticleTypes.CRIMSON_SPORE,
                    killer.getX(), killer.getY() + 1.0, killer.getZ(), 20, 0.4, 0.6, 0.4, 0.1);
        }
    }

    // ================= OYUNCU HASAR ALIRKEN =================

    private static boolean onPlayerTakeDamage(ServerPlayer player, DamageSource source, float amount) {
        InfectionData data = InfectionData.get(player.server);
        UUID uuid = player.getUUID();
        if (!data.isInfected(uuid)) return true;

        Set<String> skills = data.getSkillIds(uuid);

        // Hayvani Refleks (beast_reflex): %15 ihtimalle hasardan tamamen sıyrıl
        if (skills.contains("beast_reflex") && source.getEntity() != null) {
            if (player.getRandom().nextFloat() < 0.15f) {
                player.serverLevel().playSound(null, player.blockPosition(),
                        SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0f, 1.8f);
                player.serverLevel().sendParticles(ParticleTypes.POOF,
                        player.getX(), player.getY() + 0.8, player.getZ(), 10, 0.3, 0.4, 0.3, 0.05);
                player.displayClientMessage(Component.literal("§e*Sıyrıldın!*"), true);
                return false; // Hasarı tamamen iptal et!
            }
        }

        // Tüy Düşüşü (feather_fall): Düşme hasarını önle veya hafiflet
        if (skills.contains("feather_fall") && source.is(DamageTypeTags.IS_FALL)) {
            if (amount <= 10.0f) {
                player.serverLevel().sendParticles(ParticleTypes.CLOUD,
                        player.getX(), player.getY() + 0.2, player.getZ(), 8, 0.3, 0.1, 0.3, 0.02);
                return false; // Düşük-orta düşüşlerde hiç hasar alma
            }
        }

        // Gölge Sıyrılması (shadow_dash): Hasar aldığında hız patlaması ve kısa görünmezlik (10sn bekleme)
        if (skills.contains("shadow_dash")) {
            long simdi = player.serverLevel().getGameTime();
            Long son = GOLGE_SIYRILMA_BEKLEME.get(uuid);
            if (son == null || simdi - son > 200) {
                GOLGE_SIYRILMA_BEKLEME.put(uuid, simdi);
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 60, 2, false, false, true));
                player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 40, 0, false, false, true));
                player.serverLevel().sendParticles(ParticleTypes.SMOKE,
                        player.getX(), player.getY() + 1.0, player.getZ(), 15, 0.3, 0.5, 0.3, 0.05);
            }
        }

        return true;
    }

    // ================= OYUNCU SALDIRIRKEN =================

    private static void onPlayerDealDamage(ServerPlayer attacker, LivingEntity victim, float amount) {
        InfectionData data = InfectionData.get(attacker.server);
        UUID uuid = attacker.getUUID();
        if (!data.isInfected(uuid)) return;

        Set<String> skills = data.getSkillIds(uuid);

        // Avcının Ziyafeti (hunters_feast): Verilen hasarın %15'i can olarak döner (Lifesteal)
        if (skills.contains("hunters_feast")) {
            float canCalma = Math.max(0.5f, amount * 0.15f);
            attacker.heal(canCalma);
            attacker.serverLevel().sendParticles(ParticleTypes.HEART,
                    attacker.getX(), attacker.getY() + 1.2, attacker.getZ(), 2, 0.2, 0.2, 0.2, 0.0);
        }

        // Derin Yırtık (deep_rend): Kanama etkisi
        if (skills.contains("deep_rend")) {
            victim.addEffect(new MobEffectInstance(MobEffects.WITHER, 80, 0, false, false, true));
            attacker.serverLevel().sendParticles(ParticleTypes.CRIMSON_SPORE,
                    victim.getX(), victim.getY() + 0.8, victim.getZ(), 8, 0.2, 0.3, 0.2, 0.05);
        }

        // Savaş Kudurusu (battle_frenzy): Vuruş yaptıkça saldırı hızı artar (Acele)
        if (skills.contains("battle_frenzy")) {
            attacker.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 60, 1, false, false, false));
        }

        // Hiçlik Dokunuşu (void_touch): Zırhı yok sayan büyü hasarı
        if (skills.contains("void_touch")) {
            victim.hurt(attacker.damageSources().magic(), amount * 0.25f);
            attacker.serverLevel().sendParticles(ParticleTypes.PORTAL,
                    victim.getX(), victim.getY() + 0.8, victim.getZ(), 6, 0.2, 0.3, 0.2, 0.1);
        }

        // Yırtıcı Dehşet (predator_terror): Düşük canlı hedeflere infaz vuruşu
        if (skills.contains("predator_terror") && victim.getHealth() / victim.getMaxHealth() <= 0.25f) {
            victim.hurt(attacker.damageSources().playerAttack(attacker), amount * 0.5f);
            attacker.serverLevel().playSound(null, victim.blockPosition(),
                    SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0f, 0.8f);
            attacker.serverLevel().sendParticles(ParticleTypes.ENCHANTED_HIT,
                    victim.getX(), victim.getY() + 0.8, victim.getZ(), 15, 0.3, 0.4, 0.3, 0.1);
        }

        // Kaos Patlaması (chaos_burst): Alan hasarı
        if (skills.contains("chaos_burst")) {
            AABB alan = victim.getBoundingBox().inflate(3.5);
            for (LivingEntity cevredekiler : attacker.serverLevel().getEntitiesOfClass(LivingEntity.class, alan)) {
                if (cevredekiler != attacker && cevredekiler != victim
                        && (cevredekiler instanceof Enemy || cevredekiler instanceof Monster)) {
                    cevredekiler.hurt(attacker.damageSources().magic(), 3.5f);
                    attacker.serverLevel().sendParticles(ParticleTypes.WITCH,
                            cevredekiler.getX(), cevredekiler.getY() + 0.6, cevredekiler.getZ(), 5, 0.2, 0.2, 0.2, 0.05);
                }
            }
        }

        // Kaosun Enkarnasyonu (chaos_ultimate): Dönüşmüş formda hedefe körlük/zayıflık
        if (skills.contains("chaos_ultimate") && data.isTransformed(uuid)) {
            victim.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0, false, false, true));
            victim.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 1, false, false, true));
            attacker.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 60, 0, false, false, false));
        }
    }

    // ================= HER SANİYE TETİKLENEN PASİFLER =================

    private static void onServerTick(MinecraftServer server) {
        InfectionData data = InfectionData.get(server);

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID uuid = player.getUUID();
            if (!data.isInfected(uuid)) continue;

            Set<String> skills = data.getSkillIds(uuid);

            // Vahşi Uyanış / Kurt Hissi: Karanlıkta veya geceleri Gece Görüşü
            if (skills.contains("wild_awakening") || skills.contains("wolf_sense")) {
                if (player.serverLevel().isNight() || player.serverLevel().getRawBrightness(player.blockPosition(), 0) < 6) {
                    player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 260, 0, false, false, false));
                }
            }

            // Zincir Kırma (chain_breaker): Yavaşlatma ve zayıflıkları anında temizle
            if (skills.contains("chain_breaker")) {
                player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
                player.removeEffect(MobEffects.WEAKNESS);
                player.removeEffect(MobEffects.DIG_SLOWDOWN);
            }

            // Vahşi Öfke (berserk_rage): Can %50'nin altına indiğinde Güç etkisi
            if (skills.contains("berserk_rage")) {
                if (player.getHealth() <= player.getMaxHealth() * 0.5f) {
                    player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 40, 1, false, false, true));
                }
            }

            // Rüzgar Adımı (wind_step): Koşarken Zıplama Desteği
            if (skills.contains("wind_step") && player.isSprinting()) {
                player.addEffect(new MobEffectInstance(MobEffects.JUMP, 40, 0, false, false, false));
            }

            // Göksel Kurt (celestial_wolf): Sürekli hafif Zıplama Desteği
            if (skills.contains("celestial_wolf")) {
                player.addEffect(new MobEffectInstance(MobEffects.JUMP, 40, 1, false, false, false));
            }

            // Sürü Bağlantısı (pack_bond): Yakındaki kurtlar oyuncuya düşman olmaz
            if (skills.contains("pack_bond")) {
                AABB kurtAlani = player.getBoundingBox().inflate(12.0);
                for (Wolf kurt : player.serverLevel().getEntitiesOfClass(Wolf.class, kurtAlani)) {
                    if (kurt.getTarget() == player) {
                        kurt.setTarget(null);
                    }
                }
            }
        }
    }

    // ================= STATÜ VE ATTRIBUTE BONUSLARINI UYGULA =================

    public static void uygulaBonuslar(ServerPlayer player, InfectionData data) {
        UUID uuid = player.getUUID();
        if (!data.isInfected(uuid)) return;

        Set<String> skills = data.getSkillIds(uuid);

        // 1. Ekstra Can (wild_awakening + alpha_wolf)
        double ekstraCan = 0.0;
        if (skills.contains("wild_awakening")) ekstraCan += 4.0;
        if (skills.contains("alpha_wolf"))      ekstraCan += 6.0;

        AttributeInstance healthAttr = player.getAttribute(Attributes.MAX_HEALTH);
        if (healthAttr != null) {
            healthAttr.removeModifier(YETENEK_CAN_ID);
            if (ekstraCan > 0) {
                healthAttr.addTransientModifier(new AttributeModifier(
                        YETENEK_CAN_ID, "kurt_yetenek_can", ekstraCan, AttributeModifier.Operation.ADDITION));
            }
        }

        // 2. Saldırı Hasarı (sharp_claws + alpha_wolf)
        double ekstraHasar = 0.0;
        if (skills.contains("sharp_claws")) ekstraHasar += 3.0;
        if (skills.contains("alpha_wolf"))  ekstraHasar += 2.5;

        AttributeInstance dmgAttr = player.getAttribute(Attributes.ATTACK_DAMAGE);
        if (dmgAttr != null) {
            dmgAttr.removeModifier(YETENEK_HASAR_ID);
            if (ekstraHasar > 0) {
                dmgAttr.addTransientModifier(new AttributeModifier(
                        YETENEK_HASAR_ID, "kurt_yetenek_hasar", ekstraHasar, AttributeModifier.Operation.ADDITION));
            }
        }

        // 3. Hareket Hızı (moon_lightness + alpha_wolf)
        double ekstraHizOrani = 0.0;
        if (skills.contains("moon_lightness")) ekstraHizOrani += 0.15;
        if (skills.contains("alpha_wolf"))     ekstraHizOrani += 0.10;

        AttributeInstance speedAttr = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speedAttr != null) {
            speedAttr.removeModifier(YETENEK_HIZ_ID);
            if (ekstraHizOrani > 0) {
                speedAttr.addTransientModifier(new AttributeModifier(
                        YETENEK_HIZ_ID, "kurt_yetenek_hiz", ekstraHizOrani, AttributeModifier.Operation.MULTIPLY_BASE));
            }
        }

        // 4. Zırh Sertliği (predator_intuition)
        double ekstraZirhSertligi = 0.0;
        if (skills.contains("predator_intuition")) ekstraZirhSertligi += 2.0;

        AttributeInstance armorAttr = player.getAttribute(Attributes.ARMOR_TOUGHNESS);
        if (armorAttr != null) {
            armorAttr.removeModifier(YETENEK_ZIRH_ID);
            if (ekstraZirhSertligi > 0) {
                armorAttr.addTransientModifier(new AttributeModifier(
                        YETENEK_ZIRH_ID, "kurt_yetenek_zirh", ekstraZirhSertligi, AttributeModifier.Operation.ADDITION));
            }
        }
    }
}

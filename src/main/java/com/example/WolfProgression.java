package com.example;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Seviye / yetenek sisteminin SUNUCU tarafı:
 *  - istemciye XP + Kaos kilidi + açılmış yetenek durumunu gönderir (LEVEL_SENKRON)
 *  - istemciden gelen yetenek açma isteklerini doğrular (YETENEK_AC)
 *
 * İstemciye asla güvenilmez: puan, ön koşul, seviye, kilit ve enfekte olma durumu burada kontrol edilir.
 */
public class WolfProgression {

    private WolfProgression() {
    }

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(WolfNetworking.YETENEK_AC,
                (server, player, handler, buf, responseSender) -> {
                    String yetenekId = buf.readUtf(32); // uzunluk sınırı: kötü niyetli dev paketlere karşı
                    server.execute(() -> yetenekAcmayiDene(player, yetenekId));
                });
    }

    /**
     * Oyuncuya kendi durumunu gönderir. Paket sırası (istemci aynı sırayla okur):
     * enfekte(bool), xp(varint), kaosKilidiAcik(bool), yetenekSayisi(varint), yetenek id'leri(utf)...
     * Giriş, respawn, XP, enfeksiyon, iksir ve yetenek değişiminde çağır.
     */
    public static void senkronla(ServerPlayer player, InfectionData data) {
        UUID uuid = player.getUUID();
        Set<String> yetenekler = data.getSkillIds(uuid);

        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeBoolean(data.isInfected(uuid));
        buf.writeVarInt(data.getXp(uuid));
        buf.writeBoolean(data.isChaosUnlocked(uuid));
        buf.writeVarInt(yetenekler.size());
        for (String id : yetenekler) {
            buf.writeUtf(id, 32);
        }
        ServerPlayNetworking.send(player, WolfNetworking.LEVEL_SENKRON, buf);
    }

    private static void yetenekAcmayiDene(ServerPlayer player, String yetenekId) {
        InfectionData data = InfectionData.get(player.server);
        UUID uuid = player.getUUID();

        if (!data.isInfected(uuid)) {
            return;
        }

        WolfSkill yetenek = WolfSkill.idIle(yetenekId);
        if (yetenek == null) {
            return;
        }

        Set<String> acik = data.getSkillIds(uuid);
        WolfSkill.Sonuc sonuc = WolfSkill.degerlendir(yetenek, data.getXp(uuid), acik, data.isChaosUnlocked(uuid));

        if (sonuc == WolfSkill.Sonuc.ZATEN_ACIK) {
            return; // hızlı çift tıklama olabilir, sessizce yoksay
        }
        if (sonuc != WolfSkill.Sonuc.ACILABILIR) {
            player.sendSystemMessage(Component.literal(redMesaji(yetenek, sonuc, acik)));
            return;
        }

        data.unlockSkill(uuid, yetenek.getId());
        WolfSkillPassives.uygulaBonuslar(player, data);

        player.serverLevel().playSound(null, player.blockPosition(),
                SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1.0f, 1.2f);
        player.sendSystemMessage(Component.literal("Yeni yetenek açıldı: " + yetenek.getAd()));

        senkronla(player, data);
    }

    private static String redMesaji(WolfSkill yetenek, WolfSkill.Sonuc sonuc, Set<String> acik) {
        return switch (sonuc) {
            case KATALIZOR_GEREKLI -> "Bu dal henüz uyanmadı. Kaos İksiri gerekli.";
            case ON_KOSUL_EKSIK -> "Önce şunları açmalısın: " + yetenek.getOnKosullar().stream()
                    .filter(on -> !on.acikMi(acik))
                    .map(WolfSkill::getAd)
                    .collect(Collectors.joining(", "));
            case SEVIYE_YETERSIZ -> "Bu yetenek için seviye " + yetenek.getMinSeviye() + " gerekli.";
            case PUAN_YOK -> "Yetenek puanın yok.";
            default -> "Bu yetenek şu an açılamaz.";
        };
    }
}
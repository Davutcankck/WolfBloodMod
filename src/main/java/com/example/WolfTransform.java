package com.example;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.Commands;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;

public class WolfTransform {

    // DÃ¶nÃ¼ÅŸÃ¼m/enfeksiyon durumu tek doÄŸruluk kaynaÄŸÄ± olarak InfectionData'da tutuluyor.

    private static final long ENFEKSIYON_TAMAMLANMA_SURESI = 24000; // ~1 Minecraft gÃ¼nÃ¼ (20 dakika)

    private static final UUID HIZ_MOD_ID = UUID.fromString("a1111111-0000-0000-0000-000000000001");
    private static final UUID GUC_MOD_ID = UUID.fromString("a2222222-0000-0000-0000-000000000002");
    private static final UUID CAN_MOD_ID = UUID.fromString("a3333333-0000-0000-0000-000000000003");

    private static final double HIZ_ORANI = 0.35;
    private static final double GUC_ORANI = 0.4;
    private static final double EKSTRA_CAN = 8.0;
    private static final int ENFEKSIYON_SURESI = 20 * 30;

    // ================= KONTROL KAYBI ANI (kuluÃ§ka sÄ±rasÄ±nda rastgele, kÄ±sa sÃ¼reli) =================
    // Bellek iÃ§i - kalÄ±cÄ± olmasÄ±na gerek yok, sadece zamanlamayÄ± seyreltmek iÃ§in kullanÄ±lÄ±yor.
    private static final Map<UUID, Long> SON_KONTROL_KAYBI = new HashMap<>();
    private static final Random RASTGELE = new Random();
    private  static  final Map<UUID,Boolean> SON_YER_KONTROLU = new HashMap<>() ;

    private static final int KONTROL_KAYBI_SURESI = 20 * 10;      // efekt sÃ¼resi: 4 saniye
    private static final int KONTROL_KAYBI_MIN_ARALIK = 20 * 60; // aynÄ± oyuncuda en az 60 saniye ara
    private static final int KONTROL_KAYBI_IHTIMALI = 30;       // saniyede bir kontrol, 1/150 ihtimal (~ortalama 2.5 dakikada bir)


    // "resource": IntelliJ, ServerLevel'Ä± AutoCloseable bir kaynak sanÄ±p try-with-resources Ã¶neriyor.
    // Bu yanlÄ±ÅŸ pozitif bir uyarÄ±dÄ±r - level nesnesi sunucu tarafÄ±ndan yÃ¶netilir, elle kapatÄ±lmamalÄ±dÄ±r.
    @SuppressWarnings("resource")
    public static void register() {

        // ================= GÄ°RÄ°Å: oyuncu login olduÄŸunda bonuslarÄ± geri yÃ¼kle =================
        // Attribute modifier'lar oyuncu Ã§Ä±kÄ±ÅŸ yapÄ±nca kaybolur (transient), bu yÃ¼zden her login'de
        // enfekte/dÃ¶nÃ¼ÅŸÃ¼m durumuna gÃ¶re ilgili bonuslarÄ± yeniden uygulamamÄ±z gerekir.
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.player;
            InfectionData data = InfectionData.get(server);
            senkronizeEt(player, data);
            mevcutFormlariGonder(player, data);
        });

        // ================= RESPAWN: Ã¶lÃ¼p yeniden doÄŸduÄŸunda bonuslarÄ± geri yÃ¼kle =================
        // Ã–lÃ¼m/respawn sÄ±rasÄ±nda oyuncu iÃ§in yeni bir ServerPlayer nesnesi (ve yeni attribute
        // instance'larÄ±) oluÅŸturulur, bu yÃ¼zden transient modifier'lar kaybolur.
        // "alive = false" â†’ gerÃ§ek Ã¶lÃ¼m; "alive = true" â†’ boyut geÃ§iÅŸi (Ã¶lÃ¼m sayÄ±lmaz).
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            InfectionData data = InfectionData.get(newPlayer.serverLevel().getServer());

            if (!alive) {
                // GerÃ§ek Ã¶lÃ¼m + kuluÃ§kadaysa: enfeksiyonu tamamen sÄ±fÄ±rla.
                if (data.isIncubating(newPlayer.getUUID())) {
                    data.removeIncubation(newPlayer.getUUID());
                    newPlayer.sendSystemMessage(Component.literal(
                            "Ã–lÃ¼m iÃ§indeki uyanÄ±ÅŸÄ± durdurdu... Kurt kanÄ± vÃ¼cudundan ayrÄ±ldÄ±."));
                }

                // GerÃ§ek Ã¶lÃ¼m + kurt adam formundaysa: insan olarak respawn et.
                // Oyuncu isterse yeniden /kurtadam veya G tuÅŸuyla dÃ¶nÃ¼ÅŸebilir.
                if (data.isTransformed(newPlayer.getUUID())) {
                    data.setTransformed(newPlayer.getUUID(), false);
                    formDurumunuYayinla(newPlayer, false); // istemcilere bildir (gÃ¶rsel sÄ±fÄ±rlanÄ±r)
                    newPlayer.sendSystemMessage(Component.literal(
                            "Ã–lÃ¼m seni insan formuna geri dÃ¶ndÃ¼rdÃ¼."));
                }
            }

            senkronizeEt(newPlayer, data);
        });

        // ================= KOMUT: /kurtadam (manuel) =================
        // Bu komut SADECE dÃ¶nÃ¼ÅŸÃ¼m formunu (hÄ±z+gÃ¼Ã§) deÄŸiÅŸtirir. Ekstra can enfekte olduÄŸun
        // andan itibaren zaten kalÄ±cÄ± olarak Ã¼zerinde, formdan baÄŸÄ±msÄ±z.
        // Dolunay gecesi insan formuna GERÄ° DÃ–NÃœÅ engellenir â€” ay serbest bÄ±rakana kadar
        // kurt adam formundan Ã§Ä±kamazsÄ±n. DÃ¶nÃ¼ÅŸmek (insan â†’ kurt adam) her zaman serbesttir.
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("kurtadam").executes(context -> {
                ServerPlayer player = context.getSource().getPlayerOrException();
                InfectionData data = InfectionData.get(context.getSource().getServer());

                if (!data.isInfected(player.getUUID())) {
                    context.getSource().sendFailure(Component.literal("Sen sÄ±radan bir insansÄ±n, damarlarÄ±nda kurt kanÄ± yok."));
                    return 0;
                }

                boolean dolunayGecesi = player.serverLevel().getMoonPhase() == 0
                        && player.serverLevel().isNight();

                if (data.isTransformed(player.getUUID())) {
                    // Dolunayda geri dÃ¶nÃ¼ÅŸ yasak
                    if (dolunayGecesi) {
                        context.getSource().sendFailure(Component.literal("AyÄ±n gÃ¼cÃ¼ne karÅŸÄ± koyamazsÄ±n... SabahÄ± bekle."));
                        return 0;
                    }
                    donusumuKaldir(player, data);
                    context.getSource().sendSuccess(() -> Component.literal("Ä°nsan formuna geri dÃ¶ndÃ¼n."), false);
                } else {
                    donusumUygula(player, data); // sessiz, ses YOK
                    context.getSource().sendSuccess(() -> Component.literal("Kurt adama dÃ¶nÃ¼ÅŸtÃ¼n! Uluu!"), false);
                }
                return 1;
            }));
        });

        // ================= Ä°KSÄ°R KULLANIMI: sadece dolunayda iÃ§ilebilir =================
        UseItemCallback.EVENT.register((player, level, hand) -> {
            ItemStack stack = player.getItemInHand(hand);

            if (stack.is(WolfItem.KURT_KANI) && !level.isClientSide && player instanceof ServerPlayer serverPlayer) {
                InfectionData data = InfectionData.get(serverPlayer.serverLevel().getServer());

                if (level.getMoonPhase() != 0 || !level.isNight()) {
                    serverPlayer.sendSystemMessage(Component.literal("Kan henÃ¼z uyanmaya hazÄ±r deÄŸil..."));
                    return InteractionResultHolder.fail(stack);
                }

                if (data.isInfected(player.getUUID())) {
                    serverPlayer.sendSystemMessage(Component.literal("Zaten damarlarÄ±nda kurt kanÄ± var."));
                    return InteractionResultHolder.pass(stack);
                }

                if (data.isIncubating(player.getUUID())) {
                    serverPlayer.sendSystemMessage(Component.literal("KanÄ±ndaki ÅŸey zaten uyanÄ±yor..."));
                    return InteractionResultHolder.pass(stack);
                }

                data.startIncubation(player.getUUID(), serverPlayer.serverLevel().getServer().getTickCount());

                player.addEffect(new MobEffectInstance(WolfEffects.ENFEKSIYON, (int) ENFEKSIYON_TAMAMLANMA_SURESI, 0, false, false, true));
                player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, ENFEKSIYON_SURESI, 0, false, false, false));
                player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, ENFEKSIYON_SURESI, 0, false, false, false));
                serverPlayer.sendSystemMessage(Component.literal("DamarlarÄ±na bir ÅŸey enjekte edildi... Ä°Ã§indeki bir ÅŸey uyanÄ±yor."));

                if (!player.getAbilities().instabuild) {
                    stack.shrink(1);
                }
                return InteractionResultHolder.success(stack);
            }
            return InteractionResultHolder.pass(stack);
        });

        // ================= TICK DÃ–NGÃœSÃœ: saniyede bir kontrol =================
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 20 != 0) return;

            InfectionData data = InfectionData.get(server);

            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                ServerLevel level = player.serverLevel();
                UUID uuid = player.getUUID();

                Long tamamlanmaTick = data.getIncubationCompleteAt(uuid);
                if (tamamlanmaTick != null) {
                    if (server.getTickCount() >= tamamlanmaTick) {
                        data.removeIncubation(uuid);
                        data.addInfected(uuid);

                        // ARTIK: enfeksiyon tamamlanÄ±nca SADECE ekstra can bonusu gelir,
                        // oyuncu otomatik kurt adam formuna geÃ§mez. Forma geÃ§iÅŸ ancak
                        // dolunay gecesi (aÅŸaÄŸÄ±da) veya /kurtadam komutuyla olur.
                        uygulaSaglikBonusu(player);
                        player.heal((float) EKSTRA_CAN);

                        player.sendSystemMessage(Component.literal("Enfeksiyon tamamlandÄ±. DamarlarÄ±nda artÄ±k kurt kanÄ± akÄ±yor..."));
                        level.playSound(null, player.blockPosition(), SoundEvents.WOLF_HOWL, SoundSource.PLAYERS, 1.0f, 1.0f);
                    } else {
                        // Enfeksiyon devam ederken, arada bir "iÃ§indeki canavar" kÄ±sa sÃ¼reliÄŸine
                        // kontrolÃ¼ ele geÃ§irmeye Ã§alÄ±ÅŸÄ±yormuÅŸ hissi vermek iÃ§in rastgele tetikleme.
                        kontrolKaybiIhtimaliniDene(player, uuid, server);
                    }
                }

                boolean dolunayGecesi = level.getMoonPhase() == 0 && level.isNight();
                boolean enfekteAmaInsan = data.isInfected(uuid) && !data.isTransformed(uuid);

                if (dolunayGecesi && enfekteAmaInsan) {
                    donusumUygula(player, data);
                    player.sendSystemMessage(Component.literal("Ay seni Ã§aÄŸÄ±rÄ±yor... KontrolÃ¼ kaybediyorsun!"));
                    level.playSound(null, player.blockPosition(), SoundEvents.WOLF_HOWL, SoundSource.PLAYERS, 1.0f, 1.0f);
                }
            }
        });
    }

    // Login/respawn sonrasÄ± Ã§aÄŸrÄ±lÄ±r: enfekte ise can bonusunu, dÃ¶nÃ¼ÅŸmÃ¼ÅŸse hÄ±z+gÃ¼Ã§ bonusunu geri yÃ¼kler.
    // Ä°kisi birbirinden baÄŸÄ±msÄ±z kontrol edilir.
    private static void senkronizeEt(ServerPlayer player, InfectionData data) {
        if (data.isInfected(player.getUUID())) {
            uygulaSaglikBonusu(player);
        }
        if (data.isTransformed(player.getUUID())) {
            uygulaSavasBonuslari(player);
            player.refreshDimensions();
        }
        WolfProgression.senkronla(player, data);
    }

    // ================= CAN BONUSU: enfekte olduÄŸun andan itibaren kalÄ±cÄ±, forma baÄŸlÄ± deÄŸil =================

    private static void uygulaSaglikBonusu(ServerPlayer player) {
        Objects.requireNonNull(player.getAttribute(Attributes.MAX_HEALTH)).addTransientModifier(
                new AttributeModifier(CAN_MOD_ID, "kurt_can", EKSTRA_CAN, AttributeModifier.Operation.ADDITION));
    }

    // ================= SAVAÅ BONUSLARI (hÄ±z + gÃ¼Ã§): sadece kurt adam formundayken =================

    private static void uygulaSavasBonuslari(ServerPlayer player) {
        Objects.requireNonNull(player.getAttribute(Attributes.MOVEMENT_SPEED)).addTransientModifier(
                new AttributeModifier(HIZ_MOD_ID, "kurt_hiz", HIZ_ORANI, AttributeModifier.Operation.MULTIPLY_TOTAL));
        Objects.requireNonNull(player.getAttribute(Attributes.ATTACK_DAMAGE)).addTransientModifier(
                new AttributeModifier(GUC_MOD_ID, "kurt_guc", GUC_ORANI, AttributeModifier.Operation.MULTIPLY_TOTAL));
    }

    private static void kaldirSavasBonuslari(ServerPlayer player) {
        Objects.requireNonNull(player.getAttribute(Attributes.MOVEMENT_SPEED)).removeModifier(HIZ_MOD_ID);
        Objects.requireNonNull(player.getAttribute(Attributes.ATTACK_DAMAGE)).removeModifier(GUC_MOD_ID);
    }

    // ================= FORM DEÄÄ°ÅTÄ°RME (sadece hÄ±z+gÃ¼Ã§; can bonusu ayrÄ± yÃ¶netiliyor) =================

    private static void donusumUygula(ServerPlayer player, InfectionData data) {
        data.setTransformed(player.getUUID(), true); // KALICI: diske yazÄ±lÄ±r
        uygulaSavasBonuslari(player);
        player.refreshDimensions();
        formDurumunuYayinla(player, true);
    }

    private static void donusumuKaldir(ServerPlayer player, InfectionData data) {
        data.setTransformed(player.getUUID(), false); // KALICI: diske yazÄ±lÄ±r
        kaldirSavasBonuslari(player);
        player.refreshDimensions();
        formDurumunuYayinla(player, false);
    }

    private static void formDurumunuYayinla(ServerPlayer player, boolean donustu) {
        for (ServerPlayer alici : player.getServer().getPlayerList().getPlayers()) {
            formPaketiniGonder(alici, player.getUUID(), donustu);
        }
    }

    private static void mevcutFormlariGonder(ServerPlayer hedef, InfectionData data) {
        for (UUID uuid : data.getTransformedPlayersSnapshot()) {
            formPaketiniGonder(hedef, uuid, true);
        }
    }

    private static void formPaketiniGonder(ServerPlayer alici, UUID uuid, boolean donustu) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeUUID(uuid);
        buf.writeBoolean(donustu);
        ServerPlayNetworking.send(alici, WolfNetworking.FORM_SENKRON, buf);
    }

    // ================= KONTROL KAYBI ANI =================

    private static void kontrolKaybiIhtimaliniDene(ServerPlayer player, UUID uuid, MinecraftServer server) {
        long simdikiTick = server.getTickCount();

        Long sonTetiklenme = SON_KONTROL_KAYBI.get(uuid);
        if (sonTetiklenme != null && simdikiTick - sonTetiklenme < KONTROL_KAYBI_MIN_ARALIK) {
            return; // Ã§ok yakÄ±n zamanda tetiklendi, bekle
        }

        if (RASTGELE.nextInt(KONTROL_KAYBI_IHTIMALI) != 0) {
            return; // bu sefer ÅŸansÄ±na kÃ¼smedi
        }

        SON_KONTROL_KAYBI.put(uuid, simdikiTick);
        kontrolKaybiUygula(player);
    }

    private static void kontrolKaybiUygula(ServerPlayer player) {
        // Sunucu tarafÄ±nda kÄ±sa sÃ¼reli bir "saldÄ±rganlÄ±k" hissi: gÃ¼Ã§ + hÄ±z artÄ±ÅŸÄ±
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, KONTROL_KAYBI_SURESI, 0, false, false, true));
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, KONTROL_KAYBI_SURESI, 1, false, false, true));

        player.sendSystemMessage(Component.literal("Ä°Ã§indeki canavar bir an iÃ§in dÄ±ÅŸarÄ± Ã§Ä±kmaya Ã§alÄ±ÅŸtÄ±..."));
        player.level().playSound(null, player.blockPosition(), SoundEvents.WOLF_GROWL, SoundSource.PLAYERS, 0.8f, 0.7f);

        // Ä°stemciye bildir ki ekranda kÄ±zarma + damar efekti gÃ¶stersin
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeInt(KONTROL_KAYBI_SURESI);
        ServerPlayNetworking.send(player, WolfNetworking.KONTROL_KAYBI, buf);
    }
}

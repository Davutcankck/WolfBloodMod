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

    // Dönüşüm/enfeksiyon durumu tek doğruluk kaynağı olarak InfectionData'da tutuluyor.

    private static final long ENFEKSIYON_TAMAMLANMA_SURESI = 24000; // ~1 Minecraft günü (20 dakika)

    private static final UUID HIZ_MOD_ID = UUID.fromString("a1111111-0000-0000-0000-000000000001");
    private static final UUID GUC_MOD_ID = UUID.fromString("a2222222-0000-0000-0000-000000000002");
    private static final UUID CAN_MOD_ID = UUID.fromString("a3333333-0000-0000-0000-000000000003");

    private static final double HIZ_ORANI = 0.35;
    private static final double GUC_ORANI = 0.4;
    private static final double EKSTRA_CAN = 8.0;
    private static final int ENFEKSIYON_SURESI = 20 * 30;

    // ================= KONTROL KAYBI ANI (kuluçka sırasında rastgele, kısa süreli) =================
    // Bellek içi - kalıcı olmasına gerek yok, sadece zamanlamayı seyreltmek için kullanılıyor.
    private static final Map<UUID, Long> SON_KONTROL_KAYBI = new HashMap<>();
    private static final Random RASTGELE = new Random();
    private  static  final Map<UUID,Boolean> SON_YER_KONTROLU = new HashMap<>() ;

    private static final int KONTROL_KAYBI_SURESI = 20 * 10;      // efekt süresi: 4 saniye
    private static final int KONTROL_KAYBI_MIN_ARALIK = 20 * 60; // aynı oyuncuda en az 60 saniye ara
    private static final int KONTROL_KAYBI_IHTIMALI = 30;       // saniyede bir kontrol, 1/150 ihtimal (~ortalama 2.5 dakikada bir)


    // "resource": IntelliJ, ServerLevel'ı AutoCloseable bir kaynak sanıp try-with-resources öneriyor.
    // Bu yanlış pozitif bir uyarıdır - level nesnesi sunucu tarafından yönetilir, elle kapatılmamalıdır.
    @SuppressWarnings("resource")
    public static void register() {

        // ================= GİRİÅ: oyuncu login olduğunda bonusları geri yükle =================
        // Attribute modifier'lar oyuncu çıkış yapınca kaybolur (transient), bu yüzden her login'de
        // enfekte/dönüşüm durumuna göre ilgili bonusları yeniden uygulamamız gerekir.
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.player;
            InfectionData data = InfectionData.get(server);
            senkronizeEt(player, data);
            mevcutFormlariGonder(player, data);
        });

        // ================= RESPAWN: ölüp yeniden doğduğunda bonusları geri yükle =================
        // Ölüm/respawn sırasında oyuncu için yeni bir ServerPlayer nesnesi (ve yeni attribute
        // instance'ları) oluşturulur, bu yüzden transient modifier'lar kaybolur.
        // "alive = false" â†’ gerçek ölüm; "alive = true" â†’ boyut geçişi (ölüm sayılmaz).
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            InfectionData data = InfectionData.get(newPlayer.serverLevel().getServer());

            if (!alive) {
                // Gerçek ölüm + kuluçkadaysa: enfeksiyonu tamamen sıfırla.
                if (data.isIncubating(newPlayer.getUUID())) {
                    data.removeIncubation(newPlayer.getUUID());
                    newPlayer.sendSystemMessage(Component.literal(
                            "Ölüm içindeki uyanışı durdurdu... Kurt kanı vücudundan ayrıldı."));
                }

                // Gerçek ölüm + kurt adam formundaysa: insan olarak respawn et.
                // Oyuncu isterse yeniden /kurtadam veya G tuşuyla dönüşebilir.
                if (data.isTransformed(newPlayer.getUUID())) {
                    data.setTransformed(newPlayer.getUUID(), false);
                    formDurumunuYayinla(newPlayer, false); // istemcilere bildir (görsel sıfırlanır)
                    newPlayer.sendSystemMessage(Component.literal(
                            "Ölüm seni insan formuna geri döndürdü."));
                }
            }

            senkronizeEt(newPlayer, data);
        });

        // ================= KOMUT: /kurtadam (manuel) =================
        // Bu komut SADECE dönüşüm formunu (hız+güç) değiştirir. Ekstra can enfekte olduğun
        // andan itibaren zaten kalıcı olarak üzerinde, formdan bağımsız.
        // Dolunay gecesi insan formuna GERİ DÖNÜÅ engellenir â€” ay serbest bırakana kadar
        // kurt adam formundan çıkamazsın. Dönüşmek (insan â†’ kurt adam) her zaman serbesttir.
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("kurtadam").executes(context -> {
                ServerPlayer player = context.getSource().getPlayerOrException();
                InfectionData data = InfectionData.get(context.getSource().getServer());

                if (!data.isInfected(player.getUUID())) {
                    context.getSource().sendFailure(Component.literal("Sen sıradan bir insansın, damarlarında kurt kanı yok."));
                    return 0;
                }

                boolean dolunayGecesi = player.serverLevel().getMoonPhase() == 0
                        && player.serverLevel().isNight();

                if (data.isTransformed(player.getUUID())) {
                    // Dolunayda geri dönüş yasak
                    if (dolunayGecesi) {
                        context.getSource().sendFailure(Component.literal("Ayın gücüne karşı koyamazsın... Sabahı bekle."));
                        return 0;
                    }
                    donusumuKaldir(player, data);
                    context.getSource().sendSuccess(() -> Component.literal("İnsan formuna geri döndün."), false);
                } else {
                    donusumUygula(player, data); // sessiz, ses YOK
                    context.getSource().sendSuccess(() -> Component.literal("Kurt adama dönüştün! Uluu!"), false);
                }
                return 1;
            }));
        });

        // ================= İKSİR KULLANIMI: sadece dolunayda içilebilir =================
        UseItemCallback.EVENT.register((player, level, hand) -> {
            ItemStack stack = player.getItemInHand(hand);

            if (stack.is(WolfItem.KURT_KANI) && !level.isClientSide && player instanceof ServerPlayer serverPlayer) {
                InfectionData data = InfectionData.get(serverPlayer.serverLevel().getServer());

                if (level.getMoonPhase() != 0 || !level.isNight()) {
                    serverPlayer.sendSystemMessage(Component.literal("Kan henüz uyanmaya hazır değil..."));
                    return InteractionResultHolder.fail(stack);
                }

                if (data.isInfected(player.getUUID())) {
                    serverPlayer.sendSystemMessage(Component.literal("Zaten damarlarında kurt kanı var."));
                    return InteractionResultHolder.pass(stack);
                }

                if (data.isIncubating(player.getUUID())) {
                    serverPlayer.sendSystemMessage(Component.literal("Kanındaki şey zaten uyanıyor..."));
                    return InteractionResultHolder.pass(stack);
                }

                data.startIncubation(player.getUUID(), level.getGameTime() + ENFEKSIYON_TAMAMLANMA_SURESI);

                player.addEffect(new MobEffectInstance(WolfEffects.ENFEKSIYON, (int) ENFEKSIYON_TAMAMLANMA_SURESI, 0, false, false, true));
                player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, ENFEKSIYON_SURESI, 0, false, false, false));
                player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, ENFEKSIYON_SURESI, 0, false, false, false));
                serverPlayer.sendSystemMessage(Component.literal("Damarlarına bir şey enjekte edildi... İçindeki bir şey uyanıyor."));

                if (!player.getAbilities().instabuild) {
                    stack.shrink(1);
                }
                return InteractionResultHolder.success(stack);
            }
            return InteractionResultHolder.pass(stack);
        });

        // ================= TICK DÖNGÜSÜ: saniyede bir kontrol =================
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 20 != 0) return;

            InfectionData data = InfectionData.get(server);

            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                ServerLevel level = player.serverLevel();
                UUID uuid = player.getUUID();

                Long tamamlanmaTick = data.getIncubationCompleteAt(uuid);
                if (tamamlanmaTick != null) {
                    if (level.getGameTime() >= tamamlanmaTick) {
                        data.removeIncubation(uuid);
                        data.addInfected(uuid);

                        // ARTIK: enfeksiyon tamamlanınca SADECE ekstra can bonusu gelir,
                        // oyuncu otomatik kurt adam formuna geçmez. Forma geçiş ancak
                        // dolunay gecesi (aşağıda) veya /kurtadam komutuyla olur.
                        uygulaSaglikBonusu(player);
                        player.heal((float) EKSTRA_CAN);

                        player.sendSystemMessage(Component.literal("Enfeksiyon tamamlandı. Damarlarında artık kurt kanı akıyor..."));
                        level.playSound(null, player.blockPosition(), SoundEvents.WOLF_HOWL, SoundSource.PLAYERS, 1.0f, 1.0f);
                    } else {
                        // Enfeksiyon devam ederken, arada bir "içindeki canavar" kısa süreliğine
                        // kontrolü ele geçirmeye çalışıyormuş hissi vermek için rastgele tetikleme.
                        kontrolKaybiIhtimaliniDene(player, uuid, server);
                    }
                }

                boolean dolunayGecesi = level.getMoonPhase() == 0 && level.isNight();
                boolean enfekteAmaInsan = data.isInfected(uuid) && !data.isTransformed(uuid);

                if (dolunayGecesi && enfekteAmaInsan) {
                    donusumUygula(player, data);
                    player.sendSystemMessage(Component.literal("Ay seni çağırıyor... Kontrolü kaybediyorsun!"));
                    level.playSound(null, player.blockPosition(), SoundEvents.WOLF_HOWL, SoundSource.PLAYERS, 1.0f, 1.0f);
                }
            }
        });
    }

    // Login/respawn sonrası çağrılır: enfekte ise can bonusunu, dönüşmüşse hız+güç bonusunu geri yükler.
    // İkisi birbirinden bağımsız kontrol edilir.
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

    // ================= CAN BONUSU: enfekte olduğun andan itibaren kalıcı, forma bağlı değil =================

    private static void uygulaSaglikBonusu(ServerPlayer player) {
        Objects.requireNonNull(player.getAttribute(Attributes.MAX_HEALTH)).addTransientModifier(
                new AttributeModifier(CAN_MOD_ID, "kurt_can", EKSTRA_CAN, AttributeModifier.Operation.ADDITION));
    }

    // ================= SAVAÅ BONUSLARI (hız + güç): sadece kurt adam formundayken =================

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

    // ================= FORM DEÄİÅTİRME (sadece hız+güç; can bonusu ayrı yönetiliyor) =================

    private static void donusumUygula(ServerPlayer player, InfectionData data) {
        data.setTransformed(player.getUUID(), true); // KALICI: diske yazılır
        uygulaSavasBonuslari(player);
        player.refreshDimensions();
        formDurumunuYayinla(player, true);
    }

    private static void donusumuKaldir(ServerPlayer player, InfectionData data) {
        data.setTransformed(player.getUUID(), false); // KALICI: diske yazılır
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
            return; // çok yakın zamanda tetiklendi, bekle
        }

        if (RASTGELE.nextInt(KONTROL_KAYBI_IHTIMALI) != 0) {
            return; // bu sefer şansına küsmedi
        }

        SON_KONTROL_KAYBI.put(uuid, simdikiTick);
        kontrolKaybiUygula(player);
    }

    private static void kontrolKaybiUygula(ServerPlayer player) {
        // Sunucu tarafında kısa süreli bir "saldırganlık" hissi: güç + hız artışı
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, KONTROL_KAYBI_SURESI, 0, false, false, true));
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, KONTROL_KAYBI_SURESI, 1, false, false, true));

        player.sendSystemMessage(Component.literal("İçindeki canavar bir an için dışarı çıkmaya çalıştı..."));
        player.level().playSound(null, player.blockPosition(), SoundEvents.WOLF_GROWL, SoundSource.PLAYERS, 0.8f, 0.7f);

        // İstemciye bildir ki ekranda kızarma + damar efekti göstersin
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeInt(KONTROL_KAYBI_SURESI);
        ServerPlayNetworking.send(player, WolfNetworking.KONTROL_KAYBI, buf);
    }
}

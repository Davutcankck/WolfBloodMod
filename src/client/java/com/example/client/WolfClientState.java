package com.example.client;

import com.example.WerewolfScale;
import com.example.WolfNetworking;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// Sunucudan gelen ANLIK form bilgisini (dönüştü/dönmedi) alıp, render için KADEMELİ bir
// "ilerleme" (0.0 = tam insan, 1.0 = tam kurt adam) değerine çevirir.
//
// Ölçek (render scale), göz parlaklığı ve ileride eklenecek kulak/pençe gibi TÜM görsel
// öğeler bu TEK değere (getIlerleme) bağlanmalı - böylece hepsi otomatik senkron büyür/küçülür,
// ayrı ayrı animasyon yönetmene gerek kalmaz.
//
// NOT: Hitbox/çarpışma kutusu (WerewolfScale + PlayerWerewolfSizeMixin) bu sistemden AYRI ve
// hâlâ anlık (binary) çalışıyor - fiziksel güvenlik için kasıtlı olarak böyle bırakıldı.
public class WolfClientState {

    // Bir tick'te ilerlemenin ne kadar değişeceği. 0.15 ~= ~7 tick'te (~0.35 sn) tam geçiş.
    // Daha yavaş/hızlı animasyon istersen sadece bu sayıyı değiştir.
    private static final float ILERLEME_HIZI = 0.15f;

    // Paket alınmış (yani en az bir kez dönüşüm durumu bildirilmiş) tüm oyuncular.
    // Hedefe (0'a) ulaşana kadar burada kalırlar, sonra temizlenirler.
    private static final Set<UUID> IZLENENLER = ConcurrentHashMap.newKeySet();

    // Bu tick'teki ilerleme değeri
    private static final Map<UUID, Float> ILERLEME = new ConcurrentHashMap<>();
    // Bir önceki tick'teki ilerleme değeri (partial-tick interpolasyonu için gerekli)
    private static final Map<UUID, Float> ONCEKI_ILERLEME = new ConcurrentHashMap<>();

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(WolfNetworking.FORM_SENKRON, (client, handler, buf, responseSender) -> {
            UUID uuid = buf.readUUID();
            boolean donustu = buf.readBoolean();

            client.execute(() -> {
                WerewolfScale.istemciAyarla(uuid, donustu); // hitbox için - anlık, kendi işini yapıyor
                IZLENENLER.add(uuid); // animasyon takibine al (insana dönerken de 0'a insin diye burada kalmalı)

                if (client.level != null) {
                    Player oyuncu = client.level.getPlayerByUUID(uuid);
                    if (oyuncu != null) {
                        oyuncu.refreshDimensions(); // hitbox anlık güncellensin
                    }
                }
            });
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> tumunuIlerlet());

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            WerewolfScale.istemciTemizle();
            IZLENENLER.clear();
            ILERLEME.clear();
            ONCEKI_ILERLEME.clear();
        });
    }

    // Her client tick'te (saniyede 20 kez) çağrılır: izlenen her oyuncunun ilerlemesini
    // hedefe (dönüşmüşse 1, değilse 0) bir adım yaklaştırır.
    private static void tumunuIlerlet() {
        Iterator<UUID> it = IZLENENLER.iterator();
        while (it.hasNext()) {
            UUID uuid = it.next();

            float mevcut = ILERLEME.getOrDefault(uuid, 0f);
            ONCEKI_ILERLEME.put(uuid, mevcut);

            float hedef = WerewolfScale.istemciAktifMi(uuid) ? 1f : 0f;
            float adim = Mth.clamp(hedef - mevcut, -ILERLEME_HIZI, ILERLEME_HIZI);
            float yeni = mevcut + adim;

            ILERLEME.put(uuid, yeni);

            // Tamamen insana dönmüş VE hedefi de insansa artık takip etmeye gerek yok.
            if (yeni <= 0f && hedef == 0f) {
                it.remove();
                ILERLEME.remove(uuid);
                ONCEKI_ILERLEME.remove(uuid);
            }
        }
    }

    // partialTicks: render metodları zaten bunu parametre olarak veriyor; render metodu dışında
    // (örn. bir mixin içinde) çağırıyorsan Minecraft.getInstance().getFrameTime() kullan.
    // Önceki tick ile bu tick arasında interpolasyon yaparak animasyonun 20 tick/sn'ye
    // takılı kalmayıp ekran FPS'i kadar pürüzsüz görünmesini sağlar.
    public static float getIlerleme(UUID uuid, float partialTicks) {
        float mevcut = ILERLEME.getOrDefault(uuid, 0f);
        float onceki = ONCEKI_ILERLEME.getOrDefault(uuid, mevcut);
        return Mth.lerp(partialTicks, onceki, mevcut);
    }

    // Geriye dönük uyumluluk / hitbox tarafı için: hâlâ anlık (binary) bilgiye ihtiyaç duyan
    // yerler bunu kullanabilir.
    public static boolean kurtAdamMi(UUID uuid) {
        return WerewolfScale.istemciAktifMi(uuid);
    }
}
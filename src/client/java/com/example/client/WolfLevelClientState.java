package com.example.client;

import com.example.WolfNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import java.util.HashSet;
import java.util.Set;

/**
 * Sunucudan gelen KENDİ seviye/yetenek durumumuz (LEVEL_SENKRON).
 * Form/görsel durumu tutan WolfClientState'ten bilerek ayrı: tek sorumluluk.
 *
 * Alanlar sadece istemci ana thread'inde (client.execute içinde) yazılır ve okunur.
 */
public class WolfLevelClientState {

    private static boolean enfekte = false;
    private static int xp = 0;
    private static boolean kaosKilidiAcik = false;
    private static Set<String> yetenekler = new HashSet<>();

    private WolfLevelClientState() {
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(WolfNetworking.LEVEL_SENKRON,
                (client, handler, buf, responseSender) -> {
                    // Paket okuması netty thread'inde yapılır, durum güncellemesi ana thread'de.
                    // Okuma sırası WolfProgression.senkronla ile BİREBİR aynı olmalı.
                    boolean yeniEnfekte = buf.readBoolean();
                    int yeniXp = buf.readVarInt();
                    boolean yeniKaosKilidi = buf.readBoolean();
                    int adet = buf.readVarInt();
                    Set<String> yeniYetenekler = new HashSet<>();
                    for (int i = 0; i < adet; i++) {
                        yeniYetenekler.add(buf.readUtf(32));
                    }

                    client.execute(() -> {
                        enfekte = yeniEnfekte;
                        xp = yeniXp;
                        kaosKilidiAcik = yeniKaosKilidi;
                        yetenekler = yeniYetenekler;
                    });
                });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> sifirla());
    }

    private static void sifirla() {
        enfekte = false;
        xp = 0;
        kaosKilidiAcik = false;
        yetenekler = new HashSet<>();
    }

    public static boolean enfekteMi() {
        return enfekte;
    }

    public static int getXp() {
        return xp;
    }

    public static boolean kaosKilidiAcikMi() {
        return kaosKilidiAcik;
    }

    public static Set<String> getYetenekler() {
        return yetenekler;
    }
}
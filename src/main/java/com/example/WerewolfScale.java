package com.example;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class WerewolfScale {

    public static final float INSAN_BOY = 1.8f;
    public static final float BOY_ARTISI = 0.10f;
    public static final float OLCEK = (INSAN_BOY + BOY_ARTISI) / INSAN_BOY;

    private static final Set<UUID> ISTEMCI_FORMLAR = ConcurrentHashMap.newKeySet();

    private WerewolfScale() {
    }

    public static boolean aktifMi(Player player) {
        if (player.level().isClientSide) {
            return ISTEMCI_FORMLAR.contains(player.getUUID());
        }
        MinecraftServer server = player.getServer();
        return server != null && InfectionData.get(server).isTransformed(player.getUUID());
    }

    public static void istemciAyarla(UUID uuid, boolean donustu) {
        if (donustu) {
            ISTEMCI_FORMLAR.add(uuid);
        } else {
            ISTEMCI_FORMLAR.remove(uuid);
        }
    }

    public static boolean istemciAktifMi(UUID uuid) {
        return ISTEMCI_FORMLAR.contains(uuid);
    }

    public static void istemciTemizle() {
        ISTEMCI_FORMLAR.clear();
    }
}
package com.example;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class InfectionData extends SavedData {

    // Enfekte oyuncuların listesi (kalıcı - damarında kurt kanı olan herkes, dönüşmüş olsun olmasın)
    private final Set<UUID> infectedPlayers = new HashSet<>();

    // Kuluçkadaki oyuncular: UUID -> enfeksiyonun başladığı tick (kalıcı)
    private final Map<UUID, Long> incubatingPlayers = new HashMap<>();

    // Şu an kurt adam formunda olan oyuncular (kalıcı)
    // Böylece sunucu yeniden başlasa/oyuncu tekrar login olsa bile dönüşüm durumu korunur.
    private final Set<UUID> transformedPlayers = new HashSet<>();

    // ================= ENFEKTE OYUNCULAR (damarında kurt kanı var) =================

    public boolean isInfected(UUID uuid) {
        return infectedPlayers.contains(uuid);
    }

    public void addInfected(UUID uuid) {
        infectedPlayers.add(uuid);
        setDirty();
    }

    // ================= KULUÇKADAKİ OYUNCULAR (enfeksiyon süreci devam ediyor) =================

    public boolean isIncubating(UUID uuid) {
        return incubatingPlayers.containsKey(uuid);
    }

    public void startIncubation(UUID uuid, long startTick) {
        incubatingPlayers.put(uuid, startTick);
        setDirty();
    }

    public Long getIncubationStartTick(UUID uuid) {
        return incubatingPlayers.get(uuid);
    }

    public void removeIncubation(UUID uuid) {
        incubatingPlayers.remove(uuid);
        setDirty();
    }

    // Tick döngüsünde tüm kuluçkadaki oyuncuları dolaşmak için (eşzamanlı değişiklikten
    // kaçınmak adına kopyasını döndürüyoruz)
    public Map<UUID, Long> getIncubatingPlayersSnapshot() {
        return new HashMap<>(incubatingPlayers);
    }

    // ================= DÖNÜŞÜM DURUMU (kurt adam formu) =================

    public boolean isTransformed(UUID uuid) {
        return transformedPlayers.contains(uuid);
    }

    public void setTransformed(UUID uuid, boolean transformed) {
        boolean changed = transformed ? transformedPlayers.add(uuid) : transformedPlayers.remove(uuid);
        if (changed) {
            setDirty();
        }
    }

    public Set<UUID> getTransformedPlayersSnapshot() {
        return new HashSet<>(transformedPlayers);
    }

    // ================= KAYDETME / YÜKLEME =================

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag infectedList = new ListTag();
        for (UUID uuid : infectedPlayers) {
            infectedList.add(StringTag.valueOf(uuid.toString()));
        }
        tag.put("infected_players", infectedList);

        ListTag incubatingList = new ListTag();
        for (Map.Entry<UUID, Long> entry : incubatingPlayers.entrySet()) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putString("uuid", entry.getKey().toString());
            entryTag.putLong("start_tick", entry.getValue());
            incubatingList.add(entryTag);
        }
        tag.put("incubating_players", incubatingList);

        ListTag transformedList = new ListTag();
        for (UUID uuid : transformedPlayers) {
            transformedList.add(StringTag.valueOf(uuid.toString()));
        }
        tag.put("transformed_players", transformedList);

        return tag;
    }

    public static InfectionData load(CompoundTag tag) {
        InfectionData data = new InfectionData();

        ListTag infectedList = tag.getList("infected_players", 8); // 8 = String listesi
        for (int i = 0; i < infectedList.size(); i++) {
            data.infectedPlayers.add(UUID.fromString(infectedList.getString(i)));
        }

        ListTag incubatingList = tag.getList("incubating_players", 10); // 10 = CompoundTag listesi
        for (int i = 0; i < incubatingList.size(); i++) {
            CompoundTag entryTag = incubatingList.getCompound(i);
            UUID uuid = UUID.fromString(entryTag.getString("uuid"));
            long startTick = entryTag.getLong("start_tick");
            data.incubatingPlayers.put(uuid, startTick);
        }

        ListTag transformedList = tag.getList("transformed_players", 8); // 8 = String listesi
        for (int i = 0; i < transformedList.size(); i++) {
            data.transformedPlayers.add(UUID.fromString(transformedList.getString(i)));
        }

        return data;
    }

    public static InfectionData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                InfectionData::load,
                InfectionData::new,
                "wolfblood_infection"
        );
    }
}
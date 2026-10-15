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

    // Seviye sınırı: 4 daldaki toplam 30 puanlık yetenek ağacını açabilmek için Lv31
    public static final int MAX_SEVIYE = 31;

    // Enfekte oyuncuların listesi (kalıcı - damarında kurt kanı olan herkes, dönüşmüş olsun olmasın)
    private final Set<UUID> infectedPlayers = new HashSet<>();

    // Kuluçka süresi overworld gameTime ile ölçülür (sunucu tick sayacı restart'ta sıfırlanır).
    public static final long INCUBATION_DURATION_TICKS = 24000;

    // Kuluçkadaki oyuncular: UUID -> tamamlanacağı overworld gameTime (kalıcı)
    private final Map<UUID, Long> incubatingPlayers = new HashMap<>();

    // Eski kayıtlardaki start_tick (sunucu tick sayacı). İlk get() sırasında gameTime'a çevrilir.
    private final Map<UUID, Long> legacyIncubationStartTicks = new HashMap<>();

    // Şu an kurt adam formunda olan oyuncular (kalıcı)
    // Böylece sunucu yeniden başlasa/oyuncu tekrar login olsa bile dönüşüm durumu korunur.
    private final Set<UUID> transformedPlayers = new HashSet<>();

    // Kurt adam tecrübesi (kalıcı). Seviye buradan türetilir, ayrıca saklanmaz.
    private final Map<UUID, Integer> xpMap = new HashMap<>();

    // Açılmış yetenekler: UUID -> yetenek id'leri (kalıcı). Harcanmamış puan türetilir, saklanmaz.
    private final Map<UUID, Set<String>> unlockedSkills = new HashMap<>();

    // Kaos dalının kilidini açmış oyuncular (Kaos İksiri içilince eklenir, kalıcı).
    private final Set<UUID> chaosUnlockedPlayers = new HashSet<>();

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
        return incubatingPlayers.containsKey(uuid) || legacyIncubationStartTicks.containsKey(uuid);
    }

    public void startIncubation(UUID uuid, long completeAtGameTime) {
        incubatingPlayers.put(uuid, completeAtGameTime);
        legacyIncubationStartTicks.remove(uuid);
        setDirty();
    }

    public Long getIncubationCompleteAt(UUID uuid) {
        return incubatingPlayers.get(uuid);
    }

    public void removeIncubation(UUID uuid) {
        incubatingPlayers.remove(uuid);
        legacyIncubationStartTicks.remove(uuid);
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

    // ================= TECRÜBE / SEVİYE =================

    public int getXp(UUID uuid) {
        return xpMap.getOrDefault(uuid, 0);
    }

    public void xpEkle(UUID uuid, int miktar) {
        xpMap.merge(uuid, miktar, Integer::sum);
        setDirty();
    }

    public static int seviyeHesapla(int toplamXp) {
        // Lv2: 50, Lv3: 200, Lv4: 450 ... (seviyeXpEsigi ile birebir tutarlı)
        return Math.min(MAX_SEVIYE, (int) Math.sqrt(toplamXp / 50.0) + 1);
    }

    /** Verilen seviyeye ulaşmak için gereken toplam XP. */
    public static int seviyeXpEsigi(int seviye) {
        return (seviye - 1) * (seviye - 1) * 50;
    }

    public int getSeviye(UUID uuid) {
        return seviyeHesapla(getXp(uuid));
    }

    // ================= YETENEKLER =================

    /** Açılmış yetenek id'lerinin kopyası (kök gibi otomatik açık düğümler burada yoktur). */
    public Set<String> getSkillIds(UUID uuid) {
        Set<String> set = unlockedSkills.get(uuid);
        return set == null ? Set.of() : new HashSet<>(set);
    }

    public boolean unlockSkill(UUID uuid, String id) {
        boolean eklendi = unlockedSkills.computeIfAbsent(uuid, k -> new HashSet<>()).add(id);
        if (eklendi) {
            setDirty();
        }
        return eklendi;
    }

    // ================= KAOS DALI KİLİDİ =================

    public boolean isChaosUnlocked(UUID uuid) {
        return chaosUnlockedPlayers.contains(uuid);
    }

    public void setChaosUnlocked(UUID uuid, boolean unlocked) {
        boolean changed = unlocked ? chaosUnlockedPlayers.add(uuid) : chaosUnlockedPlayers.remove(uuid);
        if (changed) {
            setDirty();
        }
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
            entryTag.putLong("complete_at_game_time", entry.getValue());
            incubatingList.add(entryTag);
        }
        for (Map.Entry<UUID, Long> entry : legacyIncubationStartTicks.entrySet()) {
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

        ListTag xpList = new ListTag();
        for (Map.Entry<UUID, Integer> entry : xpMap.entrySet()) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putString("uuid", entry.getKey().toString());
            entryTag.putInt("xp", entry.getValue());
            xpList.add(entryTag);
        }
        tag.put("xp_players", xpList);

        ListTag skillList = new ListTag();
        for (Map.Entry<UUID, Set<String>> entry : unlockedSkills.entrySet()) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putString("uuid", entry.getKey().toString());
            ListTag ids = new ListTag();
            for (String id : entry.getValue()) {
                ids.add(StringTag.valueOf(id));
            }
            entryTag.put("skills", ids);
            skillList.add(entryTag);
        }
        tag.put("skill_players", skillList);

        ListTag chaosList = new ListTag();
        for (UUID uuid : chaosUnlockedPlayers) {
            chaosList.add(StringTag.valueOf(uuid.toString()));
        }
        tag.put("chaos_unlocked_players", chaosList);

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
            if (entryTag.contains("complete_at_game_time")) {
                data.incubatingPlayers.put(uuid, entryTag.getLong("complete_at_game_time"));
            } else {
                data.legacyIncubationStartTicks.put(uuid, entryTag.getLong("start_tick"));
            }
        }

        ListTag transformedList = tag.getList("transformed_players", 8); // 8 = String listesi
        for (int i = 0; i < transformedList.size(); i++) {
            data.transformedPlayers.add(UUID.fromString(transformedList.getString(i)));
        }

        // Eski dünyalarda aşağıdaki anahtarlar yok; getList boş liste döner, yani sorunsuz yüklenir.
        ListTag xpList = tag.getList("xp_players", 10);
        for (int i = 0; i < xpList.size(); i++) {
            CompoundTag entryTag = xpList.getCompound(i);
            data.xpMap.put(UUID.fromString(entryTag.getString("uuid")), entryTag.getInt("xp"));
        }

        ListTag skillList = tag.getList("skill_players", 10);
        for (int i = 0; i < skillList.size(); i++) {
            CompoundTag entryTag = skillList.getCompound(i);
            Set<String> set = new HashSet<>();
            ListTag ids = entryTag.getList("skills", 8);
            for (int j = 0; j < ids.size(); j++) {
                set.add(ids.getString(j));
            }
            data.unlockedSkills.put(UUID.fromString(entryTag.getString("uuid")), set);
        }

        ListTag chaosList = tag.getList("chaos_unlocked_players", 8);
        for (int i = 0; i < chaosList.size(); i++) {
            data.chaosUnlockedPlayers.add(UUID.fromString(chaosList.getString(i)));
        }

        return data;
    }

    public static InfectionData get(MinecraftServer server) {
        InfectionData data = server.overworld().getDataStorage().computeIfAbsent(
                InfectionData::load,
                InfectionData::new,
                "wolfblood_infection"
        );
        data.migrateLegacyIncubationIfNeeded(server);
        return data;
    }

    private void migrateLegacyIncubationIfNeeded(MinecraftServer server) {
        if (legacyIncubationStartTicks.isEmpty()) {
            return;
        }

        long gameTime = server.overworld().getGameTime();
        int tickCount = server.getTickCount();

        for (Map.Entry<UUID, Long> entry : legacyIncubationStartTicks.entrySet()) {
            long remaining;
            if (tickCount >= entry.getValue()) {
                remaining = Math.max(0L, INCUBATION_DURATION_TICKS - (tickCount - entry.getValue()));
            } else {
                // Restart sonrası eski tick sayacı geçersiz; kuluçkayı dolmuş say.
                remaining = 0L;
            }
            incubatingPlayers.put(entry.getKey(), gameTime + remaining);
        }
        legacyIncubationStartTicks.clear();
        setDirty();
    }
}
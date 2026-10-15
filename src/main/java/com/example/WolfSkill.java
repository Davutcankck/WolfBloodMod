package com.example;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Yetenek ağacı tanımı (veri odaklı). Panel (WolfSkillScreen), sunucu doğrulaması ve puan hesabı BURADAN okur.
 *
 * TEK AĞAÇ, yukarıdan aşağı. Koordinatlar ızgara birimidir (piksel değil): x sağa, y AŞAĞI artar,
 * (0,0) en üstteki kök düğümdür. Çaprazlar 1 birim sağ/sol + 1 birim aşağı olduğu için tam 45 derecedir.
 *
 *            Vahşi Uyanış (0,0)
 *            /                \
 *   Hayvani Çeviklik (-1,1)  Yırtıcı İçgüdü (1,1)
 *            \                /                      ÜST ELMAS (Temel)
 *             Alfa Kurdu (0,2)  <- BÜYÜK MERKEZ DÜĞÜM (Kırmızı gözlü)
 *                   |
 *                   |   (iki birimlik uzun dikey hat)
 *                   |
 *             Kaos Mührü (0,4)  <- mühür kontrol düğümü (satın alınmaz)
 *            /                \
 *   Gölge Adım (-1,5)     Hiçlik Pençesi (1,5)
 *        |                      |                    ALT ELMAS (Kaos)
 *   Avcının Ziyafeti (-1,6)  Kaos Patlaması (1,6)
 *            \                /
 *       Kaosun Enkarnasyonu (0,7)
 *
 * Kurallar:
 *   - id: diske yazılır. Yayına çıktıktan sonra değiştirme (açılmış yetenekler id ile saklanır).
 *     Ağaçtan silinen / yeniden adlandırılan id'ler "bilinmeyen" sayılır ve puana yansımaz (puan iade olur).
 *   - onKosulIdleri: listedeki HEPSİ açık olmalı (VE mantığı).
 *   - maliyet 0 olan düğüm satın alınmaz, diske yazılmaz (kök ve mühür düğümü gibi).
 *   - Mühür düğümü (isMuhur): açık/kapalı durumu yetenek listesinden değil, oyuncunun mühür bilgisinden
 *     (InfectionData.isChaosUnlocked, istemcide WolfLevelClientState.kaosKilidiAcikMi) gelir.
 *     Kaos dalındaki düğümler mühür işlenmeden açılamaz (Dal.KAOS kilidi).
 *   - minSeviye: düğümü açmak için gereken en düşük seviye.
 *   - Puan: Lv2'den itibaren her level 1 puan verir (toplam MAX_SEVIYE - 1).
 */
public enum WolfSkill {

    // ================= ÜST ELMAS: TEMEL DAL (Kök -> Çeviklik/İçgüdü -> Alfa) =================
    VAHSI_UYANIS       ("vahsi_uyanis",        Dal.TEMEL,  0, 0, 0,  1,  "Vahşi Uyanış",         "Kurt kanının damarlardaki uyanışı. Tüm güçlerin başlangıcı."),
    HAYVANI_CEVIKLIK   ("hayvani_ceviklik",    Dal.TEMEL, -1, 1, 1,  2,  "Hayvani Çeviklik",     "Reflekslerin keskinleşir, hareket hızın artar.",                      "vahsi_uyanis"),
    YIRTICI_ICGUDU     ("yirtici_icgudu",      Dal.TEMEL,  1, 1, 1,  2,  "Yırtıcı İçgüdü",       "Avcı duyuların güçlenir, saldırı gücün artar.",                       "vahsi_uyanis"),
    ALFA_KURDU         ("alfa_kurdu",          Dal.TEMEL,  0, 2, 1,  7,  "Alfa Kurdu",           "Sürünün mutlak lideri. Bedenin güçlenir, kadim mühürlere kapı açar.", "hayvani_ceviklik", "yirtici_icgudu"),

    // ================= DARBOĞAZ: MÜHÜR DÜĞÜMÜ (Uzun hat) =================
    KAOS_MUHRU         ("kaos_muhru",          Dal.TEMEL,  0, 4, 0,  1,  "Kaos Mührü",           "Boşluğun kadim mührü. Kaos Mührü işlenerek uyanır.",                  "alfa_kurdu"),

    // ================= ALT ELMAS: KAOS DALI =================
    GOLGE_ADIM         ("golge_adim",          Dal.KAOS,  -1, 5, 1,  8,  "Gölge Adım",           "Saldırılardan gölgelere sığınarak sıyrılma şansı.",                   "kaos_muhru"),
    AVCININ_ZIYAFETI   ("avcinin_ziyafeti",    Dal.KAOS,  -1, 6, 1,  9,  "Avcının Ziyafeti",     "Düşmanlarını katlettiğinde canını yenile.",                           "golge_adim"),
    HICLIK_PENCESI     ("hiclik_pencesi",      Dal.KAOS,   1, 5, 1,  8,  "Hiçlik Pençesi",       "Pençelerin hedefin zırhını kısmen yok sayar.",                        "kaos_muhru"),
    KAOS_PATLAMASI     ("kaos_patlamasi",      Dal.KAOS,   1, 6, 1,  9,  "Kaos Patlaması",       "Vuruşlarında kaotik bir alan hasarı patlaması oluşturur.",             "hiclik_pencesi"),
    KAOSUN_ENKARNASYONU("kaosun_enkarnasyonu", Dal.KAOS,   0, 7, 1, 10,  "Kaosun Enkarnasyonu",  "Kaos formunun mutlak zirvesi. Yıkıcı güç patlaması.",                  "avcinin_ziyafeti", "kaos_patlamasi");

    // ---------------------------------------------------------------------------------

    /** Dal: görsel renk ve mühür kuralı. */
    public enum Dal {
        TEMEL("Temel", false, 0xFFFFD700), // Altın sarısı
        KAOS ("Kaos",  true,  0xFFBA55D3); // Mor

        private final String ad;
        private final boolean muhurGerekli;
        private final int renk;

        Dal(String ad, boolean muhurGerekli, int renk) {
            this.ad = ad;
            this.muhurGerekli = muhurGerekli;
            this.renk = renk;
        }

        public String getAd() {
            return ad;
        }

        public int getRenk() {
            return renk;
        }

        public boolean kilitAcikMi(boolean kaosMuhruVar) {
            return !muhurGerekli || kaosMuhruVar;
        }
    }

    /** Bir yeteneğin o anki durumunun değerlendirmesi. Sunucu ve istemci AYNI sonucu üretir. */
    public enum Sonuc {
        ACILABILIR,
        ZATEN_ACIK,
        KATALIZOR_GEREKLI, // mühür gerekli (ad eski, sunucu kodu bu ada bağlı)
        ON_KOSUL_EKSIK,
        SEVIYE_YETERSIZ,
        PUAN_YOK
    }

    private static final Map<String, WolfSkill> ID_HARITASI = new HashMap<>();

    static {
        for (WolfSkill yetenek : values()) {
            ID_HARITASI.put(yetenek.id, yetenek);
        }
        // Ön koşul id'lerinde yazım hatası varsa oyun açılırken hemen anlaşılsın
        for (WolfSkill yetenek : values()) {
            for (String onId : yetenek.onKosulIdleri) {
                if (!ID_HARITASI.containsKey(onId)) {
                    throw new IllegalStateException("WolfSkill '" + yetenek.id + "': bilinmeyen ön koşul '" + onId + "'");
                }
            }
        }
    }

    private final String id;
    private final Dal dal;
    private final int x;
    private final int y;
    private final int maliyet;
    private final int minSeviye;
    private final String ad;
    private final String aciklama;
    private final String[] onKosulIdleri;

    WolfSkill(String id, Dal dal, int x, int y, int maliyet, int minSeviye,
              String ad, String aciklama, String... onKosulIdleri) {
        this.id = id;
        this.dal = dal;
        this.x = x;
        this.y = y;
        this.maliyet = maliyet;
        this.minSeviye = minSeviye;
        this.ad = ad;
        this.aciklama = aciklama;
        this.onKosulIdleri = onKosulIdleri;
    }

    public String getId() {
        return id;
    }

    public Dal getDal() {
        return dal;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getMaliyet() {
        return maliyet;
    }

    public int getMinSeviye() {
        return minSeviye;
    }

    public String getAd() {
        return ad;
    }

    public String getAciklama() {
        return aciklama;
    }

    /** Mühür kontrol düğümü mü? Bu düğüm satın alınmaz, durumu oyuncunun mühür bilgisinden gelir. */
    public boolean isMuhur() {
        return this == KAOS_MUHRU;
    }

    public List<WolfSkill> getOnKosullar() {
        List<WolfSkill> liste = new ArrayList<>(onKosulIdleri.length);
        for (String onId : onKosulIdleri) {
            liste.add(ID_HARITASI.get(onId)); // static blokta doğrulandı, null olamaz
        }
        return liste;
    }

    /** Açık mı? Maliyeti 0 olan düğümler (kök, mühür düğümü) yetenek listesine bakılmadan açık sayılır. */
    public boolean acikMi(Set<String> acilanlar) {
        return maliyet == 0 || acilanlar.contains(id);
    }

    /** Bilinmeyen id için null döner (eski kayıttan kalmış / silinmiş yetenek olabilir). */
    public static WolfSkill idIle(String id) {
        return ID_HARITASI.get(id);
    }

    /**
     * Harcanmamış yetenek puanı. Ağaçta artık olmayan (bilinmeyen) id'ler harcanan puana sayılmaz.
     */
    public static int kullanilabilirPuan(int toplamXp, Set<String> acilanlar) {
        int harcanan = 0;
        for (String id : acilanlar) {
            WolfSkill yetenek = ID_HARITASI.get(id);
            if (yetenek != null) {
                harcanan += yetenek.maliyet;
            }
        }
        return Math.max(0, InfectionData.seviyeHesapla(toplamXp) - 1 - harcanan);
    }

    /** Bu yetenek şu an açılabilir mi, açılamıyorsa neden? Kontrol sırası öncelik sırasıdır. */
    public static Sonuc degerlendir(WolfSkill yetenek, int toplamXp, Set<String> acilanlar, boolean kaosMuhruVar) {
        if (yetenek.acikMi(acilanlar)) {
            return Sonuc.ZATEN_ACIK;
        }
        if (!yetenek.dal.kilitAcikMi(kaosMuhruVar)) {
            return Sonuc.KATALIZOR_GEREKLI;
        }
        for (WolfSkill on : yetenek.getOnKosullar()) {
            if (!on.acikMi(acilanlar)) {
                return Sonuc.ON_KOSUL_EKSIK;
            }
        }
        if (InfectionData.seviyeHesapla(toplamXp) < yetenek.minSeviye) {
            return Sonuc.SEVIYE_YETERSIZ;
        }
        if (kullanilabilirPuan(toplamXp, acilanlar) < yetenek.maliyet) {
            return Sonuc.PUAN_YOK;
        }
        return Sonuc.ACILABILIR;
    }
}
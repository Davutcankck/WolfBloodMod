package com.example;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Yetenek ağacı tanımı (veri odaklı). Panel, sunucu doğrulaması ve puan hesabı BURADAN okur.
 *
 * Koordinatlar ızgara birimidir (piksel değil) ve HER DAL (sekme) için ayrı bir koordinat uzayıdır:
 *   - x sağa, y AŞAĞI artar. Ağaç yukarıdan aşağı açılır: kök (0,0) en üstte.
 *   - Temel sekme: kök -> orta dal Alfa Kurdu'nda biter, yanlarda isteğe bağlı dallar var.
 *   - Kaos sekmesi: Kaos Mührü en üstte, iki yola ayrılır, altta Kaos Kurdu'nda birleşir.
 *
 * Kurallar:
 *   - id: diske yazılır. Yayına çıktıktan sonra değiştirme (açılmış yetenekler id ile saklanır).
 *     Ağaçtan silinen / yeniden adlandırılan id'ler "bilinmeyen" sayılır ve puana yansımaz (puan iade olur).
 *   - onKosulIdleri: listedeki HEPSİ açık olmalı (VE mantığı). Başka sekmeden ön koşul verilebilir
 *     (örn. Kaos Mührü <- Alfa Kurdu), panelde sekmeler arası çizgi çizilmez.
 *   - maliyet 0 olan düğüm herkese (enfekte) otomatik açıktır, diske yazılmaz (kök gibi).
 *   - minSeviye: düğümü açmak için gereken en düşük seviye.
 *   - Puan: Lv2'den itibaren her level 1 puan verir (toplam MAX_SEVIYE - 1).
 */
public enum WolfSkill {

    // ================= TEMEL SEKME (normal kurt adam) =================
    //            id                      dal         x   y  mal  minLv  ad                 açıklama                                     ön koşullar
    KURT_KANI    ("kurt_kani",           Dal.TEMEL,  0,  0, 0,   1,  "Kurt Kanı",       "Damarlarındaki lanetin kaynağı. Ağacın kökü."),
    KALIN_POST   ("kalin_post",          Dal.TEMEL, -2,  1, 1,   1,  "Kalın Post",      "Taslak: dayanıklılık bonusu (isteğe bağlı).", "kurt_kani"),
    VAHSI_PENCE  ("vahsi_pence",         Dal.TEMEL,  0,  1, 1,   1,  "Vahşi Pençe",     "Taslak: temel saldırı gücü artışı.",          "kurt_kani"),
    KESKIN_DUYU  ("keskin_duyular",      Dal.TEMEL,  2,  1, 1,   1,  "Keskin Duyular",  "Taslak: algı bonusu (isteğe bağlı).",         "kurt_kani"),
    AV_ICGUDUSU  ("av_icgudusu",         Dal.TEMEL,  0,  2, 1,   1,  "Av İçgüdüsü",     "Taslak: hareket ve duyu bonusu.",             "vahsi_pence"),
    ALFA_KURDU   ("alfa_kurdu",          Dal.TEMEL,  0,  3, 1,   7,  "Alfa Kurdu",      "Sürünün lideri. Gelişmiş formların kapısı.",  "av_icgudusu"),

    // ================= KAOS SEKMESİ (Kaos İksiri ile uyanır) =================
    KAOS_MUHRU   ("kaos_muhru",          Dal.KAOS,   0,  0, 1,   7,  "Kaos Mührü",      "Boşluğun mührü. Kaos yeteneklerinin girişi.", "alfa_kurdu"),

    // Sol yol: patlayıcı hasar
    ZIRH_DELME_1 ("kaos_zirh_delme_1",   Dal.KAOS,  -2,  1, 1,   8,  "Boşluk Pençesi I",  "Taslak: vuruşların zırhın bir kısmını yok sayar.", "kaos_muhru"),
    ZIRH_DELME_2 ("kaos_zirh_delme_2",   Dal.KAOS,  -2,  2, 1,   9,  "Boşluk Pençesi II", "Taslak: zırh delme oranı artar.",                  "kaos_zirh_delme_1"),

    // Sağ yol: agresif hayatta kalma
    KAN_HASADI   ("kaos_kan_hasadi",     Dal.KAOS,   2,  1, 1,   8,  "Kan Hasadı",      "Taslak: öldürdükçe can yenilenir.",             "kaos_muhru"),
    KAOS_ADIMI   ("kaos_adimi",          Dal.KAOS,   2,  2, 1,   9,  "Kaos Adımı",      "Taslak: hasardan kaçınma şansı (i-frames).",    "kaos_kan_hasadi"),

    // İki yol aşağıda birleşir: form
    KAOS_KURDU   ("kaos_kurdu",          Dal.KAOS,   0,  3, 1,  10,  "Kaos Kurdu",      "Kaos formunun kilidi. Boşluğun kurdu uyanır.",  "kaos_zirh_delme_2", "kaos_adimi");

    // ---------------------------------------------------------------------------------

    /** Dal = panelde bir sekme. Sıra (ordinal) sekmelerin soldan sağa sırasıdır. */
    public enum Dal {
        //     ad       katalizör  vurgu rengi  çizgi rengi
        TEMEL("Temel",  false,     0xFFD9A441,  0xFFD03030),  // altın vurgu, KIRMIZI çizgiler
        KAOS ("Kaos",   true,      0xFF9B4DDB,  0xFFA24CE0);  // MOR çizgiler

        private final String ad;
        private final boolean katalizorGerekli;
        private final int renk;        // düğüm vurgusu (ARGB)
        private final int cizgiRengi;  // bağlantı çizgileri (ARGB)

        Dal(String ad, boolean katalizorGerekli, int renk, int cizgiRengi) {
            this.ad = ad;
            this.katalizorGerekli = katalizorGerekli;
            this.renk = renk;
            this.cizgiRengi = cizgiRengi;
        }

        public String getAd() {
            return ad;
        }

        public int getRenk() {
            return renk;
        }

        public int getCizgiRengi() {
            return cizgiRengi;
        }

        /** Dal bir katalizör (iksir) gerektiriyorsa, kilidin açık olup olmadığı. Şimdilik tek katalizör Kaos İksiri. */
        public boolean kilitAcikMi(boolean kaosKilidiAcik) {
            return !katalizorGerekli || kaosKilidiAcik;
        }
    }

    /** Bir yeteneğin o anki durumunun değerlendirmesi. Sunucu ve istemci AYNI sonucu üretir. */
    public enum Sonuc {
        ACILABILIR,
        ZATEN_ACIK,
        KATALIZOR_GEREKLI,
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

    public List<WolfSkill> getOnKosullar() {
        List<WolfSkill> liste = new ArrayList<>(onKosulIdleri.length);
        for (String onId : onKosulIdleri) {
            liste.add(ID_HARITASI.get(onId)); // static blokta doğrulandı, null olamaz
        }
        return liste;
    }

    /** Açık mı? Maliyeti 0 olan düğümler (kök) her zaman açıktır. */
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
    public static Sonuc degerlendir(WolfSkill yetenek, int toplamXp, Set<String> acilanlar, boolean kaosKilidiAcik) {
        if (yetenek.acikMi(acilanlar)) {
            return Sonuc.ZATEN_ACIK;
        }
        if (!yetenek.dal.kilitAcikMi(kaosKilidiAcik)) {
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
package com.example;

/**
 * Kurt adam rütbeleri. Rütbe ayrıca saklanmaz, her zaman seviyeden türetilir.
 * Eşikleri/isimleri/renkleri buradan değiştirebilirsin.
 */
public enum WolfRutbe {

    YAVRU(1, "Yavru", 0xFFA0A0A8),
    AVCI(3, "Avcı", 0xFF6FBF73),
    AKINCI(6, "Akıncı", 0xFF4FA3E0),
    GOLGE_AVCISI(10, "Gölge Avcısı", 0xFFBA55D3),
    ALFA(15, "Alfa", 0xFFD9A441),
    SURU_LIDERI(20, "Sürü Lideri", 0xFFE07030),
    KURT_EFENDISI(25, "Kurt Efendisi", 0xFFE04F4F),
    KADIM_KURT(30, "Kadim Kurt", 0xFFFF2222);

    private final int minSeviye;
    private final String ad;
    private final int renk; // ARGB

    WolfRutbe(int minSeviye, String ad, int renk) {
        this.minSeviye = minSeviye;
        this.ad = ad;
        this.renk = renk;
    }

    public String getAd() {
        return ad;
    }

    public int getRenk() {
        return renk;
    }

    public static WolfRutbe seviyeyeGore(int seviye) {
        WolfRutbe sonuc = YAVRU;
        for (WolfRutbe rutbe : values()) {
            if (seviye >= rutbe.minSeviye) {
                sonuc = rutbe;
            }
        }
        return sonuc;
    }
}

package com.example;

/**
 * Kurt adam rütbeleri. Rütbe ayrıca saklanmaz, her zaman seviyeden türetilir.
 * Eşikleri/isimleri/renkleri buradan değiştirebilirsin.
 */
public enum WolfRutbe {

    YAVRU(1, "Yavru", 0xFFA0A0A8),
    AVCI(3, "Avcı", 0xFF6FBF73),
    AKINCI(5, "Akıncı", 0xFF4FA3E0),
    ALFA(7, "Alfa", 0xFFD9A441),
    KURT_EFENDISI(10, "Kurt Efendisi", 0xFFE04F4F);

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

package com.ichi2.anki

import android.os.Build
import org.acra.ACRA
import timber.log.Timber
import java.util.Locale

class EInkDeviceIdentifier {

    data class DeviceInfo(
        private val originalManufacturer: String,
        private val originalModel: String,
    ) {
        val manufacturer: String
            get() = originalManufacturer.lowercase(Locale.ROOT).trim()

        val model: String
            get() = originalModel.lowercase(Locale.ROOT).trim()

        companion object {
            val current: DeviceInfo
                get() = DeviceInfo(
                    originalManufacturer = Build.MANUFACTURER,
                    originalModel = Build.MODEL
                )
        }
    }

    private val knownEInkDevices = setOf(
        //confirmed eink devices
        DeviceInfo("onyx", "FLOW"), // source: https://github.com/Hagb/decryptBooxUpdateUpx/blob/162c29f99bc6b725d1be265cfc17359aa5b55150/BooxKeys.csv#L2
        DeviceInfo("onyx", "FLOWPro"), // source: https://github.com/Hagb/decryptBooxUpdateUpx/blob/162c29f99bc6b725d1be265cfc17359aa5b55150/BooxKeys.csv#L3
        DeviceInfo("onyx", "FLOW.Pro Max"), // source: https://github.com/Hagb/decryptBooxUpdateUpx/blob/162c29f99bc6b725d1be265cfc17359aa5b55150/BooxKeys.csv#L4
        DeviceInfo("onyx", "GALILEO"), // source: https://github.com/Hagb/decryptBooxUpdateUpx/blob/162c29f99bc6b725d1be265cfc17359aa5b55150/BooxKeys.csv#L5
        DeviceInfo("onyx", "Go103"), // source: https://github.com/Hagb/decryptBooxUpdateUpx/blob/162c29f99bc6b725d1be265cfc17359aa5b55150/BooxKeys.csv#L6
        DeviceInfo("onyx", "Go6"), // source: https://github.com/Hagb/decryptBooxUpdateUpx/blob/162c29f99bc6b725d1be265cfc17359aa5b55150/BooxKeys.csv#L7
        DeviceInfo("onyx", "GoColor7"), // source: https://github.com/Hagb/decryptBooxUpdateUpx/blob/162c29f99bc6b725d1be265cfc17359aa5b55150/BooxKeys.csv#L8
        DeviceInfo("onyx", "MC_GULLIVER"), // source: https://github.com/Hagb/decryptBooxUpdateUpx/blob/162c29f99bc6b725d1be265cfc17359aa5b55150/BooxKeys.csv#L9
        DeviceInfo("onyx", "KANT"), // source: https://github.com/Hagb/decryptBooxUpdateUpx/blob/162c29f99bc6b725d1be265cfc17359aa5b55150/BooxKeys.csv#L10
        DeviceInfo("onyx", "Kon_Tiki2"), // source: https://github.com/Hagb/decryptBooxUpdateUpx/blob/162c29f99bc6b725d1be265cfc17359aa5b55150/BooxKeys.csv#L11
        DeviceInfo("onyx", "Leaf"), // source: https://github.com/Hagb/decryptBooxUpdateUpx/blob/162c29f99bc6b725d1be265cfc17359aa5b55150/BooxKeys.csv#L12
        DeviceInfo("onyx", "Leaf2"), // source: https://github.com/Hagb/decryptBooxUpdateUpx/blob/162c29f99bc6b725d1be265cfc17359aa5b55150/BooxKeys.csv#L13
        DeviceInfo("onyx", "Leaf2_P"), // source: https://github.com/Hagb/decryptBooxUpdateUpx/blob/162c29f99bc6b725d1be265cfc17359aa5b55150/BooxKeys.csv#L14
        DeviceInfo("onyx", "Leaf3"), // source: https://github.com/Hagb/decryptBooxUpdateUpx/blob/162c29f99bc6b725d1be265cfc17359aa5b55150/BooxKeys.csv#L15
        DeviceInfo("onyx", "Leaf3C"), // source: https://github.com/Hagb/decryptBooxUpdateUpx/blob/162c29f99bc6b725d1be265cfc17359aa5b55150/BooxKeys.csv#L16
        DeviceInfo("onyx", "LIVINGSTONE"), // source: https://github.com/Hagb/decryptBooxUpdateUpx/blob/162c29f99bc6b725d1be265cfc17359aa5b55150/BooxKeys.csv#L17
        DeviceInfo("onyx", "Lomonosov"), // source: https://github.com/Hagb/decryptBooxUpdateUpx/blob/162c29f99bc6b725d1be265cfc17359aa5b55150/BooxKeys.csv#L18
        DeviceInfo("onyx", "Max2"), // source: https://github.com/Hagb/decryptBooxUpdateUpx/blob/162c29f99bc6b725d1be265cfc17359aa5b55150/BooxKeys.csv#L19
        DeviceInfo("onyx", "Max2Pro"), // source: https://github.com/Hagb/decryptBooxUpdateUpx/blob/162c29f99bc6b725d1be265cfc17359aa5b55150/BooxKeys.csv#L20
        DeviceInfo("onyx", "Max3"), // source: https://github.com/Hagb/decryptBooxUpdateUpx/blob/162c29f99bc6b725d1be265cfc17359aa5b55150/BooxKeys.csv#L21
        DeviceInfo("onyx", "MaxLumi"), // source: https://github.com/Hagb/decryptBooxUpdateUpx/blob/162c29f99bc6b725d1be265cfc17359aa5b55150/BooxKeys.csv#L22
        DeviceInfo("onyx", "MaxLumi2"), // source: https://github.com/Hagb/decryptBooxUpdateUpx/blob/162c29f99bc6b725d1be265cfc17359aa5b55150/BooxKeys.csv#L23
        DeviceInfo("onyx", "Note"), // source: https://github.com/Hagb/decryptBooxUpdateUpx/blob/162c29f99bc6b725d1be265cfc17359aa5b55150/BooxKeys.csv#L24
        DeviceInfo("onyx", "Note2"), // source: https://github.com/Hagb/decryptBooxUpdateUpx/blob/162c29f99bc6b725d1be265cfc17359aa5b55150/BooxKeys.csv#L25
        DeviceInfo("onyx", "Note3"), // source: https://github.com/Hagb/decryptBooxUpdateUpx/blob/162c29f99bc6b725d1be265cfc17359aa5b55150/BooxKeys.csv#L26
        DeviceInfo("onyx", "NoteAir"), // source: https://github.com/Hagb/decryptBooxUpdateUpx/blob/162c29f99bc6b725d1be265cfc17359aa5b55150/BooxKeys.csv#L27
        DeviceInfo("onyx", "NoteAir2"), // source: https://github.com/Hagb/decryptBooxUpdateUpx/blob/162c29f99bc6b725d1be265cfc17359aa5b55150/BooxKeys.csv#L28
        DeviceInfo("onyx", "NoteAir2P"), // source: https://github.com/Hagb/decryptBooxUpdateUpx/blob/162c29f99bc6b725d1be265cfc17359aa5b55150/BooxKeys.csv#L29
        DeviceInfo("onyx", "NoteAir3"), // source: https://github.com/Hagb/decryptBooxUpdateUpx/blob/162c29f99bc6b725d1be265cfc17359aa5b55150/BooxKeys.csv#L30
        DeviceInfo("onyx", "NoteAir3C"), // source: https://github.com/Hagb/decryptBooxUpdateUpx/blob/162c29f99bc6b725d1be265cfc17359aa5b55150/BooxKeys.csv#L31
        DeviceInfo("onyx", "NoteAir4C"), // source: https://github.com/Hagb/decryptBooxUpdateUpx/blob/162c29f99bc6b725d1be265cfc17359aa5b55150/BooxKeys.csv#L32
        DeviceInfo("onyx", "NotePro"), // source: https://github.com/Hagb/decryptBooxUpdateUpx/blob/162c29f99bc6b725d1be265cfc17359aa5b55150/BooxKeys.csv#L33
        DeviceInfo("onyx", "NoteS"), // source: https://github.com/Hagb/decryptBooxUpdateUpx/blob/162c29f99bc6b725d1be265cfc17359aa5b55150/BooxKeys.csv#L34
        DeviceInfo("onyx", "NoteX"), // source: https://github.com/Hagb/decryptBooxUpdateUpx/blob/162c29f99bc6b725d1be265cfc17359aa5b55150/BooxKeys.csv#L35
        DeviceInfo("onyx", "NoteX2"), // source: https://github.com/Hagb/decryptBooxUpdateUpx/blob/162c29f99bc6b725d1be265cfc17359aa5b55150/BooxKeys.csv#L36
        DeviceInfo("onyx", "NoteX3"), // source: https://github.com/Hagb/decryptBooxUpdateUpx/blob/162c29f99bc6b725d1be265cfc17359aa5b55150/BooxKeys.csv#L37
        DeviceInfo("onyx", "NoteX3S"), // source: https://github.com/Hagb/decryptBooxUpdateUpx/blob/162c29f99bc6b725d1be265cfc17359aa5b55150/BooxKeys.csv#L38
        DeviceInfo("onyx", "NoteX3Pro"), // source: https://github.com/Hagb/decryptBooxUpdateUpx/blob/162c29f99bc6b725d1be265cfc17359aa5b55150/BooxKeys.csv#L39
        DeviceInfo("onyx", "Note_YDT"), // source: https://github.com/Hagb/decryptBooxUpdateUpx/blob/162c29f99bc6b725d1be265cfc17359aa5b55150/BooxKeys.csv#L40
        DeviceInfo("onyx", "Nova"), // source: https://github.com/Hagb/decryptBooxUpdateUpx/blob/162c29f99bc6b725d1be265cfc17359aa5b55150/BooxKeys.csv#L41
        DeviceInfo("onyx", "Nova2"), // source: https://github.com/Hagb/decryptBooxUpdateUpx/blob/162c29f99bc6b725d1be265cfc17359aa5b55150/BooxKeys.csv#L42
        DeviceInfo("onyx", "Nova3"), // source: https://github.com/Hagb/decryptBooxUpdateUpx/blob/162c29f99bc6b725d1be265cfc17359aa5b55150/BooxKeys.csv#L43
        DeviceInfo("onyx", "Nova3Color"), // source: https://github.com/Hagb/decryptBooxUpdateUpx/blob/162c29f99bc6b725d1be265cfc17359aa5b55150/BooxKeys.csv#L44
        DeviceInfo("onyx", "Nova5"), // source: https://github.com/Hagb/decryptBooxUpdateUpx/blob/162c29f99bc6b725d1be265cfc17359aa5b55150/BooxKeys.csv#L45
        DeviceInfo("onyx", "NovaAir"), // source: https://github.com/Hagb/decryptBooxUpdateUpx/blob/162c29f99bc6b725d1be265cfc17359aa5b55150/BooxKeys.csv#L46
        DeviceInfo("onyx", "NovaAir2"), // source: https://github.com/Hagb/decryptBooxUpdateUpx/blob/162c29f99bc6b725d1be265cfc17359aa5b55150/BooxKeys.csv#L47
        DeviceInfo("onyx", "NovaAirC"), // source: https://github.com/Hagb/decryptBooxUpdateUpx/blob/162c29f99bc6b725d1be265cfc17359aa5b55150/BooxKeys.csv#L48
        DeviceInfo("onyx", "NovaPlus"), // source: https://github.com/Hagb/decryptBooxUpdateUpx/blob/162c29f99bc6b725d1be265cfc17359aa5b55150/BooxKeys.csv#L49
        DeviceInfo("onyx", "NovaPro"), // source: https://github.com/Hagb/decryptBooxUpdateUpx/blob/162c29f99bc6b725d1be265cfc17359aa5b55150/BooxKeys.csv#L50
        DeviceInfo("onyx", "PadMuAP3"), // source: https://github.com/Hagb/decryptBooxUpdateUpx/blob/162c29f99bc6b725d1be265cfc17359aa5b55150/BooxKeys.csv#L51
        DeviceInfo("onyx", "Page"), // source: https://github.com/Hagb/decryptBooxUpdateUpx/blob/162c29f99bc6b725d1be265cfc17359aa5b55150/BooxKeys.csv#L52
        DeviceInfo("onyx", "Palma"), // source: https://github.com/Hagb/decryptBooxUpdateUpx/blob/162c29f99bc6b725d1be265cfc17359aa5b55150/BooxKeys.csv#L53
        DeviceInfo("onyx", "Palma2"), // source: https://github.com/Hagb/decryptBooxUpdateUpx/blob/162c29f99bc6b725d1be265cfc17359aa5b55150/BooxKeys.csv#L54
        DeviceInfo("onyx", "Poke2"), // source: https://github.com/Hagb/decryptBooxUpdateUpx/blob/162c29f99bc6b725d1be265cfc17359aa5b55150/BooxKeys.csv#L55
        DeviceInfo("onyx", "Poke2Color"), // source: https://github.com/Hagb/decryptBooxUpdateUpx/blob/162c29f99bc6b725d1be265cfc17359aa5b55150/BooxKeys.csv#L56
        DeviceInfo("onyx", "Poke3"), // source: https://github.com/Hagb/decryptBooxUpdateUpx/blob/162c29f99bc6b725d1be265cfc17359aa5b55150/BooxKeys.csv#L57
        DeviceInfo("onyx", "Poke4"), // source: https://github.com/Hagb/decryptBooxUpdateUpx/blob/162c29f99bc6b725d1be265cfc17359aa5b55150/BooxKeys.csv#L58
        DeviceInfo("onyx", "Poke4Lite"), // source: https://github.com/Hagb/decryptBooxUpdateUpx/blob/162c29f99bc6b725d1be265cfc17359aa5b55150/BooxKeys.csv#L59
        DeviceInfo("onyx", "Poke4S"), // source: https://github.com/Hagb/decryptBooxUpdateUpx/blob/162c29f99bc6b725d1be265cfc17359aa5b55150/BooxKeys.csv#L60
        DeviceInfo("onyx", "Poke5"), // source: https://github.com/Hagb/decryptBooxUpdateUpx/blob/162c29f99bc6b725d1be265cfc17359aa5b55150/BooxKeys.csv#L61
        DeviceInfo("onyx", "Poke5P"), // source: https://github.com/Hagb/decryptBooxUpdateUpx/blob/162c29f99bc6b725d1be265cfc17359aa5b55150/BooxKeys.csv#L62
        DeviceInfo("onyx", "Poke5S"), // source: https://github.com/Hagb/decryptBooxUpdateUpx/blob/162c29f99bc6b725d1be265cfc17359aa5b55150/BooxKeys.csv#L63
        DeviceInfo("onyx", "Poke_Pro"), // source: https://github.com/Hagb/decryptBooxUpdateUpx/blob/162c29f99bc6b725d1be265cfc17359aa5b55150/BooxKeys.csv#L64
        DeviceInfo("onyx", "SP_NoteS"), // source: https://github.com/Hagb/decryptBooxUpdateUpx/blob/162c29f99bc6b725d1be265cfc17359aa5b55150/BooxKeys.csv#L65
        DeviceInfo("onyx", "SP_PokeL"), // source: https://github.com/Hagb/decryptBooxUpdateUpx/blob/162c29f99bc6b725d1be265cfc17359aa5b55150/BooxKeys.csv#L66
        DeviceInfo("onyx", "T10C"), // source: https://github.com/Hagb/decryptBooxUpdateUpx/blob/162c29f99bc6b725d1be265cfc17359aa5b55150/BooxKeys.csv#L67
        DeviceInfo("onyx", "Tab10"), // source: https://github.com/Hagb/decryptBooxUpdateUpx/blob/162c29f99bc6b725d1be265cfc17359aa5b55150/BooxKeys.csv#L68
        DeviceInfo("onyx", "Tab10C"), // source: https://github.com/Hagb/decryptBooxUpdateUpx/blob/162c29f99bc6b725d1be265cfc17359aa5b55150/BooxKeys.csv#L69
        DeviceInfo("onyx", "Tab10CPro"), // source: https://github.com/Hagb/decryptBooxUpdateUpx/blob/162c29f99bc6b725d1be265cfc17359aa5b55150/BooxKeys.csv#L70
        DeviceInfo("onyx", "Tab13"), // source: https://github.com/Hagb/decryptBooxUpdateUpx/blob/162c29f99bc6b725d1be265cfc17359aa5b55150/BooxKeys.csv#L71
        DeviceInfo("onyx", "Tab8"), // source: https://github.com/Hagb/decryptBooxUpdateUpx/blob/162c29f99bc6b725d1be265cfc17359aa5b55150/BooxKeys.csv#L72
        DeviceInfo("onyx", "Tab8C"), // source: https://github.com/Hagb/decryptBooxUpdateUpx/blob/162c29f99bc6b725d1be265cfc17359aa5b55150/BooxKeys.csv#L73
        DeviceInfo("onyx", "TabMiniC"), // source: https://github.com/Hagb/decryptBooxUpdateUpx/blob/162c29f99bc6b725d1be265cfc17359aa5b55150/BooxKeys.csv#L74
        DeviceInfo("onyx", "TabUltra"), // source: https://github.com/Hagb/decryptBooxUpdateUpx/blob/162c29f99bc6b725d1be265cfc17359aa5b55150/BooxKeys.csv#L75
        DeviceInfo("onyx", "TabUltraC"), // source: https://github.com/Hagb/decryptBooxUpdateUpx/blob/162c29f99bc6b725d1be265cfc17359aa5b55150/BooxKeys.csv#L76
        DeviceInfo("onyx", "TabUltraCPro"), // source: https://github.com/Hagb/decryptBooxUpdateUpx/blob/162c29f99bc6b725d1be265cfc17359aa5b55150/BooxKeys.csv#L77
        DeviceInfo("onyx", "TabX"), // source: https://github.com/Hagb/decryptBooxUpdateUpx/blob/162c29f99bc6b725d1be265cfc17359aa5b55150/BooxKeys.csv#L78
        DeviceInfo("boyue","rk30sdk"), // source:https://github.com/koreader/android-luajit-launcher/blob/6bba3f4bb4da8073d0f4ea4f270828c8603aa54d/app/src/main/java/org/koreader/launcher/device/DeviceInfo.kt#L203
        DeviceInfo("boeye","rk30sdk"), // source:https://github.com/koreader/android-luajit-launcher/blob/6bba3f4bb4da8073d0f4ea4f270828c8603aa54d/app/src/main/java/org/koreader/launcher/device/DeviceInfo.kt#L203
        DeviceInfo("fidibo","fidibook"),// source:https://github.com/koreader/android-luajit-launcher/blob/6bba3f4bb4da8073d0f4ea4f270828c8603aa54d/app/src/main/java/org/koreader/launcher/device/DeviceInfo.kt#L248
        DeviceInfo("hyread","k06nu"),// source:https://github.com/koreader/android-luajit-launcher/blob/6bba3f4bb4da8073d0f4ea4f270828c8603aa54d/app/src/main/java/org/koreader/launcher/device/DeviceInfo.kt#L256
        DeviceInfo("rockchip","inkpalmplus"),// source:https://github.com/koreader/android-luajit-launcher/blob/6bba3f4bb4da8073d0f4ea4f270828c8603aa54d/app/src/main/java/org/koreader/launcher/device/DeviceInfo.kt#L264
        DeviceInfo("onyx","jdread"),// source:https://github.com/koreader/android-luajit-launcher/blob/6bba3f4bb4da8073d0f4ea4f270828c8603aa54d/app/src/main/java/org/koreader/launcher/device/DeviceInfo.kt#L268
        DeviceInfo("linfiny","ent-13t1"),// source:https://github.com/koreader/android-luajit-launcher/blob/6bba3f4bb4da8073d0f4ea4f270828c8603aa54d/app/src/main/java/org/koreader/launcher/device/DeviceInfo.kt#L272
        DeviceInfo("haoqing","m6"),// source:https://github.com/koreader/android-luajit-launcher/blob/6bba3f4bb4da8073d0f4ea4f270828c8603aa54d/app/src/main/java/org/koreader/launcher/device/DeviceInfo.kt#L276
        DeviceInfo("haoqing","m7"),// source:https://github.com/koreader/android-luajit-launcher/blob/6bba3f4bb4da8073d0f4ea4f270828c8603aa54d/app/src/main/java/org/koreader/launcher/device/DeviceInfo.kt#L280
        DeviceInfo("haoqing","p6"),// source:https://github.com/koreader/android-luajit-launcher/blob/6bba3f4bb4da8073d0f4ea4f270828c8603aa54d/app/src/main/java/org/koreader/launcher/device/DeviceInfo.kt#L284
        DeviceInfo("rockchip","moaanmix7"),// source:https://github.com/koreader/android-luajit-launcher/blob/6bba3f4bb4da8073d0f4ea4f270828c8603aa54d/app/src/main/java/org/koreader/launcher/device/DeviceInfo.kt#L288
        DeviceInfo("onyx","nabukreg_hd"),// source:https://github.com/koreader/android-luajit-launcher/blob/6bba3f4bb4da8073d0f4ea4f270828c8603aa54d/app/src/main/java/org/koreader/launcher/device/DeviceInfo.kt#L296
        DeviceInfo("barnesandnoble","bnrv1000"),// source:https://github.com/koreader/android-luajit-launcher/blob/6bba3f4bb4da8073d0f4ea4f270828c8603aa54d/app/src/main/java/org/koreader/launcher/device/DeviceInfo.kt#L301
        DeviceInfo("barnesandnoble","bnrv1100"),// source:https://github.com/koreader/android-luajit-launcher/blob/6bba3f4bb4da8073d0f4ea4f270828c8603aa54d/app/src/main/java/org/koreader/launcher/device/DeviceInfo.kt#L301
        DeviceInfo("barnesandnoble","bnrv1300"),// source:https://github.com/koreader/android-luajit-launcher/blob/6bba3f4bb4da8073d0f4ea4f270828c8603aa54d/app/src/main/java/org/koreader/launcher/device/DeviceInfo.kt#L301
        DeviceInfo("barnesandnoble","bnrv510"),// source:https://github.com/koreader/android-luajit-launcher/blob/6bba3f4bb4da8073d0f4ea4f270828c8603aa54d/app/src/main/java/org/koreader/launcher/device/DeviceInfo.kt#L306
        DeviceInfo("barnesandnoble","bnrv520"),// source:https://github.com/koreader/android-luajit-launcher/blob/6bba3f4bb4da8073d0f4ea4f270828c8603aa54d/app/src/main/java/org/koreader/launcher/device/DeviceInfo.kt#L306
        DeviceInfo("barnesandnoble","bnrv700"),// source:https://github.com/koreader/android-luajit-launcher/blob/6bba3f4bb4da8073d0f4ea4f270828c8603aa54d/app/src/main/java/org/koreader/launcher/device/DeviceInfo.kt#L306
        DeviceInfo("barnesandnoble","evk_mx6s1"),// source:https://github.com/koreader/android-luajit-launcher/blob/6bba3f4bb4da8073d0f4ea4f270828c8603aa54d/app/src/main/java/org/koreader/launcher/device/DeviceInfo.kt#L307
        DeviceInfo("freescale","bnrv510"),// source:https://github.com/koreader/android-luajit-launcher/blob/6bba3f4bb4da8073d0f4ea4f270828c8603aa54d/app/src/main/java/org/koreader/launcher/device/DeviceInfo.kt#L306
        DeviceInfo("freescale","bnrv520"),// source:https://github.com/koreader/android-luajit-launcher/blob/6bba3f4bb4da8073d0f4ea4f270828c8603aa54d/app/src/main/java/org/koreader/launcher/device/DeviceInfo.kt#L306
        DeviceInfo("freescale","bnrv700"),// source:https://github.com/koreader/android-luajit-launcher/blob/6bba3f4bb4da8073d0f4ea4f270828c8603aa54d/app/src/main/java/org/koreader/launcher/device/DeviceInfo.kt#L306
        DeviceInfo("freescale","evk_mx6s1"),// source:https://github.com/koreader/android-luajit-launcher/blob/6bba3f4bb4da8073d0f4ea4f270828c8603aa54d/app/src/main/java/org/koreader/launcher/device/DeviceInfo.kt#L307
        DeviceInfo("onyx","rk30sdk"),// source:https://github.com/koreader/android-luajit-launcher/blob/6bba3f4bb4da8073d0f4ea4f270828c8603aa54d/app/src/main/java/org/koreader/launcher/device/DeviceInfo.kt#L312
        DeviceInfo("onyx","jdread"),// source:https://github.com/koreader/android-luajit-launcher/blob/6bba3f4bb4da8073d0f4ea4f270828c8603aa54d/app/src/main/java/org/koreader/launcher/device/DeviceInfo.kt#L345
        DeviceInfo("onyx","mc_note4"),// source:https://github.com/koreader/android-luajit-launcher/blob/6bba3f4bb4da8073d0f4ea4f270828c8603aa54d/app/src/main/java/org/koreader/launcher/device/DeviceInfo.kt#L387
        DeviceInfo("rockchip","pubook"),// source:https://github.com/koreader/android-luajit-launcher/blob/6bba3f4bb4da8073d0f4ea4f270828c8603aa54d/app/src/main/java/org/koreader/launcher/device/DeviceInfo.kt#L483
        DeviceInfo("sony","dpt-cp1"),// source:https://github.com/koreader/android-luajit-launcher/blob/6bba3f4bb4da8073d0f4ea4f270828c8603aa54d/app/src/main/java/org/koreader/launcher/device/DeviceInfo.kt#L491
        DeviceInfo("sony","dpt-rp1"),// source:https://github.com/koreader/android-luajit-launcher/blob/6bba3f4bb4da8073d0f4ea4f270828c8603aa54d/app/src/main/java/org/koreader/launcher/device/DeviceInfo.kt#L495
        DeviceInfo("onyx","tagus_pokep"),// source:https://github.com/koreader/android-luajit-launcher/blob/6bba3f4bb4da8073d0f4ea4f270828c8603aa54d/app/src/main/java/org/koreader/launcher/device/DeviceInfo.kt#L499
        DeviceInfo("xiaomi","xiaomi_reader"),// source:https://github.com/koreader/android-luajit-launcher/blob/6bba3f4bb4da8073d0f4ea4f270828c8603aa54d/app/src/main/java/org/koreader/launcher/device/DeviceInfo.kt#L540
        DeviceInfo("barnesandnoble","NOOK"),// source:https://github.com/plotn/coolreader/blob/e5baf0607e678468aa045053ba5f092164aa1dd7/android/src/org/coolreader/crengine/DeviceInfo.java#L192
        DeviceInfo("barnesandnoble","bnrv350"),// source:https://github.com/plotn/coolreader/blob/e5baf0607e678468aa045053ba5f092164aa1dd7/android/src/org/coolreader/crengine/DeviceInfo.java#L192
        DeviceInfo("barnesandnoble","bnrv300"),// source:https://github.com/plotn/coolreader/blob/e5baf0607e678468aa045053ba5f092164aa1dd7/android/src/org/coolreader/crengine/DeviceInfo.java#L193
        DeviceInfo("barnesandnoble","bnrv500"),// source:https://github.com/plotn/coolreader/blob/e5baf0607e678468aa045053ba5f092164aa1dd7/android/src/org/coolreader/crengine/DeviceInfo.java#L193
        DeviceInfo("Viwoods","Viwoods AiPaper"),// source:https://github.com/ankidroid/Anki-Android/issues/17618
        //probably eink-devices
        DeviceInfo("sony","PRS-T"),// source:https://github.com/plotn/coolreader/blob/e5baf0607e678468aa045053ba5f092164aa1dd7/android/src/org/coolreader/crengine/DeviceInfo.java#L201
        DeviceInfo("barnesandnoble","ereader"),// source:https://github.com/koreader/android-luajit-launcher/blob/6bba3f4bb4da8073d0f4ea4f270828c8603aa54d/app/src/main/java/org/koreader/launcher/device/DeviceInfo.kt#L307
        DeviceInfo("artatech","pri"),// source:https://github.com/koreader/android-luajit-launcher/blob/6bba3f4bb4da8073d0f4ea4f270828c8603aa54d/app/src/main/java/org/koreader/launcher/device/DeviceInfo.kt#L260
        DeviceInfo("dns","DNS Airbook EGH"),// source:https://github.com/plotn/coolreader/blob/e5baf0607e678468aa045053ba5f092164aa1dd7/android/src/org/coolreader/crengine/DeviceInfo.java#L216
        DeviceInfo("crema","crema-0710c"),// source:https://github.com/koreader/android-luajit-launcher/blob/6bba3f4bb4da8073d0f4ea4f270828c8603aa54d/app/src/main/java/org/koreader/launcher/device/DeviceInfo.kt#L236
        DeviceInfo("crema","crema-0670c")// source:https://github.com/koreader/android-luajit-launcher/blob/6bba3f4bb4da8073d0f4ea4f270828c8603aa54d/app/src/main/java/org/koreader/launcher/device/DeviceInfo.kt#L240
    )

    private val eInkManufacturersList= setOf(
        "onyx",
        "boyue",
        "boeye",
        "fidibo",
        "hyread",
        "rockchip",
        "linfiny",
        "haoqing",
        "sony",
        "barnesandnoble",
        "freescale",
        "Viwoods",
        "artatech",
        "dns",
        "crema",
        "kindle",
        "bigme"
    )

    /**
     * @return `true` if a match is found, `false` otherwise.
     */
    // Checks if the device has an E-Ink display by matching its manufacturer and model.
    fun isEInkDevice(): Boolean {
        val currentDevice = DeviceInfo.current
        Timber.v("Checking device: $currentDevice")

        var isExactMatch = false
        var isPartialMatch = false

        // Check if the device is an exact match or a partial match.
        for (device in knownEInkDevices) {
            // Check if the device is an exact match.
            if (currentDevice.manufacturer == device.manufacturer &&
                currentDevice.model == device.model) {
                isExactMatch = true
                break
            }
            // Check if the device is a partial match. Partial matches are detected using substring matching.
            if ((currentDevice.manufacturer.contains(device.manufacturer) ||
                        device.manufacturer.contains(currentDevice.manufacturer)) &&
                (currentDevice.model.contains(device.model) ||
                        device.model.contains(currentDevice.model))) {
                isPartialMatch = true
            }
        }

        if (isExactMatch) {
            Timber.d("Confirmed E-ink device: $currentDevice")
            return true
        }
        //if the device is a partial match or if the manufacturer is in the list of known E-ink manufacturers then report it
        if (isPartialMatch || eInkManufacturersList.contains(currentDevice.manufacturer)) {
            Timber.w("Potential E-ink device: $currentDevice")
            ACRA.errorReporter.handleSilentException(Exception("Potential E-ink device: $currentDevice"))
        }

        return false
    }
}
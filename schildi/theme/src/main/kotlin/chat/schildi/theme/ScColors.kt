package chat.schildi.theme

import androidx.compose.ui.graphics.Color

internal object ScColors {
    val colorAccentGreen = Color(0xff8bc34a)
    val colorAccentRed = Color(0xfff44336)
    val colorAccentOrange = Color(0xffff5722)
    val colorAccentBlueLight = Color(0xff03a9f4)
    val colorAccentBlue = Color(0xff2196f3)
    val colorAccentLime = Color(0xffcddc39)
    val colorWhite = Color(0xffffffff)
    val colorWhite_fa = Color(0xfffafafa)
    val colorWhite_f5 = Color(0xfff5f5f5)
    val colorWhite_ee = Color(0xffeeeeee)
    val colorWhite_e0 = Color(0xffe0e0e0)
    val colorWhiteAlpha_b3 = Color(0xb3ffffff)
    val colorWhiteAlpha_80 = Color(0x80ffffff)
    val colorWhiteAlpha_1f = Color(0x1fffffff)
    val colorBlack = Color(0xff000000)
    val colorBlackAlpha_de = Color(0xde000000)
    val colorBlackAlpha_8a = Color(0x8a000000)
    val colorBlackAlpha_80 = Color(0x80000000)
    val colorBlackAlpha_61 = Color(0x61000000)
    val colorBlackAlpha_4c = Color(0x4c000000)
    val colorBlackAlpha_1f = Color(0x1f000000)
    val colorBlackAlpha_10 = Color(0x10000000)
    val colorGray_12 = Color(0xff121212)
    val colorGray_21 = Color(0xff212121)
    val colorGray_30 = Color(0xff303030)
    val colorGray_42 = Color(0xff424242)
    val colorGray_61 = Color(0xff616161)
    val colorGray_73 = Color(0xff737373)
}

object ScPowerLevelColors {
    val pl_100 = Color(0xfff44336)
    val pl_95 = Color(0xffff5722)
    val pl_51 = Color(0xff03a9f4)
    val pl_50 = Color(0xff2196f3)
    val pl_1 = Color(0xffcddc39)
    val pl_0 = Color(0xff8bc34a)
    val pl_null = pl_0
}

object ScBrandingColors {
    val onboardingGradientLight = listOf(
        //Color(0xffE3F2FD), Color(0xff90CAF9)
        Color(0xffffffff), Color(0xffe2f0d2)
    )
    val onboardingGradientDark = listOf(
        Color(0xff0a5c7c), Color(0xff001a2a)
    )
    val onboardingIconBgColors = listOf(
        Color(0xff0a5c7c), Color(0xff001a2a)
        //Color(0xff0D47A1), Color(0xff06234f)
    )
    val onboardingLogoOutlineDark = ScColors.colorWhiteAlpha_b3
}

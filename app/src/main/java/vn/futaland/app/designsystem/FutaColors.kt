package vn.futaland.app.designsystem

import androidx.compose.ui.graphics.Color

/**
 * Web Design Tokens matching `bds-clone` (Tailwind / CSS Variables) exactly.
 */
object FutaColors {
    // Surfaces & Backgrounds
    val PageBg = Color(0xFFFFFFFF)          // #FFFFFF pure white canvas background
    val CardBg = Color(0xFFFFFFFF)          // #FFFFFF pure white card background
    val CardBorder = Color(0xFFE9E2D5)      // #E9E2D5 sandy beige card border
    val CardBorderWeb = Color(0xFFE1D9CB)   // #E1D9CB web apartment card border
    val PeachBorder = Color(0xFFFED7AA)     // #FED7AA peach border for primary summary cards
    val LightBlueBorder = Color(0xFFDFE6ED) // #DFE6ED cool card border
    val RowDivider = Color(0xFFEEE9DF)      // #EEE9DF row divider
    val PanelDivider = Color(0xFFEDF1F5)    // #EDF1F5 panel divider

    // Typography
    val Navy = Color(0xFF061D3D)            // #061D3D deep navy ink for headers/titles
    val PanelTitle = Color(0xFF000E24)      // #000E24 deepest black-navy for panel titles
    val Slate = Color(0xFF5D6570)           // #5D6570 cool slate for labels
    val Muted = Color(0xFF625B50)           // #625B50 warm gray for subtext
    val Body = Color(0xFF123355)            // #123355 navy body text
    val SubLabel = Color(0xFF52647A)        // #52647A

    // Brand Colors
    val BrandGreen = Color(0xFF207446)      // #207446 FUTA Emerald Green (Primary)
    val BrandGreenDark = Color(0xFF064A2B)  // #064A2B deep forest green
    val BrandOrange = Color(0xFFF97316)     // #F97316 primary FUTA Orange
    val PriceAmber = Color(0xFFE08A11)      // #E08A11 amber for price highlight

    // Tinted Accents
    val CreamBg = Color(0xFFFFF7ED)         // #FFF7ED warm cream (call button / spec icons)
    val MintBg = Color(0xFFECFDF5)          // #ECFDF5 soft mint (chat button / badges)
    val TabBg = Color(0xFFE7EEF5)           // #E7EEF5 media tab container
    val RedPdfBg = Color(0xFFFF7F0)         // #FFF1F0 legal document icon bg
    val RedPdf = Color(0xFFDF5D57)           // #DF5D57 legal document icon
    val BlueAccent = Color(0xFF0068FF)      // #0068FF Zalo / Secondary blue
}

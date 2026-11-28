package io.github.dreadnought0.feedclean.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The locked "frosted glass" palette: a grey-blue gradient background, translucent white cards with a white
 * hairline border, dark teal ([Accent]) for selected states and primary buttons, amber for "Blocked" and for
 * "Pause everything" while it is on, slate for "Paused", and a muted red for destructive actions.
 */
object Glass {
    // Text
    val Ink = Color(0xFF1D2326)
    val Muted = Color(0xFF5F6B70)
    val Faint = Color(0xFF7C898D)

    // Accent (selected states, primary buttons, switches, nav selection)
    val Accent = Color(0xFF1A5E6C)
    val AccentWash = Color(0x241A5E6C)

    /** Lighter teal for charts, meters and icon glyphs. */
    val Teal = Color(0xFF2F7F8F)

    /** Destructive actions and errors. */
    val Red = Color(0xFF9A4A44)
    /** Outline for destructive actions and for Setup when accessibility access is missing. */
    val RedBorder = Red.copy(alpha = 0.45f)

    // Amber: Blocked, and Pause everything while on
    val Amber = Color(0xFFD79A2B)
    val AmberStrong = Color(0xFFC48A24)
    val AmberInk = Color(0xFF5E3B04)
    val AmberText = Color(0xFF7A4D08)
    val AmberFill = Color(0x57E2A330) // ~.34
    val AmberWash = Color(0x3DE2A330) // ~.24
    val AmberBorder = Color(0xA6B07618)
    val AmberBorderSoft = Color(0x6BB07618)

    // Slate: Paused
    val Slate = Color(0xFF6F8187)
    val SlateInk = Color(0xFF2D4046)
    val SlateBar = Color(0xFF7B8D93)

    // Glass surfaces
    val Card = Color(0x4DFFFFFF) // .30
    val CardStrong = Color(0x75FFFFFF) // .46
    val Nest = Color(0x66FFFFFF) // .40
    val Border = Color(0x9EFFFFFF) // .62
    val Divider = Color(0x8CFFFFFF) // .55
    val Track = Color(0x8CFFFFFF) // unchecked switch track
    val TrackBorder = Color(0xCCFFFFFF)

    /** Opaque frosted surface for dialogs, sheets and menus (they sit over the scrim, not the gradient). */
    val Sheet = Color(0xFFF1F5F6)

    // Background gradient (165°) and soft light blobs
    val BgTop = Color(0xFFC9D3D6)
    val BgMid = Color(0xFFC0CCD0)
    val BgBottom = Color(0xFFB9C7CC)
    val BlobWhite = Color(0x99FFFFFF)
    val BlobTeal = Color(0x422F7F8F)
    val BlobSage = Color(0x4D789696)

    /** Passing setup-checklist items. */
    val Ok = Accent
}

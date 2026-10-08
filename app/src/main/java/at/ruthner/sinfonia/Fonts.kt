package at.ruthner.sinfonia

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight

// Cormorant Garamond (Titel) und Inter (Text), beide SIL Open Font License, als Variable Fonts.
@OptIn(ExperimentalTextApi::class)
private fun variable(res: Int, w: Int) =
    Font(res, FontWeight(w), FontStyle.Normal, variationSettings = FontVariation.Settings(FontWeight(w), FontStyle.Normal))

val Display = FontFamily(
    variable(R.font.cormorant, 400), variable(R.font.cormorant, 500),
    variable(R.font.cormorant, 600), variable(R.font.cormorant, 700),
)

val Body = FontFamily(
    variable(R.font.inter, 400), variable(R.font.inter, 500),
    variable(R.font.inter, 600), variable(R.font.inter, 700),
)

package io.github.diamondrubinny.scary.ui.home

import androidx.annotation.StringRes
import io.github.diamondrubinny.scary.R
import io.github.diamondrubinny.scary.core.model.Genre
import io.github.diamondrubinny.scary.core.model.Subgenre

@StringRes
fun Genre.labelRes(): Int = when (this) {
    Genre.HORROR -> R.string.genre_horror
    Genre.THRILLER -> R.string.genre_thriller
}

@StringRes
fun Subgenre.labelRes(): Int = when (this) {
    Subgenre.PSYCHOLOGICAL -> R.string.subgenre_psychological
    Subgenre.SUPERNATURAL -> R.string.subgenre_supernatural
    Subgenre.POSSESSION -> R.string.subgenre_possession
    Subgenre.GHOST -> R.string.subgenre_ghost
    Subgenre.SLASHER -> R.string.subgenre_slasher
    Subgenre.CREATURE -> R.string.subgenre_creature
    Subgenre.FOLK -> R.string.subgenre_folk
    Subgenre.SOCIAL -> R.string.subgenre_social
    Subgenre.FOUND_FOOTAGE -> R.string.subgenre_found_footage
    Subgenre.SURVIVAL -> R.string.subgenre_survival
    Subgenre.MYSTERY -> R.string.subgenre_mystery
    Subgenre.CRIME -> R.string.subgenre_crime
}

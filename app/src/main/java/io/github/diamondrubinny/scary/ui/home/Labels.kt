// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.ui.home

import androidx.annotation.StringRes
import io.github.diamondrubinny.scary.R
import io.github.diamondrubinny.scary.core.catalog.Mood
import io.github.diamondrubinny.scary.core.catalog.Season
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
    Subgenre.ZOMBIE -> R.string.subgenre_zombie
    Subgenre.VAMPIRE -> R.string.subgenre_vampire
    Subgenre.BODY -> R.string.subgenre_body
    Subgenre.HAUNTED_HOUSE -> R.string.subgenre_haunted_house
}

fun Mood.labelRes(): Int = when (this) {
    Mood.GENTLE -> R.string.mood_gentle
    Mood.MIND_GAMES -> R.string.mood_mind_games
    Mood.GHOSTS -> R.string.mood_ghosts
    Mood.BLOOD -> R.string.mood_blood
    Mood.SHORT -> R.string.mood_short
    Mood.SLOW_BURN -> R.string.mood_slow_burn
    Mood.CLASSICS -> R.string.mood_classics
    Mood.RELENTLESS -> R.string.mood_relentless
}

fun Season.titleRes(): Int = when (this) {
    Season.HALLOWEEN -> R.string.row_halloween
    Season.FRIDAY_13TH -> R.string.row_friday_13th
}

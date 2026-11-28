// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core.catalog

import io.github.diamondrubinny.scary.core.model.Movie
import io.github.diamondrubinny.scary.core.model.Catalog
import io.github.diamondrubinny.scary.core.model.Language
import io.github.diamondrubinny.scary.core.model.MovieCollection

/**
 * The library the app shows, made from the film feed (docs/CATALOG_REBUILD_PLAN.md): the feed's films, each with the
 * fear level the hand-picked library once gave it ([LegacyLibrary]; none for any other film, and only a film with one
 * counts as curated), and the feed's rows under
 * names the app holds here. The feed carries row ids only, so a file from the network can never put its own words on
 * the home screen; a row whose id this version has no name for is left out.
 */
object FeedCatalog {
    private class Name(val he: String, val en: String, val pt: String, val ru: String, val ar: String)

    // In the feed's own order; the ids are the app's categories' (core Categories), so each row goes on from Cinemeta.
    private val names: Map<String, Name> = linkedMapOf(
        "new_nightmares" to Name("סיוטים חדשים", "New nightmares", "Novos pesadelos", "Новые кошмары", "كوابيس جديدة"),
        "popular" to Name("כולם צופים עכשיו", "Everyone is watching", "Todo mundo está vendo", "Сейчас смотрят все", "الجميع يشاهد الآن"),
        "max_fear" to Name("הכי מדורגים", "The highest rated", "Os mais bem avaliados", "С самым высоким рейтингом", "الأعلى تقييمًا"),
        "slashers" to Name("סכינים ומסכות", "Masks and blades", "Máscaras e lâminas", "Маски и клинки", "أقنعة وشفرات"),
        "supernatural" to Name("רוחות, שדים ודיבוקים", "Ghosts, demons and possession", "Fantasmas, demônios e possessão", "Призраки, демоны и одержимость", "أشباح وشياطين ومسّ"),
        "haunted_houses" to Name("בתים רדופים", "Haunted houses", "Casas assombradas", "Дома с привидениями", "بيوت مسكونة"),
        "zombies" to Name("זומבים", "The dead walk", "Os mortos andam", "Мертвецы идут", "الموتى يمشون"),
        "vampires" to Name("ערפדים", "Vampires", "Vampiros", "Вампиры", "مصاصو الدماء"),
        "creatures" to Name("יצורים ומפלצות", "Creatures and monsters", "Criaturas e monstros", "Твари и чудовища", "مخلوقات ووحوش"),
        "psychological_horror" to Name("אימה פסיכולוגית", "Psychological horror", "Terror psicológico", "Психологический хоррор", "رعب نفسي"),
        "found_footage" to Name("פוטג' שנמצא", "Found footage", "Found footage: gravações encontradas", "Найденная плёнка", "لقطات عُثر عليها"),
        "horror_comedy" to Name("צוחקים ומפחדים", "Laughing and screaming", "Rindo e gritando", "Смешно и страшно", "نضحك ونصرخ"),
        "classics" to Name("קלאסיקות שאסור לפספס", "Classics you can't skip", "Clássicos imperdíveis", "Классика, которую нельзя пропустить", "كلاسيكيات لا تُفوَّت"),
        "thrillers" to Name("מותחנים אפלים", "Dark thrillers", "Suspenses sombrios", "Мрачные триллеры", "أفلام إثارة قاتمة"),
        "series_must" to Name("סדרות אימה שחובה לראות", "Horror series you can't skip", "Séries de terror imperdíveis", "Хоррор-сериалы, которые нельзя пропустить", "مسلسلات رعب لا تُفوَّت"),
    )

    /** The ids of the rows this version can show, in home-screen order. */
    val rowIds: List<String> get() = names.keys.toList()

    /**
     * The library made of [feed]. With a [day] (days since 1970 in the viewer's time), each row begins where that day
     * turns it to (core DailyRotation); without one, the rows are as the feed gives them.
     */
    fun catalog(feed: FilmFeed.Result, legacy: LegacyLibrary = LegacyLibrary.EMPTY, day: Long? = null): Catalog {
        // A film with a fear level is one somebody watched and rated by hand: it is "curated", which is what tonight's
        // pick, the moods, family night and the time planner choose from. No other film of the feed is.
        val movies = feed.movies.map { movie -> legacy.fear(movie.imdbId)?.let { movie.copy(fear = it, curated = true) } ?: movie }
        val collections = feed.rows.mapNotNull { row ->
            names[row.id]?.let { name ->
                MovieCollection(
                    id = row.id,
                    titleHe = name.he,
                    titleEn = name.en,
                    movieIds = if (day == null) row.movieIds else DailyRotation.turned(row.movieIds, row.id, day),
                    titles = mapOf(Language.PT.code to name.pt, Language.RU.code to name.ru, Language.AR.code to name.ar),
                )
            }
        }
        return Catalog(version = FilmFeed.SUPPORTED_VERSION, movies = movies, collections = collections)
    }

    /**
     * [shown], a film as an online row brought it (Cinemeta, TMDB), with what the feed knows of the same film ([own]):
     * its names in the viewer's languages, its subgenres, its fear level and that it is one of the library's own.
     * An online row carries none of these, and a film that reached the screen through one would otherwise show its
     * English name alone and stay out of the moods, the decades and tonight's pick. What the online row gave
     * (artwork, cast, a translation of its own) is kept.
     */
    fun withOwnFields(shown: Movie, own: Movie): Movie = shown.copy(
        titleHe = shown.titleHe.ifBlank { own.titleHe },
        titles = own.titles + shown.titles.filterValues(String::isNotBlank),
        subgenres = shown.subgenres.ifEmpty { own.subgenres },
        fear = shown.fear ?: own.fear,
        curated = shown.curated || own.curated,
        tags = shown.tags.ifEmpty { own.tags },
        director = shown.director.ifBlank { own.director },
        runtimeMin = shown.runtimeMin.takeIf { it > 0 } ?: own.runtimeMin,
        synopsisHe = shown.synopsisHe.ifBlank { own.synopsisHe },
        synopsisEn = shown.synopsisEn.ifBlank { own.synopsisEn },
        synopses = own.synopses + shown.synopses.filterValues(String::isNotBlank),
        imdbRating = shown.imdbRating ?: own.imdbRating,
    )
}

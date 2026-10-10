package com.kinetica.keyboard.engine

import com.kinetica.keyboard.engine.models.StreamId
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Integration checks against the real bundled assets: memory budget, load
 * sanity, and end-to-end decode quality on the full 46k-word dictionary.
 * Unit tests run with the module directory as working dir, so the assets are
 * reachable relatively; skipped (not failed) if the layout ever changes.
 */
class RealDictionaryTest {

    private fun assetPath(name: String): Path {
        val direct = Paths.get("src/main/assets/dictionaries/$name")
        if (Files.exists(direct)) return direct
        return Paths.get("app/src/main/assets/dictionaries/$name")
    }

    private fun loadTrie(): Trie {
        val p = assetPath("en_wordlist.txt")
        assumeTrue("wordlist asset not found", Files.exists(p))
        return Files.newBufferedReader(p).use { DictionaryLoader.loadWordlist(it) }
    }

    private fun loadTurkishDictionary(): LoadedDictionary {
        val p = assetPath("tr_wordlist.txt")
        assumeTrue("Turkish wordlist asset not found", Files.exists(p))
        val rows = Files.newBufferedReader(p).use { it.lineSequence().count() }
        // CI generates the 50k-source asset before running these tests. A checkout's
        // intentionally small seed dictionary is not a meaningful quality baseline.
        assumeTrue("Run tools/generate_assets.py --lang tr before the Turkish quality baseline", rows >= 30_000)
        return Files.newBufferedReader(p).use {
            DictionaryLoader.load(it, alphabet = Alphabet.TURKISH)
        }
    }

    @Test
    fun fullDictionaryLoadsWithinMemoryBudget() {
        val trie = loadTrie()
        assertTrue("word count ${trie.wordCount}", trie.wordCount >= 30_000)
        // The whole-engine budget is 8 MB; the trie itself must stay well under.
        assertTrue("trie bytes ${trie.sizeBytes()}", trie.sizeBytes() < 4 * 1024 * 1024)
        for (w in listOf("the", "something", "keyboard", "hello")) {
            assertTrue("missing $w", trie.contains(w))
        }
    }

    @Test
    fun bigramsLoadAndBoost() {
        val trie = loadTrie()
        val p = assetPath("en_bigrams.txt")
        assumeTrue("bigram asset not found", Files.exists(p))
        val table = Files.newBufferedReader(p).use { DictionaryLoader.loadBigrams(it, trie) }
        assertTrue("bigram count ${table.size}", table.size > 50_000)
        assertTrue("table bytes ${table.sizeBytes()}", table.sizeBytes() < 4 * 1024 * 1024)
        val boost = table.multiplier(trie.nodeFor("of"), trie.nodeFor("the"))
        assertTrue("of->the boost $boost", boost > 1.5f)
    }

    @Test
    fun realDictionarySwipeDecode() {
        val trie = loadTrie()
        val g = TestData.qwertyGeometry()
        val predictor = WordPredictor(trie, BigramTable.EMPTY, g)

        val result = predictor.decode(
            listOf(TestData.swipe("something", g, 0, 600)), emptyList(),
        )
        assertTrue(result.isNotEmpty())
        assertEquals("something", result[0].word)

        val hello = predictor.decode(
            listOf(TestData.swipe("helo", g, 0, 400)), emptyList(),
        )
        assertTrue(hello.map { it.word }.contains("hello"))
    }


    private data class TurkishSwipeTarget(val group: String, val word: String, val ordinal: Int)

    private data class SwipeScore(var total: Int = 0, var top1: Int = 0, var top3: Int = 0) {
        fun record(word: String, candidates: List<String>) {
            total++
            if (candidates.firstOrNull() == word) top1++
            if (word in candidates.take(3)) top3++
        }
    }

    private val turkishSwipeGroups = linkedMapOf(
        "core" to listOf(
            "merhaba", "güzel", "bugün", "ev", "bir", "ben", "çok", "şimdi",
            "neden", "nasıl", "teşekkür", "istiyorum", "biliyorum", "arkadaşım",
            "şaka", "çabuk", "düşündüm", "göz", "müzik", "doğru",
        ),
        "short-words" to listOf(
            "tamam", "evet", "hayır", "haydi", "belki", "sonra", "önce", "burada",
            "orada", "nerede", "herkes", "zaman", "insan", "çocuk", "kadın",
            "erkek", "aile", "anne", "baba", "kardeş",
        ),
        "turkish-letters" to listOf(
            "çalışmak", "öğretmen", "öğrenci", "şehir", "güneş", "yağmur", "soğuk",
            "sıcak", "küçük", "büyük", "ışık", "ağaç", "şarkı", "kahvaltı", "düzgün",
            "yanlış", "değişiklik", "öğrenmek", "güçlü", "mümkün",
        ),
        "repeated-letters" to listOf(
            "araba", "memnun", "kahkaha", "teşekkürler", "düşünmek", "gerçekten",
            "herhangi", "tekrar", "elektrik", "matematik", "papatya", "başarı",
            "ananas", "susuz", "babaanne", "görüşürüz", "çocuklar", "arkadaşlar",
            "sarımsak", "güldürmek",
        ),
        "long-and-inflected" to listOf(
            "çalışıyorum", "konuşuyorum", "gidiyorum", "geliyorum", "yapıyorum",
            "seviyorum", "düşünüyorum", "anlamıyorum", "bekliyorum", "kullanıyorum",
            "konuşuyorsunuz", "arkadaşlarımız", "kitapları", "evimize", "arabada",
            "kitabını", "çocukların", "sorumluluk", "değiştirmek", "oluşturmak",
        ),
        "similar-paths" to listOf(
            "masa", "kasa", "yasa", "kaza", "kedi", "dedi", "yeni", "yeri",
            "gelen", "giden", "kitap", "hitap", "sorun", "sorum", "söz", "yazı",
            "yazma", "bile", "bilek", "dolu",
        ),
    )

    private fun availableTurkishSwipeTargets(trie: Trie): List<TurkishSwipeTarget> {
        val requested = turkishSwipeGroups.flatMap { (group, words) ->
            words.map { group to it }
        }
        val available = requested.filter { (_, word) ->
            trie.contains(AccentFolder.fold(word, Alphabet.TURKISH))
        }
        val missing = requested.filterNot { (_, word) ->
            trie.contains(AccentFolder.fold(word, Alphabet.TURKISH))
        }
        println(
            "TR_SWIPE_DATASET requested=${requested.size} available=${available.size} " +
                "missing=${missing.size} words=${missing.joinToString(",") { it.second }}"
        )
        for ((group, words) in turkishSwipeGroups) {
            val count = available.count { it.first == group }
            println("TR_SWIPE_DATASET_GROUP group=$group available=$count requested=${words.size}")
        }
        assertTrue(
            "Too few Turkish swipe targets are present in the generated dictionary: " +
                "${available.size}/${requested.size}",
            available.size >= 60,
        )
        return available.mapIndexed { index, (group, word) -> TurkishSwipeTarget(group, word, index) }
    }

    private fun evaluateTurkishSwipeCondition(
        predictor: WordPredictor,
        geometry: KeyboardGeometry,
        targets: List<TurkishSwipeTarget>,
        condition: String,
    ): Map<String, SwipeScore> {
        val scores = linkedMapOf("all" to SwipeScore())
        for (group in turkishSwipeGroups.keys) scores[group] = SwipeScore()
        for (target in targets) {
            // 35–94 ms per character. Duration varies by target, but is identical
            // across conditions for a given word so condition comparisons are paired.
            val duration = target.word.length.toLong() * (35L + (target.ordinal * 29L % 60L))
            val token = when (condition) {
                "clean-centerline" -> TestData.swipe(
                    target.word, geometry, t0 = 0, durMs = duration,
                )
                "overshoot-0.25" -> TestData.sloppySwipe(
                    target.word, geometry, t0 = 0, durMs = duration, overshootKw = 0.25f,
                )
                "jitter-0.12-overshoot-0.45" -> TestData.jitteredSwipe(
                    target.word, geometry, t0 = 0, durMs = duration,
                    overshootKw = 0.45f, jitterKw = 0.12f,
                    seed = 0x5EED + target.ordinal * 37,
                )
                else -> error("Unknown swipe benchmark condition: $condition")
            }
            val candidates = predictor.decode(listOf(token), emptyList()).map { it.word }
            val rank = candidates.indexOf(target.word)
            if (rank != 0) {
                val rankLabel = if (rank < 0) "not-found" else (rank + 1).toString()
                println(
                    "TR_SWIPE_MISS condition=$condition group=${target.group} " +
                        "target=${target.word} rank=$rankLabel " +
                        "top5=${candidates.take(5).joinToString(",")}"
                )
            }
            scores.getValue("all").record(target.word, candidates)
            scores.getValue(target.group).record(target.word, candidates)
        }
        for ((group, score) in scores) {
            val pct1 = if (score.total == 0) 0.0 else 100.0 * score.top1 / score.total
            val pct3 = if (score.total == 0) 0.0 else 100.0 * score.top3 / score.total
            val prefix = if (group == "all") "TR_SWIPE_BASELINE" else "TR_SWIPE_BASELINE_GROUP"
            println(
                "$prefix dataset=${score.total} condition=$condition group=$group " +
                    "top1=${score.top1}/${score.total} " +
                    "top1_pct=${"%.1f".format(java.util.Locale.ROOT, pct1)} " +
                    "top3=${score.top3}/${score.total} " +
                    "top3_pct=${"%.1f".format(java.util.Locale.ROOT, pct3)}"
            )
        }
        return scores
    }

    @Test
    fun turkishCleanSwipeTopOneTopThreeBaseline() {
        val loaded = loadTurkishDictionary()
        val geometry = TestData.turkishQwertyGeometry()
        val predictor = WordPredictor(
            trie = loaded.trie,
            bigrams = BigramTable.EMPTY,
            geometry = geometry,
            forms = loaded.forms,
            language = "tr",
        )
        val targets = availableTurkishSwipeTargets(loaded.trie)
        val scores = evaluateTurkishSwipeCondition(predictor, geometry, targets, "clean-centerline")
        val overall = scores.getValue("all")
        assertTrue(
            "Turkish synthetic swipe Top-3 below 90%: ${overall.top3}/${overall.total}",
            overall.top3 * 10 >= overall.total * 9,
        )
    }

    @Test
    fun turkishSloppySwipeTopOneTopThreeBaseline() {
        val loaded = loadTurkishDictionary()
        val geometry = TestData.turkishQwertyGeometry()
        val predictor = WordPredictor(
            trie = loaded.trie,
            bigrams = BigramTable.EMPTY,
            geometry = geometry,
            forms = loaded.forms,
            language = "tr",
        )
        val targets = availableTurkishSwipeTargets(loaded.trie)
        val overshoot = evaluateTurkishSwipeCondition(
            predictor, geometry, targets, "overshoot-0.25",
        ).getValue("all")
        assertTrue(
            "Turkish overshoot Top-3 below 95%: ${overshoot.top3}/${overshoot.total}",
            overshoot.top3 * 100 >= overshoot.total * 95,
        )

        val jitter = evaluateTurkishSwipeCondition(
            predictor, geometry, targets, "jitter-0.12-overshoot-0.45",
        ).getValue("all")
        assertTrue(
            "Turkish jitter Top-3 below 95%: ${jitter.top3}/${jitter.total}",
            jitter.top3 * 100 >= jitter.total * 95,
        )
        assertTrue(
            "Turkish jitter Top-1 below 90%: ${jitter.top1}/${jitter.total}",
            jitter.top1 * 10 >= jitter.total * 9,
        )
    }


    /** Shared setup for supplemental Turkish reports; stays test-only. */
    private fun turkishFixture(): Triple<LoadedDictionary, WordPredictor, KeyboardGeometry> {
        val loaded = loadTurkishDictionary()
        val geometry = TestData.turkishQwertyGeometry()
        val predictor = WordPredictor(
            trie = loaded.trie,
            bigrams = BigramTable.EMPTY,
            geometry = geometry,
            forms = loaded.forms,
            language = "tr",
        )
        return Triple(loaded, predictor, geometry)
    }

    private fun loadTurkishBigramTable(trie: Trie): BigramTable {
        val path = assetPath("tr_bigrams.txt")
        assumeTrue("Turkish bigram asset not found", Files.exists(path))
        return Files.newBufferedReader(path).use { DictionaryLoader.loadBigrams(it, trie) }
    }

    // Gate: seeded gesture generation and decoder ranking must be repeatable.
    @Test
    fun turkishJitteredSwipeDecodeIsDeterministic() {
        val (_, predictor, geometry) = turkishFixture()
        val targets = availableTurkishSwipeTargets(predictor.trie).take(40)
        for (target in targets) {
            val duration = target.word.length.toLong() * 60L
            fun run() = predictor.decode(
                listOf(
                    TestData.jitteredSwipe(
                        target.word, geometry, t0 = 0, durMs = duration,
                        overshootKw = 0.45f, jitterKw = 0.12f,
                        seed = 0x5EED + target.ordinal * 37,
                    ),
                ),
                emptyList(),
            ).map { it.word }
            assertEquals("non-deterministic decode for ${target.word}", run(), run())
        }
        println("TR_SWIPE_DETERMINISM checked=${targets.size} pass=true")
    }

    // Measurement only: JVM timings are a regression signal, not a phone-latency claim.
    @Test
    fun turkishSwipeLatencyReport() {
        val (_, predictor, geometry) = turkishFixture()
        val targets = availableTurkishSwipeTargets(predictor.trie)
        fun decodeOnce(word: String, ordinal: Int) {
            val duration = word.length.toLong() * (35L + (ordinal * 29L % 60L))
            predictor.decode(
                listOf(TestData.swipe(word, geometry, t0 = 0, durMs = duration)),
                emptyList(),
            )
        }
        repeat(3) { targets.take(20).forEach { decodeOnce(it.word, it.ordinal) } }
        val samples = targets.map { target ->
            val start = System.nanoTime()
            decodeOnce(target.word, target.ordinal)
            (System.nanoTime() - start) / 1_000_000.0
        }.sorted()
        fun percentile(p: Double) = samples[((samples.size - 1) * p).toInt()]
        println(
            "TR_SWIPE_LATENCY candidates=${KineticaConstants.MAX_CANDIDATES} n=${samples.size} " +
                "mean_ms=${"%.2f".format(java.util.Locale.ROOT, samples.average())} " +
                "p50_ms=${"%.2f".format(java.util.Locale.ROOT, percentile(0.50))} " +
                "p95_ms=${"%.2f".format(java.util.Locale.ROOT, percentile(0.95))} " +
                "max_ms=${"%.2f".format(java.util.Locale.ROOT, samples.last())}"
        )
        // Emergency runaway guard only. Hosted CI is not a representative Android device.
        assertTrue("Turkish swipe p95 latency is unexpectedly high: ${percentile(0.95)} ms", percentile(0.95) < 3000.0)
    }

    // Measurement only: separate true dotted/dotless-I spellings from missing words.
    @Test
    fun turkishDottedDotlessTwinsReport() {
        val (_, predictor, geometry) = turkishFixture()
        val words = listOf(
            "dış", "diş", "kır", "kir", "sık", "sik", "kız", "kiz",
            "yıl", "yil", "tıp", "tip", "kıl", "kil", "ılık", "ilik",
        )
        var present = 0
        for (word in words) {
            if (!predictor.holdsSpelling(word)) {
                println("TR_PAIR target=$word available=false rank=not-in-dictionary top5=")
                continue
            }
            present++
            val candidates = predictor.decode(
                listOf(TestData.swipe(word, geometry, t0 = 0, durMs = word.length.toLong() * 60L)),
                emptyList(),
            ).map { it.word }
            val rank = candidates.indexOf(word)
            println(
                "TR_PAIR target=$word available=true rank=${if (rank < 0) "not-found" else (rank + 1).toString()} " +
                    "top5=${candidates.take(5).joinToString(",")}"
            )
        }
        println("TR_PAIR_SUMMARY present=$present requested=${words.size}")
    }

    // Measurement only: exact surface-form coverage for common and complex suffix chains.
    @Test
    fun turkishInflectionCoverageReport() {
        val (_, predictor, _) = turkishFixture()
        val inflected = listOf(
            "evlerimizdekilerden", "arkadaşlarımızınkinden", "gidebilirmişiz", "okuyamayacaktım",
            "güzelleştirmek", "çalıştırabilirsiniz", "öğrencilerimizle", "kitaplığımızdaki",
            "yapamayacağımızı", "sevgilimizinki", "konuşturabildik", "anlayamadıklarımız",
            "evler", "evlerimiz", "evlerimizde", "kitapları", "kitaplarımız", "kitabımızdan",
            "geliyorum", "geleceğim", "yapmadım", "yapabilir", "konuşuyorsunuz",
        )
        val found = inflected.filter { predictor.holdsSpelling(it) }
        val missing = inflected - found.toSet()
        println(
            "TR_COVERAGE inflected_requested=${inflected.size} found=${found.size} " +
                "found_words=${found.joinToString(",")} missing=${missing.joinToString(",")}"
        )
    }

    @Test
    fun turkishNextWordSuggestionsReport() {
        val loaded = loadTurkishDictionary()
        val bigrams = loadTurkishBigramTable(loaded.trie)
        val geometry = TestData.turkishQwertyGeometry()
        val predictor = WordPredictor(
            trie = loaded.trie,
            bigrams = bigrams,
            geometry = geometry,
            forms = loaded.forms,
            language = "tr",
        )
        val suggestions = predictor.nextWords("bir", 10, personal = false).map { it.word }
        println("TR_NEXT_WORD previous=bir count=${suggestions.size} candidates=${suggestions.joinToString(",")}")
        assertTrue("Expected at least one next-word suggestion after 'bir'", suggestions.isNotEmpty())
        assertEquals("Next-word output should not contain duplicate spellings", suggestions.size, suggestions.toSet().size)
    }

    @Test
    fun turkishLongWordDecodeDiagnostics() {
        val loaded = loadTurkishDictionary()
        val geometry = TestData.turkishQwertyGeometry()
        val predictor = WordPredictor(
            trie = loaded.trie,
            bigrams = BigramTable.EMPTY,
            geometry = geometry,
            forms = loaded.forms,
            language = "tr",
        )
        val targets = listOf("konuşuyorum", "düşünüyorum", "konuşuyorsunuz", "arkadaşlarımız")
        val oldSink = DecodeTrace.sink
        val traceLines = ArrayList<String>()
        try {
            DecodeTrace.sink = { line -> traceLines.add(line) }
            for (word in targets) {
                for (condition in listOf("clean", "overshoot", "jitter")) {
                    traceLines.clear()
                    val duration = word.length.toLong() * 60L
                    val token = when (condition) {
                        "clean" -> TestData.swipe(word, geometry, t0 = 0, durMs = duration)
                        "overshoot" -> TestData.sloppySwipe(
                            word, geometry, t0 = 0, durMs = duration, overshootKw = 0.25f,
                        )
                        else -> TestData.jitteredSwipe(
                            word, geometry, t0 = 0, durMs = duration,
                            overshootKw = 0.45f, jitterKw = 0.12f,
                            seed = 0x5EED + word.hashCode(),
                        )
                    }
                    val candidates = predictor.decode(listOf(token), emptyList()).map { it.word }
                    val rank = candidates.indexOf(word)
                    println(
                        "TR_SWIPE_TRACE target=$word condition=$condition " +
                            "rank=${if (rank < 0) "not-found" else (rank + 1).toString()} " +
                            "top5=${candidates.take(5).joinToString(",")}"
                    )
                    traceLines.filter { it.contains("attempts=") || it.startsWith("decode out") ||
                        it.trimStart().startsWith("nearmiss") ||
                        it.startsWith("TR_SWIPE_VISIT") }.forEach { line ->
                        println("TR_SWIPE_TRACE_DETAIL target=$word condition=$condition $line")
                    }
                }
            }
        } finally {
            DecodeTrace.sink = oldSink
        }
    }

    @Test
    fun turkishLongInflectedWordsFitSingleSwipeSegment() {
        val geometry = TestData.turkishQwertyGeometry()
        val words = listOf("konuşuyorsunuz", "arkadaşlarımız")
        for (word in words) {
            val token = TestData.swipe(word, geometry, t0 = 0, durMs = word.length.toLong() * 60L)
            val segment = Matcher.buildSegment(token, geometry)
            println(
                "TR_SWIPE_SEGMENT_BOUND word=$word length=${word.length} " +
                    "maxLetters=${segment.maxLetters} arcLen=${"%.2f".format(java.util.Locale.ROOT, segment.arcLen)}"
            )
            assertTrue(
                "Turkish one-swipe search bound ${segment.maxLetters} excludes target '$word'",
                segment.maxLetters >= word.length,
            )
        }
    }

@Test
    fun letterRevisitWordsSurviveSloppySwipes() {
        // Regression for the "however" -> "hoover" collapse: words that visit
        // a letter twice (the second e in how-e-v-e-r) were pruned by the
        // path-order monotonicity check, because each letter recorded only its
        // single nearest resample index. A perfect center-to-center path passes
        // that check with zero margin; any real overshoot at a turn shifted the
        // minima and killed the word before DTW scored it, leaving a shorter
        // revisit-free rival ("hoover") as the sole survivor.
        val trie = loadTrie()
        val g = TestData.qwertyGeometry()
        val predictor = WordPredictor(trie, BigramTable.EMPTY, g)

        val words = listOf(
            "however", "remember", "minimum", "tomorrow",
            "banana", "people", "interesting", "whatever",
        )
        for (word in words) {
            for (overshoot in listOf(0f, 0.25f, 0.45f)) {
                val token = if (overshoot == 0f) {
                    TestData.swipe(word, g, 0, 700)
                } else {
                    TestData.sloppySwipe(word, g, 0, 700, overshootKw = overshoot)
                }
                val result = predictor.decode(listOf(token), emptyList())
                assertTrue(
                    "'$word' (overshoot $overshoot) missing from ${result.map { it.word }}",
                    result.map { it.word }.contains(word),
                )
            }
        }

        // The specific reported failure: "however" must not lose to "hoover".
        val sloppy = predictor.decode(
            listOf(TestData.sloppySwipe("however", g, 0, 700, overshootKw = 0.45f)),
            emptyList(),
        )
        assertEquals("however", sloppy[0].word)
        // And a decode must never collapse to a single forced choice.
        assertTrue("expected >= 3 candidates, got ${sloppy.map { it.word }}", sloppy.size >= 3)
    }

    @Test
    fun englishContractionsDecodeFromPlainInput() {
        // The transparent apostrophe walk reaches a dictionary
        // contraction from the plain (apostrophe-free) letters, so swiping or
        // tapping "dont"/"arent"/... surfaces "don't"/"aren't"/... once the
        // contractions are in the wordlist. There is no apostrophe key on the
        // alpha layer; the ' is inserted for free by the trie descent.
        val trie = loadTrie()
        val g = TestData.qwertyGeometry()
        val predictor = WordPredictor(trie, BigramTable.EMPTY, g)

        val cases = mapOf(
            "dont" to "don't",
            "arent" to "aren't",
            "isnt" to "isn't",
            "cant" to "can't",
            "heres" to "here's",
        )
        for ((typed, expected) in cases) {
            val swiped = predictor.decode(
                listOf(TestData.swipe(typed, g, 0, 500)), emptyList(),
            )
            assertTrue(
                "'$expected' missing from swipe '$typed': ${swiped.map { it.word }}",
                swiped.map { it.word }.contains(expected),
            )
        }

        // Tap-typing the letters reaches it too (all-anchor apostrophe insert).
        val tapped = predictor.decode(
            "dont".mapIndexed { i, c -> TestData.tap(c, g, i * 100L) }, emptyList(),
        )
        assertTrue(
            "'don't' missing from tap 'dont': ${tapped.map { it.word }}",
            tapped.map { it.word }.contains("don't"),
        )
    }

    @Test
    fun decodeLatencyIsBounded() {
        val trie = loadTrie()
        val g = TestData.qwertyGeometry()
        val predictor = WordPredictor(trie, BigramTable.EMPTY, g)
        val tokens = listOf(TestData.swipe("keyboard", g, 0, 600))
        predictor.decode(tokens, emptyList()) // warmup
        val t0 = System.nanoTime()
        repeat(20) { predictor.decode(tokens, emptyList()) }
        val perDecodeMs = (System.nanoTime() - t0) / 20 / 1_000_000.0
        // Budget is 100 ms on a mid-range phone; a desktop JVM must be far under.
        assertTrue("decode took $perDecodeMs ms", perDecodeMs < 100.0)
    }

    @Test
    fun completionSurfacesFromTapPrefix() {
        // Real-dictionary completion golden: a 5-tap prefix surfaces its rare long
        // extension mid-word. Personal words merge into the trie at load the same
        // way, so a bundled word keeps this golden runnable everywhere.
        val trie = loadTrie()
        val g = TestData.qwertyGeometry()
        val predictor = WordPredictor(trie, BigramTable.EMPTY, g)
        val tokens = "keybo".mapIndexed { i, c -> TestData.tap(c, g, i * 100L) }
        val result = predictor.decode(tokens, emptyList())
        assertTrue(
            "'keyboard' missing from ${result.map { it.word }}",
            result.map { it.word }.contains("keyboard"),
        )
    }

    @Test
    fun completionDecodeLatencyIsBounded() {
        // Worst-case completion fan-out: "co" prefixes ~1500 words of the
        // real dictionary, so the bounded descent (COMPLETION_MAX_EXTRA,
        // MAX_CANDIDATES) keeps this inside the same 100 ms budget as full
        // swipe decodes.
        val trie = loadTrie()
        val g = TestData.qwertyGeometry()
        val predictor = WordPredictor(trie, BigramTable.EMPTY, g)
        val tokens = listOf(TestData.tap('c', g, 0), TestData.tap('o', g, 100))
        predictor.decode(tokens, emptyList()) // warmup
        val t0 = System.nanoTime()
        repeat(20) { predictor.decode(tokens, emptyList()) }
        val perDecodeMs = (System.nanoTime() - t0) / 20 / 1_000_000.0
        assertTrue("completion decode took $perDecodeMs ms", perDecodeMs < 100.0)
    }

    @Test
    fun multiAnchorDecodeLatencyIsBounded() {
        // Worst-case merge fan-out: a swipe carrying two cross-stream anchors
        // emits the multi-anchor interleave in three trim variants on top of
        // every other generator, and each interleave costs three DTW segments
        // per candidate instead of one. MAX_SPLIT_ANCHORS and MAX_ALT_SEQUENCES
        // keep that inside the same 100 ms budget.
        val trie = loadTrie()
        val g = TestData.qwertyGeometry()
        val predictor = WordPredictor(trie, BigramTable.EMPTY, g)
        val tokens = listOf(
            TestData.swipe("watd", g, t0 = 0, durMs = 900, stream = StreamId.LEFT),
            TestData.tap('n', g, 300, StreamId.RIGHT),
            TestData.tap('e', g, 600, StreamId.RIGHT),
        )
        predictor.decode(tokens, emptyList()) // warmup
        val t0 = System.nanoTime()
        repeat(20) { predictor.decode(tokens, emptyList()) }
        val perDecodeMs = (System.nanoTime() - t0) / 20 / 1_000_000.0
        assertTrue("multi-anchor decode took $perDecodeMs ms", perDecodeMs < 100.0)
    }

    @Test
    fun dualThumbDecodeLatencyIsBounded() {
        // Two overlapping cross-stream swipes, what two thumbs typing at once
        // produce, which none of the other latency bounds covers (their worst case
        // is one swipe with two anchors). Cutting both swipes multiplies the piece
        // count, so this is where MAX_SPLIT_ANCHORS and MAX_ALT_SEQUENCES have to
        // hold.
        // Shaped on the device buffer for "keys": right thumb k..y, left thumb e..s,
        // the left starting inside the right and ending with it.
        val trie = loadTrie()
        val g = TestData.qwertyGeometry()
        val predictor = WordPredictor(trie, BigramTable.EMPTY, g)
        val tokens = listOf(
            TestData.sloppySwipe("kjhy", g, t0 = 0, durMs = 420, stream = StreamId.RIGHT),
            TestData.sloppySwipe("res", g, t0 = 70, durMs = 360, stream = StreamId.LEFT),
        )
        predictor.decode(tokens, emptyList()) // warmup
        val t0 = System.nanoTime()
        repeat(20) { predictor.decode(tokens, emptyList()) }
        val perDecodeMs = (System.nanoTime() - t0) / 20 / 1_000_000.0
        assertTrue("dual-thumb decode took $perDecodeMs ms", perDecodeMs < 100.0)
    }

    @Test
    fun contactCutDecodeLatencyIsBounded() {
        // The bound above is two synthetic swipes, which carry contacts (TestData.
        // contactsAlong derives them from the path) and so reach this generator. It
        // does not reach the worst shape: three swipes, all three cuttable, where the
        // pair enumeration has three pairs to spend MAX_ALT_SEQUENCES on, not one.
        // Replayed from the device so the contacts are the ones a thumb produced.
        // Measured at 1.08 ms per decode against the 100 ms ceiling.
        val trie = loadTrie()
        val g = TestData.qwertyGeometry()
        val predictor = WordPredictor(trie, BigramTable.EMPTY, g)
        val tokens = TraceReplay.tokens(
            "decode in[en]: swipe[LEFT,t=4635556..4636808," +
                "keys=w@0-226,e@226-281,d@281-547,e@547-703,r@703-970,f@970-1024,g@1024-1252] " +
                "swipe[RIGHT,t=4635672..4635999,keys=o@0-126,k@126-186,j@186-236,n@236-327] " +
                "swipe[RIGHT,t=4636383..4636677,keys=i@0-110,j@110-236,n@236-294] ctx=[]",
            g,
        )
        predictor.decode(tokens, emptyList()) // warmup
        val t0 = System.nanoTime()
        repeat(20) { predictor.decode(tokens, emptyList()) }
        val perDecodeMs = (System.nanoTime() - t0) / 20 / 1_000_000.0
        assertTrue("contact-cut decode took $perDecodeMs ms", perDecodeMs < 100.0)
    }

    @Test
    fun mergedDecodeLatencyIsBounded() {
        // The split-variant fan-out (V1/V2/V3 per (tap, swipe) pair, capped by
        // MAX_ALT_SEQUENCES=12) multiplies pattern count for merged buffers;
        // the single-swipe case above never fires the generators. The
        // quindi early-tap buffer exercises all three variants against the
        // full dictionary under the same 100 ms budget.
        val trie = loadTrie()
        val g = TestData.qwertyGeometry()
        val predictor = WordPredictor(trie, BigramTable.EMPTY, g)
        val tokens = listOf(
            TestData.tap('q', g, 0, StreamId.LEFT),
            TestData.swipe("uini", g, t0 = 200, durMs = 600, stream = StreamId.RIGHT),
            TestData.tap('d', g, 520, StreamId.LEFT),
        )
        predictor.decode(tokens, emptyList()) // warmup
        val t0 = System.nanoTime()
        repeat(20) { predictor.decode(tokens, emptyList()) }
        val perDecodeMs = (System.nanoTime() - t0) / 20 / 1_000_000.0
        assertTrue("merged decode took $perDecodeMs ms", perDecodeMs < 100.0)
    }

    /**
     * The retraction's dictionary gate, on the buffers that made it necessary.
     *
     * Every pair here is verbatim from a capture: the first word was typed, the automatic
     * space arrived, and the next word's first letter decided whether the space survived.
     * `isLivePrefix` decides it.
     */
    @Test
    fun theRetractionGateSeparatesAFinishedWordFromAnUnfinishedOne() {
        val p = assetPath("it_wordlist.txt")
        assumeTrue("it_wordlist asset not found", Files.exists(p))
        val dict = Files.newBufferedReader(p).use { DictionaryLoader.load(it) }
        val pred = WordPredictor(dict.trie, BigramTable.EMPTY, TestData.qwertyGeometry(), dict.forms)

        // Finished words swallowing the next one, the reported bug. Each of these
        // decoded to nothing on device once the space had been taken back.
        for (fused in listOf("automaticop", "automaticope", "cadettod", "cadettodi")) {
            assertTrue("$fused must not look like a word in waiting", !pred.isLivePrefix(fused))
        }

        // What the retraction is for, and it must survive: a premature space inside a
        // word still being typed.
        for (fused in listOf("autom", "automatic", "mangian", "mangiano", "prov", "praticam")) {
            assertTrue("$fused is a real continuation", pred.isLivePrefix(fused))
        }

        // Accents fold, because a prefix has not chosen them yet: `perche` reaches the
        // `perché` node as the decoder's own accent handling does.
        assertTrue("perch", pred.isLivePrefix("perch"))
        assertTrue("case is folded", pred.isLivePrefix("AUTOM"))

        // An empty base never retracts: there is nothing to fuse into.
        assertTrue("empty", !pred.isLivePrefix(""))
    }

    /**
     * What the autospace's joined-token lookup finds, per language.
     *
     * The rule is the same in both; the outcomes differ because the data does. English
     * contractions are in the wordlist with large counts, so `don't` spaces. Italian
     * elisions are not in it (ten apostrophe entries, all corpus junk), so the lookup
     * cannot find `d'accordo`.
     *
     * `autospacesTappedWord` does not depend on this lookup for an apostrophe joiner: when
     * it answers no, it judges the piece after the apostrophe, and the second half of every
     * elision is an ordinary entry (the last block below). So the Italian assertion stands,
     * but it no longer decides whether the space arrives.
     */
    @Test
    fun theJoinedTokenLookupIsDecidedByWhatEachWordlistHolds() {
        val enPath = assetPath("en_wordlist.txt")
        val itPath = assetPath("it_wordlist.txt")
        assumeTrue("wordlists not found", Files.exists(enPath) && Files.exists(itPath))
        val g = TestData.qwertyGeometry()
        val enDict = Files.newBufferedReader(enPath).use { DictionaryLoader.load(it) }
        val itDict = Files.newBufferedReader(itPath).use { DictionaryLoader.load(it) }
        val en = WordPredictor(enDict.trie, BigramTable.EMPTY, g, enDict.forms)
        val it = WordPredictor(itDict.trie, BigramTable.EMPTY, g, itDict.forms)

        // English contractions: the whole token is a word, so the space is earned.
        for (w in listOf("don't", "it's", "can't")) {
            assertTrue("$w should be an English word", en.isWord(w))
        }
        // Not words, so a token like this stays refused, the reported behaviour to keep.
        for (w in listOf("log-12.com", "example.com", "session_notes")) {
            assertTrue("$w must not be a word", !en.isWord(w) && !it.isWord(w))
        }
        // The Italian gap. When elided forms are generated into the wordlist this
        // assertion flips; update it then.
        for (w in listOf("d'accordo", "l'altro", "un'ora", "dell'anno")) {
            assertTrue("$w is absent from it_wordlist", !it.isWord(w))
        }
        // ...and what the fallback rests on: the piece after the apostrophe is an ordinary
        // Italian word in every one of them, so judging it on its own is a real answer.
        // `ora` is the shortest at three letters, so the two-letter length rule never
        // bites on the forms that matter.
        for (w in listOf("accordo", "altro", "ora", "anno", "immagine")) {
            assertTrue("$w should be an Italian word", it.isWord(w))
            assertTrue("$w is long enough for the length rule", w.length >= 2)
        }
        // The retraction's second question, on real data: `dell'anno` is unreachable as a
        // whole and `anno` is reachable, so the fusion is asked of the tail as well.
        assertTrue("dell'anno is not a live prefix", !it.isLivePrefix("dell'anno"))
        assertTrue("anno is", it.isLivePrefix("anno"))
    }
}

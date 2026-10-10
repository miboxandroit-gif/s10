package com.kinetica.tools;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import zemberek.morphology.TurkishMorphology;
import zemberek.morphology.analysis.SingleAnalysis;
import zemberek.morphology.lexicon.DictionaryItem;

/**
 * Offline asset generator only. Zemberek and its full lexicon are not bundled in the Android APK.
 * It analyzes high-frequency words and emits a bounded set of common noun/verb surface forms.
 */
public final class TurkishMorphologyWords {
    private static final Locale TR = Locale.forLanguageTag("tr-TR");
    private static final Pattern TURKISH_WORD =
            Pattern.compile("^[abcçdefgğhıijklmnoöprsştuüvyz]{2,24}$");
    private static final String[] CASES = {"Acc", "Dat", "Loc", "Abl", "Gen"};
    private static final String[] POSSESSIVES = {"P1sg", "P2sg", "P3sg", "P1pl", "P2pl", "P3pl"};
    private static final String[] NOUN_NUMBERS = {"A3sg", "A3pl"};
    private static final String[] VERB_MOODS = {"Prog1", "Past", "Fut", "Aor", "Narr", "Necess", "Cond"};
    private static final String[] PERSONS = {"A1sg", "A2sg", "A3sg", "A1pl", "A2pl", "A3pl"};
    private static final int FORMS_PER_ROOT = 32;
    private static final long FREQUENCY_DIVISOR = 1000L;

    private TurkishMorphologyWords() {}

    private static final class Seed {
        final String word;
        final long count;
        Seed(String word, long count) { this.word = word; this.count = count; }
    }

    private static final class Root {
        final DictionaryItem item;
        final String pos;
        long frequency;
        Root(DictionaryItem item, String pos, long frequency) {
            this.item = item; this.pos = pos; this.frequency = frequency;
        }
    }

    private static final class Options {
        Path input;
        Path output;
        int maxSeeds = 60000;
        int maxRootsPerPos = 1200;
        int maxForms = 20000;
    }

    public static void main(String[] args) throws Exception {
        Options options = parseArgs(args);
        List<Seed> seeds = new ArrayList<>();
        Set<String> existing = new HashSet<>();
        try (BufferedReader reader = Files.newBufferedReader(options.input, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                int tab = line.lastIndexOf('\t');
                if (tab <= 0) continue;
                String word = line.substring(0, tab).trim().toLowerCase(TR);
                long count;
                try { count = Long.parseLong(line.substring(tab + 1).trim()); }
                catch (NumberFormatException ignored) { continue; }
                if (!validSurface(word) || count <= 0) continue;
                existing.add(word);
                if (seeds.size() < options.maxSeeds) seeds.add(new Seed(word, count));
            }
        }
        if (seeds.isEmpty()) throw new IllegalArgumentException("No valid Turkish seed words in " + options.input);

        TurkishMorphology morphology = TurkishMorphology.createWithDefaults();
        Map<String, Root> rootsById = new HashMap<>();
        int analyzed = 0;
        for (Seed seed : seeds) {
            // Proper-name suffixes and apostrophe forms are handled as a separate policy.
            if (seed.word.indexOf('\'') >= 0) continue;
            analyzed++;
            try {
                for (SingleAnalysis analysis : morphology.analyze(seed.word)) {
                    DictionaryItem item = analysis.getDictionaryItem();
                    if (analysis.isUnknown() || analysis.isRuntime() || item == null || item.isUnknown()) continue;
                    String pos = item.primaryPos == null ? "" : item.primaryPos.name();
                    if (!pos.equals("Noun") && !pos.equals("Verb")) continue;
                    if (item.lemma == null || !item.lemma.equals(item.lemma.toLowerCase(TR))
                            || item.lemma.indexOf('\'') >= 0 || !validSurface(item.lemma)) continue;
                    Root old = rootsById.get(item.id);
                    if (old == null) rootsById.put(item.id, new Root(item, pos, seed.count));
                    else if (seed.count > old.frequency) old.frequency = seed.count;
                }
            } catch (RuntimeException ignored) {
                // An unsupported entry is skipped; one bad surface must not abort the batch.
            }
        }

        Comparator<Root> rootOrder = Comparator.comparingLong((Root r) -> r.frequency).reversed()
                .thenComparing(r -> r.item.id);
        List<Root> nounRoots = rootsById.values().stream().filter(r -> r.pos.equals("Noun"))
                .sorted(rootOrder).limit(options.maxRootsPerPos).toList();
        List<Root> verbRoots = rootsById.values().stream().filter(r -> r.pos.equals("Verb"))
                .sorted(rootOrder).limit(options.maxRootsPerPos).toList();

        LinkedHashMap<String, Long> generated = new LinkedHashMap<>();
        for (Root root : nounRoots) generateNounForms(morphology, root, existing, generated);
        for (Root root : verbRoots) generateVerbForms(morphology, root, existing, generated);

        List<Map.Entry<String, Long>> ranked = new ArrayList<>(generated.entrySet());
        // Stable order preserves frequent-root traversal for equal-frequency forms.
        ranked.sort(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder()));
        int emitted = Math.min(options.maxForms, ranked.size());
        Files.createDirectories(options.output.toAbsolutePath().getParent());
        try (BufferedWriter writer = Files.newBufferedWriter(options.output, StandardCharsets.UTF_8)) {
            for (int i = 0; i < emitted; i++) {
                Map.Entry<String, Long> row = ranked.get(i);
                writer.write(row.getKey());
                writer.write('\t');
                writer.write(Long.toString(Math.max(1L, row.getValue())));
                writer.newLine();
            }
        }
        System.out.printf(Locale.ROOT,
                "TR_MORPHOLOGY seeds=%d analyzed=%d noun_roots=%d verb_roots=%d candidates=%d emitted=%d frequency_divisor=%d%n",
                seeds.size(), analyzed, nounRoots.size(), verbRoots.size(), generated.size(), emitted, FREQUENCY_DIVISOR);
    }

    private static void generateNounForms(TurkishMorphology morphology, Root root,
                                          Set<String> existing, LinkedHashMap<String, Long> generated) {
        LinkedHashMap<String, Long> local = new LinkedHashMap<>();
        for (String number : NOUN_NUMBERS) {
            add(morphology, root, existing, local, number);
            for (String caseTag : CASES) add(morphology, root, existing, local, number, "Pnon", caseTag);
            for (String possessive : POSSESSIVES) {
                add(morphology, root, existing, local, number, possessive);
                for (String caseTag : CASES) add(morphology, root, existing, local, number, possessive, caseTag);
                if (local.size() >= FORMS_PER_ROOT) break;
            }
            if (local.size() >= FORMS_PER_ROOT) break;
        }
        mergeRootForms(generated, local);
    }

    private static void generateVerbForms(TurkishMorphology morphology, Root root,
                                          Set<String> existing, LinkedHashMap<String, Long> generated) {
        LinkedHashMap<String, Long> local = new LinkedHashMap<>();
        for (String mood : VERB_MOODS) {
            for (String person : PERSONS) {
                add(morphology, root, existing, local, mood, person);
                add(morphology, root, existing, local, "Neg", mood, person);
                if (local.size() >= FORMS_PER_ROOT) break;
            }
            if (local.size() >= FORMS_PER_ROOT) break;
        }
        for (String infinitive : new String[] {"Inf1", "Inf2", "Inf3"}) {
            if (local.size() >= FORMS_PER_ROOT) break;
            add(morphology, root, existing, local, infinitive);
        }
        mergeRootForms(generated, local);
    }

    private static void add(TurkishMorphology morphology, Root root, Set<String> existing,
                            LinkedHashMap<String, Long> local, String... morphemes) {
        if (local.size() >= FORMS_PER_ROOT) return;
        try {
            for (var result : morphology.getWordGenerator().generate(root.item, morphemes)) {
                String surface = result.surface == null ? "" : result.surface.toLowerCase(TR);
                if (!validSurface(surface) || existing.contains(surface)) continue;
                local.putIfAbsent(surface, Math.max(1L, root.frequency / FREQUENCY_DIVISOR));
                if (local.size() >= FORMS_PER_ROOT) return;
            }
        } catch (RuntimeException ignored) {
            // Some POS/morpheme paths are not valid for a given lexicon item.
        }
    }

    private static void mergeRootForms(LinkedHashMap<String, Long> all, Map<String, Long> local) {
        for (Map.Entry<String, Long> row : local.entrySet()) {
            Long previous = all.get(row.getKey());
            if (previous == null) all.put(row.getKey(), row.getValue());
            else if (row.getValue() > previous) all.put(row.getKey(), row.getValue());
        }
    }

    private static boolean validSurface(String word) {
        return word != null && TURKISH_WORD.matcher(word).matches() && word.indexOf('\'') < 0;
    }

    private static Options parseArgs(String[] args) {
        Options out = new Options();
        for (int i = 0; i < args.length; i++) {
            String option = args[i];
            if (i + 1 >= args.length) throw new IllegalArgumentException("Missing value for " + option);
            String value = args[++i];
            switch (option) {
                case "--input" -> out.input = Paths.get(value);
                case "--output" -> out.output = Paths.get(value);
                case "--max-seeds" -> out.maxSeeds = Integer.parseInt(value);
                case "--max-roots-per-pos" -> out.maxRootsPerPos = Integer.parseInt(value);
                case "--max-forms" -> out.maxForms = Integer.parseInt(value);
                default -> throw new IllegalArgumentException("Unknown option: " + option);
            }
        }
        if (out.input == null || out.output == null) {
            throw new IllegalArgumentException("Usage: --input wordlist.tsv --output forms.tsv [--max-seeds N --max-roots-per-pos N --max-forms N]");
        }
        if (out.maxSeeds <= 0 || out.maxRootsPerPos <= 0 || out.maxForms < 0) {
            throw new IllegalArgumentException("limits must be positive (max-forms may be zero)");
        }
        return out;
    }
}

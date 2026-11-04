package dev.drimoz.yafs.data;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TranslationsTest {

    private static final Pattern PLACEHOLDER = Pattern.compile("%%|%s");

    private static final Path LANG = projectRoot().resolve(Path.of("src", "main", "resources", "assets", "yafs", "lang"));

    @Test
    void frenchMatchesEnglish() throws IOException {
        JsonObject english = read(LANG.resolve("en_us.json"));
        JsonObject french = read(LANG.resolve("fr_fr.json"));

        List<String> problems = new ArrayList<>();
        for (String key : english.keySet()) {
            if (!french.has(key)) {
                problems.add("fr_fr n'a pas " + key);
            } else if (!placeholders(french, key).equals(placeholders(english, key))) {
                problems.add("fr_fr a d'autres %s que en_us pour " + key);
            }
        }
        for (String key : french.keySet()) {
            if (!english.has(key)) problems.add("fr_fr a en trop " + key);
        }
        for (JsonObject locale : List.of(english, french)) {
            for (String key : locale.keySet()) {
                if (locale.get(key).getAsString().isBlank()) problems.add("valeur vide : " + key);
            }
        }
        assertEquals(List.of(), problems);
    }

    private static String placeholders(JsonObject json, String key) {
        Matcher matcher = PLACEHOLDER.matcher(json.get(key).getAsString());
        StringBuilder found = new StringBuilder();
        while (matcher.find()) found.append(matcher.group());
        return found.toString();
    }

    private static Path projectRoot() {
        for (Path dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
            if (Files.isRegularFile(dir.resolve("gradle.properties"))) return dir;
        }
        throw new IllegalStateException("Aucune racine de projet au-dessus de " + Path.of("").toAbsolutePath());
    }

    private static JsonObject read(Path file) throws IOException {
        try (Reader reader = Files.newBufferedReader(file)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
}

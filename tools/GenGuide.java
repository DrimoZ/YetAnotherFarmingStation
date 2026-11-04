import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Remplit les tableaux du guide joueur (docs/04-JOUEUR.md) avec ce que disent les données et le
 * code : un chiffre cité dans ces tableaux existe, puisqu'il en vient (YAF-091).
 *
 * <p>Usage : {@code java tools/GenGuide.java} réécrit le guide ; {@code java tools/GenGuide.java --check}
 * sort en erreur si un tableau est en retard, sans rien écrire. Chaque bloc se délimite par
 * {@code <!-- gen:nom -->} et {@code <!-- /gen:nom -->} ; ce qui est entre les deux est remplacé.
 */
public class GenGuide {

    static final Path ROOT = Path.of("").toAbsolutePath();
    static final Path RES = ROOT.resolve("src/main/resources");
    static final Path JAVA = ROOT.resolve("src/main/java/dev/drimoz/yafs");
    static final Path DOC = ROOT.resolve("docs/04-JOUEUR.md");

    static Map<String, String> lang = new LinkedHashMap<>();

    public static void main(String[] args) throws IOException {
        boolean check = args.length > 0 && args[0].equals("--check");
        loadLang();

        String doc = Files.readString(DOC, StandardCharsets.UTF_8);
        String out = doc;
        out = block(out, "tiers", tiers());
        out = block(out, "constants", constants());
        out = block(out, "modules", modules());
        out = block(out, "fertilizers", fertilizers());
        out = block(out, "soils", soils());
        out = block(out, "crops", crops());

        if (check) {
            if (!out.equals(doc)) {
                System.err.println("docs/04-JOUEUR.md est en retard sur les données : java tools/GenGuide.java");
                System.exit(1);
            }
            System.out.println("Le guide est à jour.");
            return;
        }
        Files.writeString(DOC, out, StandardCharsets.UTF_8);
        System.out.println("Guide écrit : " + DOC);
    }

    // Blocs

    static String block(String doc, String name, String content) {
        String open = "<!-- gen:" + name + " -->";
        String close = "<!-- /gen:" + name + " -->";
        int a = doc.indexOf(open);
        int b = doc.indexOf(close);
        if (a < 0 || b < a) throw new IllegalStateException("Bloc absent du guide : " + name);
        return doc.substring(0, a + open.length()) + "\n" + content.strip() + "\n" + doc.substring(b);
    }

    // Lecture

    static void loadLang() throws IOException {
        String text = Files.readString(RES.resolve("assets/yafs/lang/fr_fr.json"), StandardCharsets.UTF_8);
        Matcher m = Pattern.compile("\"([^\"]+)\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(text);
        while (m.find()) lang.put(m.group(1), m.group(2).replace("\\\"", "\""));
    }

    /** Les noms français de vanilla dont on est sûr ; les autres restent en code plutôt qu'inventés. */
    static final Map<String, String> VANILLA = Map.ofEntries(
            Map.entry("minecraft:beetroot_seeds", "Graines de betterave"), Map.entry("minecraft:cactus", "Cactus"),
            Map.entry("minecraft:carrot", "Carotte"), Map.entry("minecraft:cocoa_beans", "Fèves de cacao"),
            Map.entry("minecraft:melon_seeds", "Graines de pastèque"), Map.entry("minecraft:melon", "Pastèque"),
            Map.entry("minecraft:nether_wart", "Verrue du Nether"), Map.entry("minecraft:potato", "Pomme de terre"),
            Map.entry("minecraft:pumpkin_seeds", "Graines de citrouille"), Map.entry("minecraft:pumpkin", "Citrouille"),
            Map.entry("minecraft:sugar_cane", "Canne à sucre"), Map.entry("minecraft:sweet_berries", "Baies sucrées"),
            Map.entry("minecraft:wheat_seeds", "Graines de blé"), Map.entry("minecraft:bone_meal", "Poudre d'os"),
            Map.entry("minecraft:dirt", "Terre"), Map.entry("minecraft:grass_block", "Bloc d'herbe"),
            Map.entry("minecraft:dirt_path", "Chemin de terre"), Map.entry("minecraft:farmland", "Terre labourée"));

    static String name(String id) {
        if (VANILLA.containsKey(id)) return VANILLA.get(id);
        int colon = id.indexOf(':');
        String ns = id.substring(0, colon), path = id.substring(colon + 1);
        String key = lang.containsKey("item." + ns + "." + path) ? "item." + ns + "." + path : "block." + ns + "." + path;
        return lang.containsKey(key) ? lang.get(key) : "`" + id + "`";
    }

    static Map<String, String> flat(Path file) throws IOException {
        String text = Files.readString(file, StandardCharsets.UTF_8);
        Map<String, String> values = new LinkedHashMap<>();
        Matcher m = Pattern.compile("\"([a-z_]+)\"\\s*:\\s*(\"[^\"]*\"|-?[0-9.]+|true|false)").matcher(text);
        while (m.find()) values.put(m.group(1), m.group(2).replace("\"", ""));
        return values;
    }

    static List<Path> files(String dir) throws IOException {
        try (Stream<Path> s = Files.list(RES.resolve("data/yafs/yafs/" + dir))) {
            return s.filter(p -> p.toString().endsWith(".json")).sorted().toList();
        }
    }

    static Matcher find(Path file, String regex, String what) throws IOException {
        Matcher m = Pattern.compile(regex).matcher(Files.readString(file, StandardCharsets.UTF_8));
        if (!m.find()) throw new IllegalStateException("Introuvable dans " + file.getFileName() + " : " + what);
        return m;
    }

    // Nombres, à la française

    static String n(double v) {
        if (v == Math.rint(v)) return String.format(Locale.US, "%,d", (long) v).replace(',', ' ');
        String s = String.format(Locale.US, "%.2f", v);
        s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
        return s.replace('.', ',');
    }

    static double d(Map<String, String> v, String key, double fallback) {
        return v.containsKey(key) ? Double.parseDouble(v.get(key)) : fallback;
    }

    static String duration(double seconds) {
        if (seconds >= 3600 && seconds % 3600 == 0) return n(seconds / 3600) + " h";
        if (seconds >= 60 && seconds % 60 == 0) return n(seconds / 60) + " min";
        return n(seconds) + " s";
    }

    static double num(String s) {
        return Double.parseDouble(s.replace("_", ""));
    }

    // Tableaux

    static String tiers() throws IOException {
        Path file = JAVA.resolve("content/greenhouse/GreenhouseTier.java");
        Matcher m = Pattern.compile("(I{1,3})\\(new MultiblockShape\\((\\d+), (\\d+), (\\d+)\\), (\\d+), (\\d+)\\)").matcher(Files.readString(file, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder("| Palier | Volume (largeur × hauteur × profondeur) | Parcelles | Emplacements de module |\n|---|---|---|---|\n");
        boolean any = false;
        while (m.find()) {
            any = true;
            int side = Integer.parseInt(m.group(5));
            sb.append("| ").append(m.group(1)).append(" | ").append(m.group(2)).append(" × ").append(m.group(3)).append(" × ").append(m.group(4))
                    .append(" | ").append(side * side).append(" (").append(side).append(" × ").append(side).append(") | ").append(m.group(6)).append(" |\n");
        }
        if (!any) throw new IllegalStateException("Aucun palier trouvé dans GreenhouseTier.java");
        return sb.toString();
    }

    static String constants() throws IOException {
        Path be = JAVA.resolve("content/greenhouse/GreenhouseBlockEntity.java");
        Path crop = JAVA.resolve("content/crop/Crop.java");
        Path cfg = JAVA.resolve("YafsConfig.java");
        Path module = JAVA.resolve("content/module/ModuleDefinition.java");

        double tank = num(find(be, "TANK_CAPACITY = ([\\d_]+);", "TANK_CAPACITY").group(1));
        double energy = num(find(be, "ENERGY_CAPACITY = ([\\d_]+);", "ENERGY_CAPACITY").group(1));
        double step = num(find(be, "PROCESS_INTERVAL = (\\d+);", "PROCESS_INTERVAL").group(1));
        double outBase = num(find(be, "return (\\d+) \\+ this\\.extra;", "limite de sortie").group(1));
        Matcher cost = find(crop, "new Cost\\(([\\d_]+), (\\d+), (\\d+)\\);", "Cost.DEFAULT");
        double residue = Double.parseDouble(find(crop, "this\\(ticks, water, energy, ([\\d.]+)D, null\\)", "résidu par défaut").group(1));
        double speed = Double.parseDouble(find(cfg, "\"energizedSpeed\", ([\\d.]+)D", "energizedSpeed").group(1));
        double growth = Double.parseDouble(find(cfg, "\"growthSpeed\", ([\\d.]+)D", "growthSpeed").group(1));
        boolean residues = Boolean.parseBoolean(find(cfg, "\"residues\", (true|false)", "residues").group(1));
        double stack = num(find(module, "\"max_stack\", (\\d+)", "max_stack par défaut").group(1));
        Matcher light = find(JAVA.resolve("core/Efficiency.java"), "TOLERANT = new Requirements\\((\\d+), (\\d+), ([\\d.]+)D, ([\\d.]+)D\\)", "exigences tolérantes");

        double ticks = num(cost.group(1));
        return "| Ce que c'est | Valeur |\n|---|---|\n"
                + "| Réservoir d'eau de la serre | " + n(tank) + " mB |\n"
                + "| Réserve d'énergie de base | " + n(energy) + " FE |\n"
                + "| Une culture, sans réglage : durée d'un cycle | " + n(ticks) + " ticks, environ " + n(Math.round(ticks / 1200)) + " min |\n"
                + "| Une culture, sans réglage : eau par cycle et par plant | " + n(num(cost.group(2))) + " mB |\n"
                + "| Une culture, sans réglage : énergie, si la parcelle est alimentée | " + n(num(cost.group(3))) + " FE/t |\n"
                + "| Chance de résidu, par plant et par cycle | " + n(residue) + " |\n"
                + "| Une culture reconnue automatiquement : lumière | " + light.group(1) + " à " + light.group(2) + " |\n"
                + "| Une culture reconnue automatiquement : température | " + n(Double.parseDouble(light.group(3))) + " à " + n(Double.parseDouble(light.group(4))) + " °C |\n"
                + "| Items qu'une case de sortie tient, sans module de capacité | " + n(outBase) + " |\n"
                + "| Modules d'une même sorte acceptés, par défaut | " + n(stack) + " |\n"
                + "| La serre travaille tous les | " + n(step) + " ticks |\n"
                + "| Réglage `energizedSpeed` (vitesse d'une parcelle alimentée) | ×" + n(speed) + " |\n"
                + "| Réglage `growthSpeed` (multiplicateur global) | ×" + n(growth) + " |\n"
                + "| Réglage `residues` | " + (residues ? "activés" : "coupés") + " |\n";
    }

    static final List<String> MODULE_ORDER = List.of("module_grow_lamp", "module_blackout", "module_heater", "module_cooler", "module_rain_collector",
            "module_accelerator_1", "module_accelerator_2", "module_accelerator_3", "module_density_1", "module_density_2", "module_density_3",
            "module_battery", "module_capacity", "module_composter");

    static String modules() throws IOException {
        List<Path> all = new ArrayList<>(files("module"));
        all.sort((a, b) -> {
            int ia = MODULE_ORDER.indexOf(a.getFileName().toString().replace(".json", ""));
            int ib = MODULE_ORDER.indexOf(b.getFileName().toString().replace(".json", ""));
            return Integer.compare(ia < 0 ? 999 : ia, ib < 0 ? 999 : ib);
        });
        StringBuilder sb = new StringBuilder("| Module | Ce qu'il fait | Ce qu'il coûte | Maximum |\n|---|---|---|---|\n");
        for (Path file : all) {
            String id = file.getFileName().toString().replace(".json", "");
            Map<String, String> v = flat(file);
            List<String> does = new ArrayList<>();
            List<String> costs = new ArrayList<>();
            if (d(v, "speed_multiplier", 1) > 1) does.add("vitesse des parcelles alimentées ×" + n(d(v, "speed_multiplier", 1)) + " (les bonus s'additionnent)");
            if (d(v, "density", 1) > 1) does.add("+" + n(d(v, "density", 1) - 1) + (d(v, "density", 1) - 1 == 1 ? " plant" : " plants") + " par parcelle");
            if (d(v, "light_floor", 0) > 0) does.add("lumière jamais sous " + n(d(v, "light_floor", 0)));
            if (d(v, "light_cap", 15) < 15) does.add("lumière plafonnée à " + n(d(v, "light_cap", 15)));
            if (d(v, "temperature_shift", 0) != 0) does.add("température " + (d(v, "temperature_shift", 0) > 0 ? "+" : "") + n(d(v, "temperature_shift", 0)) + " °C");
            if (d(v, "rain_collection", 0) > 0) does.add(n(d(v, "rain_collection", 0)) + " mB d'eau de pluie par seconde");
            if (d(v, "energy_capacity", 0) > 0) does.add("+" + n(d(v, "energy_capacity", 0)) + " FE de réserve");
            if (d(v, "output_capacity", 0) > 0) does.add("+" + n(d(v, "output_capacity", 0)) + " items par case de sortie");
            if (d(v, "composting", 0) > 0) does.add("change " + n(d(v, "compost_count", 4)) + " composts de la sortie en un engrais, " + n(d(v, "composting", 0)) + " lot par pas");
            if (d(v, "energy_per_tick", 0) > 0) costs.add(n(d(v, "energy_per_tick", 0)) + " FE/t");
            if (d(v, "energy_multiplier", 1) > 1) costs.add("énergie des parcelles ×" + n(d(v, "energy_multiplier", 1)));
            if (d(v, "water_multiplier", 1) > 1) costs.add("eau ×" + n(d(v, "water_multiplier", 1)));
            if (d(v, "density", 1) > 1) costs.add("chaque plant boit et consomme");
            sb.append("| ").append(name("yafs:" + id)).append(" | ").append(String.join(" ; ", does)).append(" | ")
                    .append(costs.isEmpty() ? "—" : String.join(" ; ", costs)).append(" | ").append(n(d(v, "max_stack", 16))).append(" |\n");
        }
        return sb.toString();
    }

    static String fertilizers() throws IOException {
        StringBuilder sb = new StringBuilder("| Engrais | Effet sur toute la serre | Un item nourrit une parcelle |\n|---|---|---|\n");
        for (Path file : files("fertilizer")) {
            Map<String, String> v = flat(file);
            String item = v.get("item");
            String label = item.startsWith("#") ? "tout item du tag `" + item + "`" : name(item);
            List<String> effect = new ArrayList<>();
            if (d(v, "speed", 1) > 1) effect.add("croissance ×" + n(d(v, "speed", 1)));
            if (d(v, "yield", 1) > 1) effect.add("récolte ×" + n(d(v, "yield", 1)));
            sb.append("| ").append(label).append(" | ").append(String.join(" ; ", effect)).append(" | ").append(duration(d(v, "seconds", 300))).append(" |\n");
        }
        return sb.toString();
    }

    static String soils() throws IOException {
        StringBuilder sb = new StringBuilder("| Sols | Comptent comme | Bonus de leur parcelle |\n|---|---|---|\n");
        for (Path file : files("substrate")) {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            Matcher items = Pattern.compile("\"items\"\\s*:\\s*\\[([^\\]]*)\\]").matcher(text);
            if (!items.find()) throw new IllegalStateException("Pas d'items dans " + file.getFileName());
            List<String> names = new ArrayList<>();
            for (String raw : items.group(1).split(",")) {
                String id = raw.trim().replace("\"", "");
                if (!id.isEmpty()) names.add(id.startsWith("#") ? "tag `" + id + "`" : name(id));
            }
            Map<String, String> v = flat(file);
            List<String> bonus = new ArrayList<>();
            if (d(v, "speed", 1) > 1) bonus.add("croissance ×" + n(d(v, "speed", 1)));
            if (d(v, "yield", 1) > 1) bonus.add("récolte ×" + n(d(v, "yield", 1)));
            sb.append("| ").append(String.join(", ", names)).append(" | ").append(name(v.get("counts_as"))).append(" | ")
                    .append(bonus.isEmpty() ? "—" : String.join(" ; ", bonus)).append(" |\n");
        }
        return sb.toString();
    }

    static String crops() throws IOException {
        StringBuilder sb = new StringBuilder("| Culture réglée par un fichier | Lumière | Température | Particularité |\n|---|---|---|---|\n");
        for (Path file : files("crop")) {
            Map<String, String> v = flat(file);
            List<String> notes = new ArrayList<>();
            if (v.containsKey("catalyst")) notes.add("catalyseur " + name(v.get("catalyst")));
            if (v.containsKey("harvest")) notes.add("récolte du bloc " + name(v.get("harvest")));
            if (v.containsKey("ticks")) notes.add("cycle de " + duration(d(v, "ticks", 0) / 20));
            if (v.containsKey("water_per_cycle")) notes.add(n(d(v, "water_per_cycle", 0)) + " mB par cycle");
            sb.append("| ").append(name(v.get("seed"))).append(" | ").append(n(d(v, "min_light", 9))).append(" à ").append(n(d(v, "max_light", 15)))
                    .append(" | ").append(n(d(v, "min_temperature", 0))).append(" à ").append(n(d(v, "max_temperature", 35))).append(" °C | ")
                    .append(notes.isEmpty() ? "—" : String.join(" ; ", notes)).append(" |\n");
        }
        return sb.toString();
    }
}

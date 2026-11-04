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

public class GenGuide {

    static final Path ROOT = Path.of("").toAbsolutePath();
    static final Path RES = ROOT.resolve("src/main/resources");
    static final Path JAVA = ROOT.resolve("src/main/java/dev/drimoz/yafs");
    static final Path WIKI = ROOT.resolve("docs/wiki");

    static Map<String, String> lang = new LinkedHashMap<>();

    public static void main(String[] args) throws IOException {
        boolean check = args.length > 0 && args[0].equals("--check");
        loadLang();

        Map<String, String> blocks = new LinkedHashMap<>();
        blocks.put("tiers", tiers());
        blocks.put("constants", constants());
        blocks.put("modules", modules());
        blocks.put("fertilizers", fertilizers());
        blocks.put("soils", soils());
        blocks.put("crops", crops());

        Map<Path, String> pages = new LinkedHashMap<>();
        try (Stream<Path> s = Files.list(WIKI)) {
            for (Path p : s.filter(f -> f.toString().endsWith(".md")).sorted().toList())
                pages.put(p, Files.readString(p, StandardCharsets.UTF_8));
        }
        boolean stale = false;
        for (var block : blocks.entrySet()) {
            Path home = null;
            for (var page : pages.entrySet()) {
                if (page.getValue().contains("<!-- gen:" + block.getKey() + " -->")) {
                    if (home != null) throw new IllegalStateException("Block in two pages: " + block.getKey());
                    home = page.getKey();
                }
            }
            if (home == null) throw new IllegalStateException("Block missing from the wiki: " + block.getKey());
            String before = pages.get(home);
            String after = replace(before, block.getKey(), block.getValue());
            if (!after.equals(before)) {
                stale = true;
                pages.put(home, after);
            }
        }

        if (check) {
            if (stale) {
                System.err.println("The wiki is behind the data: run java tools/GenGuide.java");
                System.exit(1);
            }
            System.out.println("The wiki is up to date.");
            return;
        }
        for (var page : pages.entrySet()) Files.writeString(page.getKey(), page.getValue(), StandardCharsets.UTF_8);
        System.out.println("Wiki written: " + WIKI);
    }

    static String replace(String doc, String name, String content) {
        String open = "<!-- gen:" + name + " -->";
        String close = "<!-- /gen:" + name + " -->";
        int a = doc.indexOf(open);
        int b = doc.indexOf(close);
        if (a < 0 || b < a) throw new IllegalStateException("Block missing from the wiki: " + name);
        return doc.substring(0, a + open.length()) + "\n" + content.strip() + "\n" + doc.substring(b);
    }

    static void loadLang() throws IOException {
        String text = Files.readString(RES.resolve("assets/yafs/lang/en_us.json"), StandardCharsets.UTF_8);
        Matcher m = Pattern.compile("\"([^\"]+)\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(text);
        while (m.find()) lang.put(m.group(1), m.group(2).replace("\\\"", "\""));
    }

    static String name(String id) {
        int colon = id.indexOf(':');
        String ns = id.substring(0, colon), path = id.substring(colon + 1);
        String key = lang.containsKey("item." + ns + "." + path) ? "item." + ns + "." + path : "block." + ns + "." + path;
        if (lang.containsKey(key)) return lang.get(key);
        if (!ns.equals("minecraft")) return "`" + id + "`";
        StringBuilder sb = new StringBuilder();
        for (String word : path.split("_")) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return sb.toString();
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
        if (!m.find()) throw new IllegalStateException("Not found in " + file.getFileName() + ": " + what);
        return m;
    }

    static String n(double v) {
        if (v == Math.rint(v)) return String.format(Locale.US, "%,d", (long) v);
        String s = String.format(Locale.US, "%.2f", v);
        return s.replaceAll("0+$", "").replaceAll("\\.$", "");
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

    static String tiers() throws IOException {
        Path file = JAVA.resolve("content/greenhouse/GreenhouseTier.java");
        Matcher m = Pattern.compile("(I{1,3})\\(new MultiblockShape\\((\\d+), (\\d+), (\\d+)\\), (\\d+), (\\d+)\\)").matcher(Files.readString(file, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder("| Tier | Volume (width × height × depth) | Plots | Module slots |\n|---|---|---|---|\n");
        boolean any = false;
        while (m.find()) {
            any = true;
            int side = Integer.parseInt(m.group(5));
            sb.append("| ").append(m.group(1)).append(" | ").append(m.group(2)).append(" × ").append(m.group(3)).append(" × ").append(m.group(4))
                    .append(" | ").append(side * side).append(" (").append(side).append(" × ").append(side).append(") | ").append(m.group(6)).append(" |\n");
        }
        if (!any) throw new IllegalStateException("No tier found in GreenhouseTier.java");
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
        double outBase = num(find(be, "return (\\d+) \\+ this\\.extra;", "output limit").group(1));
        Matcher cost = find(crop, "new Cost\\(([\\d_]+), (\\d+), (\\d+)\\);", "Cost.DEFAULT");
        double residue = Double.parseDouble(find(crop, "this\\(ticks, water, energy, ([\\d.]+)D, null\\)", "default residue").group(1));
        double speed = Double.parseDouble(find(cfg, "\"energizedSpeed\", ([\\d.]+)D", "energizedSpeed").group(1));
        double growth = Double.parseDouble(find(cfg, "\"growthSpeed\", ([\\d.]+)D", "growthSpeed").group(1));
        boolean residues = Boolean.parseBoolean(find(cfg, "\"residues\", (true|false)", "residues").group(1));
        double stack = num(find(module, "\"max_stack\", (\\d+)", "default max_stack").group(1));
        Matcher light = find(JAVA.resolve("core/Efficiency.java"), "TOLERANT = new Requirements\\((\\d+), (\\d+), ([\\d.]+)D, ([\\d.]+)D\\)", "tolerant requirements");

        double ticks = num(cost.group(1));
        return "| What | Value |\n|---|---|\n"
                + "| Greenhouse water tank | " + n(tank) + " mB |\n"
                + "| Base energy storage | " + n(energy) + " FE |\n"
                + "| A crop with no settings: length of a cycle | " + n(ticks) + " ticks, about " + n(Math.round(ticks / 1200)) + " min |\n"
                + "| A crop with no settings: water per cycle and plant | " + n(num(cost.group(2))) + " mB |\n"
                + "| A crop with no settings: energy, when the plot is powered | " + n(num(cost.group(3))) + " FE/t |\n"
                + "| Residue chance, per plant and cycle | " + n(residue) + " |\n"
                + "| An automatically recognised crop: light | " + light.group(1) + " to " + light.group(2) + " |\n"
                + "| An automatically recognised crop: temperature | " + n(Double.parseDouble(light.group(3))) + " to " + n(Double.parseDouble(light.group(4))) + " °C |\n"
                + "| Items an output slot holds, with no Capacity module | " + n(outBase) + " |\n"
                + "| Modules of one kind accepted, by default | " + n(stack) + " |\n"
                + "| The greenhouse works every | " + n(step) + " ticks |\n"
                + "| Setting `energizedSpeed` (speed of a powered plot) | ×" + n(speed) + " |\n"
                + "| Setting `growthSpeed` (global multiplier) | ×" + n(growth) + " |\n"
                + "| Setting `residues` | " + (residues ? "on" : "off") + " |\n";
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
        StringBuilder sb = new StringBuilder("| Module | What it does | What it costs | Maximum |\n|---|---|---|---|\n");
        for (Path file : all) {
            String id = file.getFileName().toString().replace(".json", "");
            Map<String, String> v = flat(file);
            List<String> does = new ArrayList<>();
            List<String> costs = new ArrayList<>();
            if (d(v, "speed_multiplier", 1) > 1) does.add("powered plots grow ×" + n(d(v, "speed_multiplier", 1)) + " faster (bonuses add up)");
            if (d(v, "density", 1) > 1) does.add("+" + n(d(v, "density", 1) - 1) + (d(v, "density", 1) - 1 == 1 ? " plant" : " plants") + " per plot");
            if (d(v, "light_floor", 0) > 0) does.add("light never below " + n(d(v, "light_floor", 0)));
            if (d(v, "light_cap", 15) < 15) does.add("light never above " + n(d(v, "light_cap", 15)));
            if (d(v, "temperature_shift", 0) != 0) does.add("temperature " + (d(v, "temperature_shift", 0) > 0 ? "+" : "") + n(d(v, "temperature_shift", 0)) + " °C");
            if (d(v, "rain_collection", 0) > 0) does.add("collects " + n(d(v, "rain_collection", 0)) + " mB of rain per second");
            if (d(v, "energy_capacity", 0) > 0) does.add("+" + n(d(v, "energy_capacity", 0)) + " FE of storage");
            if (d(v, "output_capacity", 0) > 0) does.add("+" + n(d(v, "output_capacity", 0)) + " items per output slot");
            if (d(v, "composting", 0) > 0) does.add("turns " + n(d(v, "compost_count", 4)) + " compost from the output into one fertilizer, " + n(d(v, "composting", 0)) + " batch per step");
            if (d(v, "energy_per_tick", 0) > 0) costs.add(n(d(v, "energy_per_tick", 0)) + " FE/t");
            if (d(v, "energy_multiplier", 1) > 1) costs.add("plot energy ×" + n(d(v, "energy_multiplier", 1)));
            if (d(v, "water_multiplier", 1) > 1) costs.add("water ×" + n(d(v, "water_multiplier", 1)));
            if (d(v, "density", 1) > 1) costs.add("every plant drinks and uses energy");
            sb.append("| ").append(name("yafs:" + id)).append(" | ").append(String.join("; ", does)).append(" | ")
                    .append(costs.isEmpty() ? "—" : String.join("; ", costs)).append(" | ").append(n(d(v, "max_stack", 16))).append(" |\n");
        }
        return sb.toString();
    }

    static String fertilizers() throws IOException {
        StringBuilder sb = new StringBuilder("| Fertilizer | Effect on the whole greenhouse | One item feeds one plot for |\n|---|---|---|\n");
        for (Path file : files("fertilizer")) {
            Map<String, String> v = flat(file);
            String item = v.get("item");
            String label = item.startsWith("#") ? "any item of the tag `" + item + "`" : name(item);
            List<String> effect = new ArrayList<>();
            if (d(v, "speed", 1) > 1) effect.add("growth ×" + n(d(v, "speed", 1)));
            if (d(v, "yield", 1) > 1) effect.add("harvest ×" + n(d(v, "yield", 1)));
            sb.append("| ").append(label).append(" | ").append(String.join("; ", effect)).append(" | ").append(duration(d(v, "seconds", 300))).append(" |\n");
        }
        return sb.toString();
    }

    static String soils() throws IOException {
        StringBuilder sb = new StringBuilder("| Soils | Count as | Bonus on their plot |\n|---|---|---|\n");
        for (Path file : files("substrate")) {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            Matcher items = Pattern.compile("\"items\"\\s*:\\s*\\[([^\\]]*)\\]").matcher(text);
            if (!items.find()) throw new IllegalStateException("No items in " + file.getFileName());
            List<String> names = new ArrayList<>();
            for (String raw : items.group(1).split(",")) {
                String id = raw.trim().replace("\"", "");
                if (!id.isEmpty()) names.add(id.startsWith("#") ? "tag `" + id + "`" : name(id));
            }
            Map<String, String> v = flat(file);
            List<String> bonus = new ArrayList<>();
            if (d(v, "speed", 1) > 1) bonus.add("growth ×" + n(d(v, "speed", 1)));
            if (d(v, "yield", 1) > 1) bonus.add("harvest ×" + n(d(v, "yield", 1)));
            sb.append("| ").append(String.join(", ", names)).append(" | ").append(name(v.get("counts_as"))).append(" | ")
                    .append(bonus.isEmpty() ? "—" : String.join("; ", bonus)).append(" |\n");
        }
        return sb.toString();
    }

    static String crops() throws IOException {
        StringBuilder sb = new StringBuilder("| Crop with its own file | Light | Temperature | Notes |\n|---|---|---|---|\n");
        for (Path file : files("crop")) {
            Map<String, String> v = flat(file);
            List<String> notes = new ArrayList<>();
            if (v.containsKey("catalyst")) notes.add("catalyst " + name(v.get("catalyst")));
            if (v.containsKey("harvest")) notes.add("harvests the block " + name(v.get("harvest")));
            if (v.containsKey("ticks")) notes.add("cycle of " + duration(d(v, "ticks", 0) / 20));
            if (v.containsKey("water_per_cycle")) notes.add(n(d(v, "water_per_cycle", 0)) + " mB per cycle");
            sb.append("| ").append(name(v.get("seed"))).append(" | ").append(n(d(v, "min_light", 9))).append(" to ").append(n(d(v, "max_light", 15)))
                    .append(" | ").append(n(d(v, "min_temperature", 0))).append(" to ").append(n(d(v, "max_temperature", 35))).append(" °C | ")
                    .append(notes.isEmpty() ? "—" : String.join("; ", notes)).append(" |\n");
        }
        return sb.toString();
    }
}

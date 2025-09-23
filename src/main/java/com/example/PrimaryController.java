package com.example;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.JarURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.FileChooser;
import javafx.stage.Window;

public class PrimaryController {

    @FXML
    private ComboBox<String> class_selection;
    @FXML
    private ImageView image_display;
    @FXML
    private TextField player_name;
    @FXML
    private TextField character_name;
    @FXML
    private MenuItem save_mi;
    @FXML
    private MenuItem load_mi;
    @FXML
    private MenuItem close_mi;
    @FXML
    private TextField level_display_field;
    @FXML
    private javafx.scene.control.Button up_level_btn;
    @FXML
    private javafx.scene.control.Button down_level_btn;
    @FXML
    private ImageView skill_slot_one;
    @FXML
    private ImageView skill_slot_two;
    @FXML
    private ImageView skill_slot_three;
    @FXML
    private ImageView skill_slot_four;
    @FXML
    private TextField strength_tf;
    @FXML
    private TextField dexterity_tf;
    @FXML
    private TextField intelligence_tf;
    @FXML
    private TextField luck_tf;
    @FXML
    private TextField power_tf;

    private int baseStr, baseDex, baseInt, baseLuck, basePower;

    private static final int UNLOCK_SLOT_2 = 5;
    private static final int UNLOCK_SLOT_3 = 10;
    private static final int UNLOCK_SLOT_4 = 15;

// ---------- EXPORT ----------
    private void exportCharacterToCsv() {
        // Collect current values (in a stable order)
        Map<String, String> data = new LinkedHashMap<>();
        data.put("player_name", safeText(player_name));
        data.put("character_name", safeText(character_name));
        data.put("class_selection", class_selection == null ? "" : String.valueOf(class_selection.getValue()));
        data.put("level_display_field", safeText(level_display_field));
        data.put("strength_tf", safeText(strength_tf));
        data.put("dexterity_tf", safeText(dexterity_tf));
        data.put("intelligence_tf", safeText(intelligence_tf));
        data.put("luck_tf", safeText(luck_tf));
        data.put("power_tf", safeText(power_tf));

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export Character");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
        chooser.setInitialFileName("character_export.csv");
        Window owner = image_display.getScene() != null ? image_display.getScene().getWindow() : null;
        File file = chooser.showSaveDialog(owner);
        if (file == null) {
            return;
        }

        try (BufferedWriter out = new BufferedWriter(new FileWriter(file, StandardCharsets.UTF_8))) {
            out.write("fxid,value");
            out.newLine();
            for (var e : data.entrySet()) {
                out.write(escapeCsv(e.getKey()));
                out.write(",");
                out.write(escapeCsv(e.getValue()));
                out.newLine();
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private String safeText(TextField tf) {
        return tf == null || tf.getText() == null ? "" : tf.getText();
    }

    private String escapeCsv(String s) {
        if (s == null) {
            return "";
        }
        boolean needQuotes = s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r");
        String v = s.replace("\"", "\"\"");
        return needQuotes ? "\"" + v + "\"" : v;
    }

// ---------- IMPORT ----------
    private void importCharacterFromCsv() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Import Character");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
        Window owner = image_display.getScene() != null ? image_display.getScene().getWindow() : null;
        File file = chooser.showOpenDialog(owner);
        if (file == null) {
            return;
        }

        try {
            var lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
            // Optional header skip
            int start = 0;
            if (!lines.isEmpty() && lines.get(0).toLowerCase().startsWith("fxid")) {
                start = 1;
            }

            String importedClass = null;
            String importedLevel = null;
            // stash attributes to apply after recompute
            Map<String, String> attrs = new LinkedHashMap<>();

            for (int i = start; i < lines.size(); i++) {
                String line = lines.get(i).trim();
                if (line.isEmpty()) {
                    continue;
                }
                String[] kv = parseCsvLineTwoColumns(line);
                if (kv == null) {
                    continue;
                }
                String key = kv[0];
                String val = kv[1];

                switch (key) {
                    case "player_name" -> {
                        if (player_name != null) {
                            player_name.setText(val);
                    
                        }}
                    case "character_name" -> {
                        if (character_name != null) {
                            character_name.setText(val);
                    
                        }}
                    case "class_selection" ->
                        importedClass = val;
                    case "level_display_field" ->
                        importedLevel = val;

                    case "strength_tf", "dexterity_tf", "intelligence_tf", "luck_tf", "power_tf" -> {
                        attrs.put(key, val);
                    }
                    default -> {
                        /* ignore unknown keys */ }
                }
            }

            // Apply class first (triggers your class logic)
            if (importedClass != null && !importedClass.isBlank() && class_selection != null) {
                class_selection.setValue(importedClass);
                // Your class selection handler should load stats, set icons, reset level=1, etc.
                // If not auto-triggered, you can call: class_selection.getOnAction().handle(new ActionEvent());
            }

            // Then apply level (override whatever the class handler set)
            if (importedLevel != null && !importedLevel.isBlank()) {
                level_display_field.setText(importedLevel);
                updateSkillSlotVisibilityByLevel();
                // Recompute stats from base + cumulative bonuses at this level
                refreshDisplayedStats();
            }

            // Finally, overwrite fields with imported attribute values (exact import snapshot)
            if (!attrs.isEmpty()) {
                if (attrs.containsKey("strength_tf")) {
                    strength_tf.setText(attrs.get("strength_tf"));
                }
                if (attrs.containsKey("dexterity_tf")) {
                    dexterity_tf.setText(attrs.get("dexterity_tf"));
                }
                if (attrs.containsKey("intelligence_tf")) {
                    intelligence_tf.setText(attrs.get("intelligence_tf"));
                }
                if (attrs.containsKey("luck_tf")) {
                    luck_tf.setText(attrs.get("luck_tf"));
                }
                if (attrs.containsKey("power_tf")) {
                    power_tf.setText(attrs.get("power_tf"));
                }
            }

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

// Parse a CSV line with exactly 2 columns: fxid,value
// Supports quoted values with escaped quotes ("") and commas inside quotes.
    private String[] parseCsvLineTwoColumns(String line) {
        // Simple state machine to split first comma not inside quotes
        boolean inQuotes = false;
        StringBuilder a = new StringBuilder();
        StringBuilder b = new StringBuilder();
        StringBuilder cur = a;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
                // Handle doubled quotes inside quotes: "" -> "
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cur.append('"');
                    i++;
                    inQuotes = true; // still inside quotes
                }
            } else if (c == ',' && !inQuotes && cur == a) {
                cur = b; // switch to second column
            } else {
                cur.append(c);
            }
        }
        String k = a.toString().trim();
        String v = b.toString().trim();
        // Strip surrounding quotes if present
        if (k.startsWith("\"") && k.endsWith("\"") && k.length() >= 2) {
            k = k.substring(1, k.length() - 1).replace("\"\"", "\"");
        }
        if (v.startsWith("\"") && v.endsWith("\"") && v.length() >= 2) {
            v = v.substring(1, v.length() - 1).replace("\"\"", "\"");
        }
        if (k.isEmpty()) {
            return null;
        }
        return new String[]{k, v};
    }


    /* ---- Image updaters for portrait ---- */
    private void playerSelectedWarrior() {
        image_display.setImage(new Image(App.class.getResource("images/warrior_image.png").toString()));
    }

    private void playerSelectedWizard() {
        image_display.setImage(new Image(App.class.getResource("images/wizard_image.png").toString()));
    }

    private void playerSelectedBard() {
        image_display.setImage(new Image(App.class.getResource("images/bard_image.png").toString()));
    }

    /* ---- Stats loader from /stats/*.csv ---- */
    private void loadStatsCsv(String csvFileName) {
        // Reset base stats
        baseStr = baseDex = baseInt = baseLuck = basePower = 0;

        try (InputStream in = App.class.getResourceAsStream("stats/" + csvFileName)) {
            if (in == null) {
                System.err.println("Missing stats file: " + csvFileName);
                return;
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
                String line = reader.readLine(); // header
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split(",", -1);
                    if (parts.length < 2) {
                        continue;
                    }

                    String attribute = parts[0].trim().toLowerCase();
                    int value;
                    try {
                        value = Integer.parseInt(parts[1].trim());
                    } catch (NumberFormatException nfe) {
                        value = 0;
                    }

                    switch (attribute) {
                        case "strength" ->
                            baseStr = value;
                        case "dexterity" ->
                            baseDex = value;
                        case "intelligence" ->
                            baseInt = value;
                        case "luck" ->
                            baseLuck = value;
                        case "power" ->
                            basePower = value;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Now that base stats are set, compute displayed stats for current level
        refreshDisplayedStats();
    }

    @FXML
    private void handleLevelUp() {
        int current = parseLevel();
        level_display_field.setText(String.valueOf(current + 1));
        updateSkillSlotVisibilityByLevel();
        refreshDisplayedStats(); // 👈 add this
    }

    @FXML
    private void handleLevelDown() {
        int current = parseLevel();
        if (current > 1) {
            level_display_field.setText(String.valueOf(current - 1));
            updateSkillSlotVisibilityByLevel();
            refreshDisplayedStats(); // 👈 add this
        }
    }

    // helper: safely parse the field
    private int parseLevel() {
        try {
            return Integer.parseInt(level_display_field.getText().trim());
        } catch (NumberFormatException e) {
            return 1; // default if invalid
        }
    }

    // keep layout tight when hidden
    private void setSlotVisible(ImageView slot, boolean visible) {
        slot.setVisible(visible);
        slot.setManaged(visible);
    }

    private void updateSkillSlotVisibilityByLevel() {
        int lvl = parseLevel();

        setSlotVisible(skill_slot_two, lvl >= UNLOCK_SLOT_2);
        setSlotVisible(skill_slot_three, lvl >= UNLOCK_SLOT_3);
        setSlotVisible(skill_slot_four, lvl >= UNLOCK_SLOT_4);
    }

    private void resetSkillSlotsVisibility() {
        // slot 1 always visible at start
        setSlotVisible(skill_slot_one, true);
        setSlotVisible(skill_slot_two, false);
        setSlotVisible(skill_slot_three, false);
        setSlotVisible(skill_slot_four, false);
    }

    /* ========= Skill icon loader (lists actual files) ========= */
    // Called when class changes; lists first 4 image files under skill_icons/<className>/
    private void updateSkillIconsForClass(String className) {
        String dir = "skill_icons/" + className.toLowerCase() + "/";
        List<String> icons = listResourceFiles(dir, ext -> {
            String low = ext.toLowerCase();
            return low.endsWith(".png") || low.endsWith(".jpg") || low.endsWith(".jpeg");
        });

        // Sort by filename for consistent order, then limit to 4
        icons.sort(Comparator.naturalOrder());
        while (icons.size() < 4) {
            icons.add(null); // pad to 4 if fewer files
        }
        setSkillIcon(skill_slot_one, icons.get(0) == null ? null : dir + icons.get(0));
        setSkillIcon(skill_slot_two, icons.get(1) == null ? null : dir + icons.get(1));
        setSkillIcon(skill_slot_three, icons.get(2) == null ? null : dir + icons.get(2));
        setSkillIcon(skill_slot_four, icons.get(3) == null ? null : dir + icons.get(3));

        resetSkillSlotsVisibility();
    }

    // List file names inside a resource directory. Works for "file:" and "jar:" URLs.
    private List<String> listResourceFiles(String resourceDir, java.util.function.Predicate<String> nameFilter) {
        List<String> names = new ArrayList<>();
        try {
            URL url = App.class.getResource(resourceDir);
            if (url == null) {
                System.err.println("Resource directory not found: " + resourceDir);
                return names;
            }
            String protocol = url.getProtocol();

            if ("file".equals(protocol)) {
                // Development / Maven run: directory exists on filesystem
                URI uri = url.toURI();
                try (var stream = Files.list(Path.of(uri))) {
                    stream.filter(Files::isRegularFile)
                            .map(p -> p.getFileName().toString())
                            .filter(nameFilter)
                            .forEach(names::add);
                }
            } else if ("jar".equals(protocol)) {
                // Running from packaged JAR
                JarURLConnection conn = (JarURLConnection) url.openConnection();
                try (JarFile jar = conn.getJarFile()) {
                    String prefix = conn.getEntryName();
                    Enumeration<JarEntry> entries = jar.entries();
                    while (entries.hasMoreElements()) {
                        JarEntry e = entries.nextElement();
                        String name = e.getName();
                        if (!e.isDirectory() && name.startsWith(prefix)) {
                            String justName = name.substring(prefix.length());
                            // only direct children (avoid subfolders)
                            if (!justName.isEmpty() && !justName.contains("/")) {
                                if (nameFilter.test(justName)) {
                                    names.add(justName);
                                }
                            }
                        }
                    }
                }
            } else {
                // Fallback: try to read as a directory listing (often not available)
                System.err.println("Unsupported protocol for listing: " + protocol + " (" + resourceDir + ")");
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return names;
    }

    // Set an ImageView image and install a tooltip showing the file's base name
    private void setSkillIcon(ImageView slot, String resourcePath) {
        if (resourcePath == null) {
            slot.setImage(null);
            // We can't directly remove unknown existing tooltip instance; installing a new one with null text is pointless.
            // Typically, re-installing replaces the previous one, so we just skip tooltip when clearing.
            return;
        }
        URL url = App.class.getResource(resourcePath);
        if (url != null) {
            slot.setImage(new Image(url.toString()));
            Tooltip.install(slot, new Tooltip(prettyFileName(resourcePath)));
        } else {
            slot.setImage(null);
        }
    }

    private String prettyFileName(String resourcePath) {
        int slash = resourcePath.lastIndexOf('/');
        String file = (slash >= 0) ? resourcePath.substring(slash + 1) : resourcePath;
        int dot = file.lastIndexOf('.');
        if (dot > 0) {
            file = file.substring(0, dot);
        }
        file = file.replace('_', ' ').replace('-', ' ');
        String[] parts = file.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) {
                continue;
            }
            sb.append(Character.toUpperCase(p.charAt(0)));
            if (p.length() > 1) {
                sb.append(p.substring(1));
            }
            sb.append(' ');
        }
        return sb.toString().trim();
    }

    /* ========= Misc ========= */
    private void clearStatsFields() {
        strength_tf.clear();
        dexterity_tf.clear();
        intelligence_tf.clear();
        luck_tf.clear();
        power_tf.clear();
    }

    private void loadClassList() {
        try (InputStream in = App.class.getResourceAsStream("class_list.txt")) {
            if (in == null) {
                System.err.println("class_list.txt not found in resources");
                return;
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String className = line.trim();
                    if (!className.isEmpty()) {
                        class_selection.getItems().add(className);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void refreshDisplayedStats() {
        String cls = class_selection.getValue();
        if (cls == null || cls.isBlank()) {
            return;
        }

        int lvl = Math.max(1, parseLevel());

        int sumStr = 0, sumDex = 0, sumInt = 0, sumLuck = 0, sumPower = 0;

        // Adjust if you rename the folder (e.g., to level_bonuses)
        String path = "/com/level bonuses/" + cls.toLowerCase() + "_level_bonuses.csv";

        try (InputStream in = PrimaryController.class.getResourceAsStream(path)) {
            if (in != null) {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(in))) {
                    String line = br.readLine(); // header
                    while ((line = br.readLine()) != null) {
                        String[] parts = line.split(",", -1);
                        if (parts.length < 6) {
                            continue;
                        }

                        int levelVal = Integer.parseInt(parts[0].trim());
                        if (levelVal <= lvl) {
                            sumStr += parseOrZero(parts[1]);
                            sumDex += parseOrZero(parts[2]);
                            sumInt += parseOrZero(parts[3]);
                            sumLuck += parseOrZero(parts[4]);
                            sumPower += parseOrZero(parts[5]);
                        }
                    }
                }
            }
        } catch (Exception e) {
            // swallow or log minimally if you prefer
        }

        setStatsFields(
                baseStr + sumStr,
                baseDex + sumDex,
                baseInt + sumInt,
                baseLuck + sumLuck,
                basePower + sumPower
        );
    }

    private void setStatsFields(int s, int d, int i, int l, int p) {
        strength_tf.setText(Integer.toString(s));
        dexterity_tf.setText(Integer.toString(d));
        intelligence_tf.setText(Integer.toString(i));
        luck_tf.setText(Integer.toString(l));
        power_tf.setText(Integer.toString(p));
    }

    private int parseOrZero(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    /* ========= Init ========= */
    @FXML
    private void initialize() {
        if (level_display_field.getText() == null || level_display_field.getText().isBlank()) {
            level_display_field.setText("1");
        }

        if (save_mi != null) {
            save_mi.setOnAction(e -> exportCharacterToCsv());
        }
        if (load_mi != null) {
            load_mi.setOnAction(e -> importCharacterFromCsv());
        }

        if (class_selection != null && class_selection.getItems().isEmpty()) {
            loadClassList();
        }

        class_selection.setOnAction(event -> {
            String choice = class_selection.getValue();
            if (choice == null) {
                return;
            }

            switch (choice) {
                case "Warrior" -> {
                    playerSelectedWarrior();
                    loadStatsCsv("warrior_stats.csv");
                }
                case "Wizard" -> {
                    playerSelectedWizard();
                    loadStatsCsv("wizard_stats.csv");
                }
                case "Bard" -> {
                    playerSelectedBard();
                    loadStatsCsv("bard_stats.csv");
                }
                default -> {
                    image_display.setImage(null);
                    clearStatsFields();
                }
            }

            // Always refresh skill icons based on actual files present
            updateSkillIconsForClass(choice);
            level_display_field.setText("1");
            resetSkillSlotsVisibility();          // hide 2–4 again
            updateSkillSlotVisibilityByLevel();   // enforce current level rules
            refreshDisplayedStats();

        });
    }
}

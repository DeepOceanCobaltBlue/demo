package com.example;

/* Christopher Peters
 *  This program runs a D&D style simplified character creation sheet
 *  
 * 
 */
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

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.FileChooser;
import javafx.stage.Window;

public class PrimaryController {

    /* JavaFX GUI interactable components */
    @FXML    private ComboBox<String> class_selection;
    @FXML    private ImageView image_display;
    @FXML    private TextField player_name;
    @FXML    private TextField character_name;
    @FXML    private MenuItem save_mi;
    @FXML    private MenuItem load_mi;
    @FXML    private MenuItem readme_mi;
    @FXML    private MenuItem close_mi;
    @FXML    private TextField level_display_field;
    @FXML    private javafx.scene.control.Button up_level_btn;
    @FXML    private javafx.scene.control.Button down_level_btn;
    @FXML    private ImageView skill_slot_one;
    @FXML    private ImageView skill_slot_two;
    @FXML    private ImageView skill_slot_three;
    @FXML    private ImageView skill_slot_four;
    @FXML    private TextField strength_tf;
    @FXML    private TextField dexterity_tf;
    @FXML    private TextField intelligence_tf;
    @FXML    private TextField luck_tf;
    @FXML    private TextField power_tf;

    private int baseStr, baseDex, baseInt, baseLuck, basePower;
    private static final int UNLOCK_SLOT_2 = 5;
    private static final int UNLOCK_SLOT_3 = 10;
    private static final int UNLOCK_SLOT_4 = 15;

    /* ---------- EXPORT ---------- */
    /**
     * This function is used to save/export a character sheet as a CSV file. All
     * fields are added to a LinkedHashMap such that the name of the
     * component(fxid) is the key and the value contained in the component is
     * the value. (key, value). Writes only: player_name, character_name,
     * class_selection, level_display_field. attribute values are calculated on
     * import
     *
     */
    private void exportCharacterToCsv() {
        /* Player data to be saved (no attribute fields) */
        Map<String, String> data = new LinkedHashMap<>();
        data.put("player_name", safeText(player_name));
        data.put("character_name", safeText(character_name));
        data.put("class_selection", class_selection == null ? "" : String.valueOf(class_selection.getValue()));
        data.put("level_display_field", safeText(level_display_field));

        /* Setup FileChooser */
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export Character");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
        chooser.setInitialFileName(safeText(character_name) + " sheet");
        Window owner = image_display.getScene() != null ? image_display.getScene().getWindow() : null;
        File file = chooser.showSaveDialog(owner);
        if (file == null) {
            return;
        }

        /* Write CSV */
        try (BufferedWriter out = new BufferedWriter(new FileWriter(file, StandardCharsets.UTF_8))) {
            out.write("fxid,value"); // header
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

    /**
     * Helper function: Returns text from a textfield or converts null strings
     * to empty strings
     *
     * @param tf - textfield containing text to be extracted
     * @return the extracted text as a string
     */
    private String safeText(TextField tf) {
        return tf == null || tf.getText() == null ? "" : tf.getText();
    }

    /**
     * Helper function: Modify null string to be an empty string. Modify certain
     * characters in the string to be safe for export to CSV.
     *
     * @param s - string to be checked
     * @return safe string
     */
    private String escapeCsv(String s) {
        if (s == null) {
            return "";
        }
        boolean needQuotes = s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r");
        String v = s.replace("\"", "\"\"");
        return needQuotes ? "\"" + v + "\"" : v;
    }


    /* ---------- IMPORT ---------- */
    /**
     * Loads a character CSV exported by this app. Restores only: player_name,
     * character_name, class_selection, level_display_field. Attributes are NOT
     * read from file; they are recalculated from class/level.
     */
    private void importCharacterFromCsv() {
        /* Setup FileChooser */
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Import Character");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
        Window owner = image_display.getScene() != null ? image_display.getScene().getWindow() : null;
        File file = chooser.showOpenDialog(owner);
        if (file == null) {
            return;
        }

        /* Attempt to import */
        try {
            var lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);

            // skip header if present
            int start = (!lines.isEmpty() && lines.get(0).toLowerCase().startsWith("fxid")) ? 1 : 0;

            String importedClass = null;
            String importedLevel = null;
            String importedPlayer = null;
            String importedCharacter = null;

            for (int i = start; i < lines.size(); i++) {
                String line = lines.get(i).trim();
                if (line.isEmpty()) {
                    continue; // no error handling
                }
                String[] kv = parseCsvLineTwoColumns(line);
                if (kv == null) {
                    continue; // no error handling
                }
                String key = kv[0];
                String val = kv[1];

                switch (key) {
                    case "player_name" ->
                        importedPlayer = val;
                    case "character_name" ->
                        importedCharacter = val;
                    case "class_selection" ->
                        importedClass = val;
                    case "level_display_field" ->
                        importedLevel = val;
                    default -> {
                        /* ignore unknown keys */ } // no error handling
                }
            }

            // Apply player name
            if (importedPlayer != null && player_name != null) { // name is not null and component is initialized
                player_name.setText(importedPlayer);
            }

            // Apply character name
            if (importedCharacter != null && character_name != null) {
                character_name.setText(importedCharacter);
            }

            // Apply class 
            if (importedClass != null && !importedClass.isBlank() && class_selection != null) {
                class_selection.setValue(importedClass);
            }

            // Apply level, update skill slot visibility, and compute & update attribute values
            if (importedLevel != null && !importedLevel.isBlank()) {
                level_display_field.setText(importedLevel);
                updateSkillSlotVisibilityByLevel();
                refreshDisplayedStats();
            }

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Helper function: This function is used to seperate the key and value
     * pairs from an imported character sheet. supports strings that contain
     * quotation marks key - fxid of target component to update value - text/int
     * value to populate component with
     *
     * @param line - a line read from a csv save file
     * @return - String[] of
     */
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

    /**
     * Set the portrait image for the given class name. Defaults to
     * blank_image.png if the class name is missing or no matching portrait
     * exists.
     */
    private void setPortraitForClass(String className) {
        String path;

        if (className == null || className.isBlank()) {
            path = "images/blank_image.png";
        } else {
            String slug = className.toLowerCase().replace(' ', '_');
            path = "images/" + slug + "_image.png";
        }

        URL url = App.class.getResource(path);
        if (url == null) {
            // if the specific portrait doesn't exist, also fallback to blank
            url = App.class.getResource("images/blank_image.png");
        }

        image_display.setImage(url != null ? new Image(url.toString()) : null);
    }

    /**
     * This function will load base stats for any class using stats csv. If
     * className is blank or the file is missing, base stats are reset to 0.
     * Always calls refreshDisplayedStats() at the end to update stats based on
     * character level.
     */
    private void loadStatsForClass(String className) {
        // reset base values
        baseStr = baseDex = baseInt = baseLuck = basePower = 0;

        if (className != null && !className.isBlank()) {
            String slug = className.toLowerCase().replace(' ', '_'); // no spaces in file names
            String resource = "stats/" + slug + "_stats.csv";

            try (InputStream in = App.class.getResourceAsStream(resource)) {
                if (in != null) {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
                        String line = reader.readLine(); // header
                        while ((line = reader.readLine()) != null) {
                            String[] parts = line.split(",", -1);
                            if (parts.length < 2) { // no unexpected elements present
                                continue;   // no error handling
                            }

                            // [0] - attribute name
                            // [1] - attribute value
                            String attribute = parts[0].trim().toLowerCase();
                            int value = parseOrZero(parts[1].trim());

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
                } else {
                    // missing file: keep zeros
                }
            } catch (Exception e) {
                // keep zeros on error
            }
        }

        refreshDisplayedStats();
    }

    /* ---------- LEVEL UP/DOWN ACTIONS ---------- */
    /**
     * This function updates character sheet following the user increasing
     * character level by 1. This includes attribute values and skill slot
     * visibilty.
     */
    @FXML
    private void handleLevelUp() {
        int current = parseLevel();
        level_display_field.setText(String.valueOf(current + 1));
        updateSkillSlotVisibilityByLevel();
        refreshDisplayedStats();
    }

    /**
     * This function updates character sheet following the user decreasing
     * character level by 1. This includes attribute values and skill slot
     * visibilty.
     */
    @FXML
    private void handleLevelDown() {
        int current = parseLevel();
        if (current > 1) {
            level_display_field.setText(String.valueOf(current - 1));
            updateSkillSlotVisibilityByLevel();
            refreshDisplayedStats();
        }
    }

    /**
     * Helper function perform a try/catch block on parsing an integer from the
     * level display textfield default value returned is 1
     *
     * @return 1 or current level
     */
    private int parseLevel() {
        try {
            return Integer.parseInt(level_display_field.getText().trim());
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    /**
     * Helper function This function will update skill slot visibility
     */
    private void setSlotVisible(ImageView slot, boolean visible) {
        slot.setVisible(visible);
    }

    /**
     * Helper function This function updates which skill slots are visible based
     * on character level and default unlock level
     */
    private void updateSkillSlotVisibilityByLevel() {
        int lvl = parseLevel();

        setSlotVisible(skill_slot_two, lvl >= UNLOCK_SLOT_2);
        setSlotVisible(skill_slot_three, lvl >= UNLOCK_SLOT_3);
        setSlotVisible(skill_slot_four, lvl >= UNLOCK_SLOT_4);
    }

    /**
     * Helper function This function set skill slot visibility to default
     * configuration
     */
    private void resetSkillSlotsVisibility() {
        // slot 1 always visible at start
        setSlotVisible(skill_slot_one, true);
        setSlotVisible(skill_slot_two, false);
        setSlotVisible(skill_slot_three, false);
        setSlotVisible(skill_slot_four, false);
    }

    /**
     * This function updates all skill slot icons when a new class is selected.
     * Limited to 4 icons even if more exist in folder, sorted by filename.
     * Calls to update which skills are visible based on character level.
     *
     * @param className - used to retrieve image files for relevant skill icons
     */
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
                System.err.println("Unsupported protocol for listing: " + protocol + " (" + resourceDir + ")");
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return names;
    }

    /**
     * Helper function This function sets an ImageView image and install a
     * tooltip showing the file's base name
     *
     * @param slot - the ImageView to be updated
     * @param resourcePath - the path of the image to put into the imageview
     */
    private void setSkillIcon(ImageView slot, String resourcePath) {
        if (resourcePath == null) {
            slot.setImage(null); // clear existing tooltip
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

    /**
     * Helper function This function takes the name of the image file and uses
     * it to create a tooltip that displays the name when the user hovers over
     * that skill slot image. String is modified from example_name to Example
     * Name.
     *
     * @param resourcePath
     * @return
     */
    private String prettyFileName(String resourcePath) {
        // substitute regex's with spaces
        int slash = resourcePath.lastIndexOf('/');
        String file = (slash >= 0) ? resourcePath.substring(slash + 1) : resourcePath;
        int dot = file.lastIndexOf('.');
        if (dot > 0) {
            file = file.substring(0, dot);
        }
        file = file.replace('_', ' ').replace('-', ' ');
        // split string at spaces
        String[] parts = file.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) {
                continue;
            }
            sb.append(Character.toUpperCase(p.charAt(0))); // capitalize first char of each word
            if (p.length() > 1) {
                sb.append(p.substring(1));
            }
            sb.append(' ');
        }
        return sb.toString().trim();
    }

    /**
     * This function reads the file 'class_list.txt' which contains the list of
     * available classes to be used as selectable options for character
     * creation.
     */
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

    /**
     * This function is used to read a character level bonuses csv and calculate
     * the total value of each attribute. Then the components that display that
     * value are updated with the calculated value. Character class base values
     * for attributes are included in the final sum.
     */
    private void refreshDisplayedStats() {
        String cls = class_selection.getValue();
        if (cls == null || cls.isBlank()) {
            return;
        }

        int lvl = Math.max(1, parseLevel());
        int sumStr = 0, sumDex = 0, sumInt = 0, sumLuck = 0, sumPower = 0;
        String path = "/com/level bonuses/" + cls.toLowerCase() + "_level_bonuses.csv"; // ex resources/com/level bonuses/bard_level_bonuses.csv

        /* Read in attribute level bonuses up to the character current level from class level bonuses csv & sum values */
        try (InputStream in = PrimaryController.class.getResourceAsStream(path)) {
            if (in != null) {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(in))) {
                    String line = br.readLine(); // header, skip to next line
                    while ((line = br.readLine()) != null) {
                        String[] parts = line.split(",", -1);
                        // verify expected number of elements(6) (no error handling)
                        if (parts.length < 6) {
                            continue;
                        }

                        // this level grants 'class' certain bonuses to attributes
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

        // update component values with calculated character stats
        setStatsFields(
                baseStr + sumStr,
                baseDex + sumDex,
                baseInt + sumInt,
                baseLuck + sumLuck,
                basePower + sumPower
        );
    }

    /**
     * Helper function This function performs the action of updating the
     * character attribute component fields with the given values.
     *
     * @param s - strength value
     * @param d - dexterity value
     * @param i - intelligence value
     * @param l - luck value
     * @param p - power value
     */
    private void setStatsFields(int s, int d, int i, int l, int p) {
        strength_tf.setText(Integer.toString(s));
        dexterity_tf.setText(Integer.toString(d));
        intelligence_tf.setText(Integer.toString(i));
        luck_tf.setText(Integer.toString(l));
        power_tf.setText(Integer.toString(p));
    }

    /**
     * Helper function This function will perform a try/catch block around a
     * parseInt operation
     *
     * @param s - string to convert to int
     * @return - integer value represented by string
     */
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

        if (close_mi != null) {
            close_mi.setOnAction(e -> Platform.exit());
        }

        if (readme_mi != null) {
            readme_mi.setOnAction(e -> {
                Alert alert = new Alert(AlertType.INFORMATION);
                alert.setTitle("Documentation");
                alert.setHeaderText(null);
                alert.setContentText("See documentation (README.md) for more details.");
                alert.showAndWait();
            });
        }

        if (class_selection != null && class_selection.getItems().isEmpty()) {
            loadClassList();
        }

        class_selection.setOnAction(event -> {
            String choice = class_selection.getValue();

            // Portrait (always show something)
            setPortraitForClass(choice); // falls back to blank_image.png if null/missing

            // Base stats for the class (generalized)
            loadStatsForClass(choice);

            // Skill icons for the class (already generalized by folder)
            if (choice != null && !choice.isBlank()) {
                updateSkillIconsForClass(choice);
            } else {
                // if no class, clear/hide skill slots as a safe default
                setSkillIcon(skill_slot_one, null);
                setSkillIcon(skill_slot_two, null);
                setSkillIcon(skill_slot_three, null);
                setSkillIcon(skill_slot_four, null);
                resetSkillSlotsVisibility();
            }

            // Reset level and enforce unlock rules
            level_display_field.setText("1");
            resetSkillSlotsVisibility();
            updateSkillSlotVisibilityByLevel();

            // No extra refresh needed here: loadStatsCsv() already called refreshDisplayedStats().
        });

    }
}

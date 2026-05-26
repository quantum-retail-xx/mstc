package com.mstc.dashboard;

import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * YAML config loader for species and classes.
 * Unique bidders are counted only when species and class match.
 */
public class YamlConfig {

    public static class SpeciesDef {
        public String name;
        // Types from auction table (IronOre Type = "Acacia", etc.)
        public List<String> types = new ArrayList<>();
    }

    private final List<SpeciesDef> speciesList = new ArrayList<>();

    @SuppressWarnings("unchecked")
    public YamlConfig(Path yamlPath) throws Exception {
        Yaml yaml = new Yaml();
        try (InputStream in = Files.newInputStream(yamlPath)) {
            Map<String, Object> root = yaml.load(in);
            if (root == null) return;
            Object s = root.get("species");
            if (s instanceof List) {
                for (Object o : (List<Object>) s) {
                    if (o instanceof Map) {
                        Map<String, Object> m = (Map<String, Object>) o;
                        SpeciesDef sd = new SpeciesDef();
                        sd.name = (String) m.getOrDefault("name", "");
                        Object typesObj = m.get("types");
                        if (typesObj instanceof List) {
                            for (Object tv : (List<Object>) typesObj) {
                                sd.types.add(String.valueOf(tv));
                            }
                        }
                        speciesList.add(sd);
                    }
                }
            }
        }
    }

    public List<SpeciesDef> getSpeciesList() {
        return speciesList;
    }

    /**
     * Returns true if speciesName and typeName match any configured species definition.
     * Counts unique bidders only within matching species and type.
     */
    public boolean isSpeciesAndTypeMatch(String speciesName, String typeName) {
        if (speciesName == null) return false;
        String sName = speciesName.trim();
        for (SpeciesDef sd : speciesList) {
            if (sd == null) continue;
            if (!sd.name.equalsIgnoreCase(sName)) continue;
            // If species has no type restrictions, allow all types
            if (sd.types == null || sd.types.isEmpty()) return true;
            // Otherwise check if typeName matches one of the configured types
            if (typeName == null || typeName.trim().isEmpty()) return false;
            for (String type : sd.types) {
                if (type != null && type.equalsIgnoreCase(typeName.trim())) return true;
            }
        }
        return false;
    }
}


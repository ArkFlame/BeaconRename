package com.arkflame.flameforge.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class YamlValues {
    private final String rootPath;
    private final ConfigurationSection section;
    private final ValidationReport report;

    public YamlValues(ConfigurationSection section, ValidationReport report) {
        this("", section, report);
    }

    public YamlValues(String path, ConfigurationSection section, ValidationReport report) {
        this.rootPath = path == null ? "" : path;
        this.section = section;
        this.report = report;
    }

    public static YamlValues fromFile(File file, ValidationReport report) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        return new YamlValues("", yaml, report);
    }

    public ConfigurationSection getSection(String path) {
        return section.getConfigurationSection(path);
    }

    public YamlValues sub(String path) {
        ConfigurationSection sub = section.getConfigurationSection(path);
        return new YamlValues(extendPath(path), sub, report);
    }

    public boolean contains(String path) {
        return section.contains(path);
    }

    public String getString(String path, String def) {
        Object value = section.get(path);
        if (value == null) {
            return def;
        }
        if (value instanceof String) {
            return (String) value;
        }
        report.addError(rootPath, path, "Expected string but got " + value.getClass().getSimpleName());
        return def;
    }

    public String getString(String path) {
        return getString(path, null);
    }

    public int getInt(String path, int def) {
        Object value = section.get(path);
        if (value == null) {
            return def;
        }
        if (value instanceof Integer) {
            return (Integer) value;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        report.addError(rootPath, path, "Expected integer but got " + value.getClass().getSimpleName());
        return def;
    }

    public int getInt(String path) {
        return getInt(path, 0);
    }

    public long getLong(String path, long def) {
        Object value = section.get(path);
        if (value == null) {
            return def;
        }
        if (value instanceof Long) {
            return (Long) value;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        report.addError(rootPath, path, "Expected long but got " + value.getClass().getSimpleName());
        return def;
    }

    public long getLong(String path) {
        return getLong(path, 0L);
    }

    public double getDouble(String path, double def) {
        Object value = section.get(path);
        if (value == null) {
            return def;
        }
        if (value instanceof Double) {
            return (Double) value;
        }
        if (value instanceof Float) {
            return ((Float) value).doubleValue();
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        report.addError(rootPath, path, "Expected double but got " + value.getClass().getSimpleName());
        return def;
    }

    public double getDouble(String path) {
        return getDouble(path, 0.0);
    }

    public boolean getBoolean(String path, boolean def) {
        Object value = section.get(path);
        if (value == null) {
            return def;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        report.addError(rootPath, path, "Expected boolean but got " + value.getClass().getSimpleName());
        return def;
    }

    public boolean getBoolean(String path) {
        return getBoolean(path, false);
    }

    public List<String> getStringList(String path, List<String> def) {
        Object value = section.get(path);
        if (value == null) {
            return def;
        }
        if (value instanceof List) {
            List<?> list = (List<?>) value;
            List<String> result = new ArrayList<>(list.size());
            for (int i = 0; i < list.size(); i++) {
                Object item = list.get(i);
                if (item instanceof String) {
                    result.add((String) item);
                } else {
                    report.addError(rootPath, path + "[" + i + "]", "Expected string in list but got " + item.getClass().getSimpleName());
                    result.add(String.valueOf(item));
                }
            }
            return result;
        }
        report.addError(rootPath, path, "Expected list but got " + value.getClass().getSimpleName());
        return def;
    }

    public List<String> getStringList(String path) {
        return getStringList(path, new ArrayList<>());
    }

    public List<?> getList(String path) {
        Object value = section.get(path);
        if (value == null) {
            return null;
        }
        if (value instanceof List) {
            return (List<?>) value;
        }
        report.addError(rootPath, path, "Expected list but got " + value.getClass().getSimpleName());
        return null;
    }

    public Map<String, Object> getValues(String path, boolean deep) {
        Object value = section.get(path);
        if (value == null) {
            return null;
        }
        if (value instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) value;
            return map;
        }
        report.addError(rootPath, path, "Expected section/map but got " + value.getClass().getSimpleName());
        return null;
    }

    public int getSchemaVersion(String path) {
        return getInt(path, -1);
    }

    private String extendPath(String path) {
        if (rootPath.isEmpty()) {
            return path;
        }
        return rootPath + "." + path;
    }

    public String getRootPath() {
        return rootPath;
    }

    public ConfigurationSection getRawSection() {
        return section;
    }
}

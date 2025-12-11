package net.democracycraft.elections.src.util.yml;

import net.democracycraft.elections.Elections;
import net.democracycraft.elections.src.util.config.DataFolder;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * AutoYML is a small reflection-based utility to serialize/deserialize simple Java POJOs
 * to and from YAML files using SnakeYAML.
 *
 * Optimization improvements:
 * - Added caching for Reflection fields to reduce overhead on repeated saves/loads.
 * - Enforced UTF-8 encoding for file I/O.
 * - Optimized primitive parsing to avoid unnecessary String object creation.
 *
 * @param <T> Root data type (recommended to implement Serializable)
 */
public class AutoYML<T extends Serializable> {

    // Caches to prevent expensive reflection lookups on every IO operation
    private static final Map<Class<?>, List<Field>> FIELD_CACHE = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Constructor<?>> CONSTRUCTOR_CACHE = new ConcurrentHashMap<>();

    private final Class<T> clazz;
    private final File file;
    private final String header;
    private final Yaml yaml;
    private final Object ioLock = new Object();

    public AutoYML(Class<T> clazz, File file, String header) {
        this.clazz = clazz;
        this.file = file;
        this.header = header;

        DumperOptions opts = new DumperOptions();
        opts.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        opts.setPrettyFlow(true);
        opts.setIndent(2);
        opts.setWidth(120);
        this.yaml = new Yaml(opts);

        ensureParent();
    }

    private void ensureParent() {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            if (!parent.mkdirs() && !parent.exists()) {
                Elections.getInstance().getLogger().log(Level.WARNING,
                        "Could not create parent directories for: " + parent.getAbsolutePath());
            }
        }
    }

    public boolean exists() {
        synchronized (ioLock) {
            return file.exists();
        }
    }

    public boolean delete() {
        synchronized (ioLock) {
            return file.delete();
        }
    }

    public T load() {
        synchronized (ioLock) {
            if (!file.exists()) return null;
            // Use InputStreamReader with explicit UTF-8
            try (InputStreamReader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
                Object raw = yaml.load(reader);
                if (!(raw instanceof Map)) return null;
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) raw;
                return buildFromMap(map, clazz);
            } catch (Exception e) {
                Elections.getInstance().getLogger().log(Level.SEVERE, "Failed to load YAML: " + file, e);
                return null;
            }
        }
    }

    public T loadOrCreate(Supplier<T> defaultSupplier) {
        synchronized (ioLock) {
            T result = load();
            if (result == null) {
                result = defaultSupplier.get();
                save(result);
            }
            return result;
        }
    }

    public void save(T obj) {
        synchronized (ioLock) {
            Map<String, Object> map = toMap(obj);
            // Use OutputStreamWriter with explicit UTF-8
            try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
                if (header != null && !header.isEmpty()) {
                    for (String line : header.split("\n")) {
                        writer.write("# " + line + "\n");
                    }
                    writer.write("\n");
                }
                yaml.dump(map, writer);
            } catch (IOException e) {
                Elections.getInstance().getLogger().log(Level.SEVERE, "Failed to save YAML: " + file, e);
            }
        }
    }

    private <X> X buildFromMap(Map<String, Object> map, Class<X> target) {
        try {
            Constructor<?> ctor = CONSTRUCTOR_CACHE.computeIfAbsent(target, t -> {
                try {
                    Constructor<?> c = t.getDeclaredConstructor();
                    c.setAccessible(true);
                    return c;
                } catch (NoSuchMethodException e) {
                    throw new RuntimeException("No no-arg constructor for " + t.getName(), e);
                }
            });

            @SuppressWarnings("unchecked")
            X instance = (X) ctor.newInstance();

            for (Field field : getCachedFields(target)) {
                String name = field.getName();
                if (!map.containsKey(name)) continue;

                Object raw = map.get(name);
                Object converted = convertValue(raw, field.getGenericType());

                if (converted == null && field.getType().isPrimitive()) continue;

                try {
                    field.set(instance, converted);
                } catch (Exception setEx) {
                    Elections.getInstance().getLogger().log(Level.WARNING,
                            "Could not set field '" + name + "' on " + target.getSimpleName(), setEx);
                }
            }
            return instance;
        } catch (Exception e) {
            throw new RuntimeException("Cannot build instance of " + target.getSimpleName(), e);
        }
    }

    private Object convertValue(Object raw, Type type) {
        if (raw == null) return null;

        if (type instanceof Class<?> pClass) {
            if (pClass == String.class) return String.valueOf(raw);
            if (pClass == Integer.class || pClass == int.class) return toInteger(raw);
            if (pClass == Long.class || pClass == long.class) return toLong(raw);
            if (pClass == Double.class || pClass == double.class) return toDouble(raw);
            if (pClass == Float.class || pClass == float.class) return toFloat(raw);
            if (pClass == Short.class || pClass == short.class) return toShort(raw);
            if (pClass == Byte.class || pClass == byte.class) return toByte(raw);
            if (pClass == Character.class || pClass == char.class) return toChar(raw);
            if (pClass == Boolean.class || pClass == boolean.class) return toBoolean(raw);

            if (pClass.isEnum()) {
                if (pClass.isInstance(raw)) return raw;
                String s = String.valueOf(raw);
                for (Object c : pClass.getEnumConstants()) {
                    if (((Enum<?>) c).name().equalsIgnoreCase(s)) {
                        return c;
                    }
                }
                return null;
            }

            if (pClass.isArray()) {
                Class<?> comp = pClass.getComponentType();
                if (raw instanceof Collection<?>) {
                    Collection<?> coll = (Collection<?>) raw;
                    Object array = Array.newInstance(comp, coll.size());
                    int i = 0;
                    for (Object item : coll) {
                        Array.set(array, i++, convertValue(item, comp));
                    }
                    return array;
                }
                return null;
            }

            if (raw instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) raw;
                return buildFromMap(map, pClass);
            }

            if (pClass.isInstance(raw)) return raw;
            return String.valueOf(raw);
        }

        if (type instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType) type;
            Type rawType = pt.getRawType();

            if (rawType instanceof Class<?>) {
                Class<?> rawClass = (Class<?>) rawType;

                if (List.class.isAssignableFrom(rawClass)) {
                    Type argType = pt.getActualTypeArguments()[0];
                    if (!(raw instanceof Collection)) return null;
                    return ((Collection<?>) raw).stream()
                            .map(item -> convertValue(item, argType))
                            .collect(Collectors.toList());
                }

                if (Set.class.isAssignableFrom(rawClass)) {
                    Type argType = pt.getActualTypeArguments()[0];
                    if (!(raw instanceof Collection)) return null;
                    Collection<?> rawColl = (Collection<?>) raw;
                    Set<Object> set = new LinkedHashSet<>(Math.max((int) (rawColl.size() / .75f) + 1, 16));
                    for (Object item : rawColl) set.add(convertValue(item, argType));
                    return set;
                }

                if (Map.class.isAssignableFrom(rawClass)) {
                    Type keyType = pt.getActualTypeArguments()[0];
                    Type valType = pt.getActualTypeArguments()[1];
                    if (!(raw instanceof Map)) return null;
                    @SuppressWarnings("unchecked")
                    Map<Object, Object> in = (Map<Object, Object>) raw;
                    Map<Object, Object> out = new LinkedHashMap<>(in.size());
                    for (Map.Entry<Object, Object> e : in.entrySet()) {
                        out.put(convertMapKey(e.getKey(), keyType), convertValue(e.getValue(), valType));
                    }
                    return out;
                }
            }
        }
        return raw;
    }

    private Map<String, Object> toMap(Object obj) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Field field : getCachedFields(obj.getClass())) {
            try {
                Object value = field.get(obj);
                result.put(field.getName(), serializeValue(value));
            } catch (IllegalAccessException e) {
                Elections.getInstance().getLogger().log(Level.WARNING,
                        "Could not read field '" + field.getName() + "' from " + obj.getClass().getSimpleName(), e);
            }
        }
        return result;
    }

    private Object serializeValue(Object value) {
        if (value == null) return null;

        if (value instanceof String || value instanceof Number || value instanceof Boolean)
            return value;

        if (value instanceof Enum<?>) return ((Enum<?>) value).name();

        if (value.getClass().isArray()) {
            int len = Array.getLength(value);
            List<Object> out = new ArrayList<>(len);
            for (int i = 0; i < len; i++) out.add(serializeValue(Array.get(value, i)));
            return out;
        }

        if (value instanceof Collection<?>) {
            return ((Collection<?>) value).stream()
                    .map(this::serializeValue)
                    .collect(Collectors.toList());
        }

        if (value instanceof Map<?, ?>) {
            Map<?, ?> map = (Map<?, ?>) value;
            Map<String, Object> out = new LinkedHashMap<>(map.size());
            for (Map.Entry<?, ?> e : map.entrySet()) {
                out.put(String.valueOf(e.getKey()), serializeValue(e.getValue()));
            }
            return out;
        }

        return toMap(value);
    }

    public static <T extends Serializable> AutoYML<T> create(
            Class<T> clazz, String fileName, DataFolder dataFolder, String header
    ) {
        File folder = new File(Elections.getInstance().getDataFolder(), dataFolder.getPath());
        if (!folder.exists()) {
            if (!folder.mkdirs() && !folder.exists()) {
                Elections.getInstance().getLogger().log(Level.WARNING,
                        "Could not create data folder: " + folder.getAbsolutePath());
            }
        }
        if (!fileName.endsWith(".yml")) fileName += ".yml";
        return new AutoYML<>(clazz, new File(folder, fileName), header);
    }

    @FunctionalInterface
    public interface Supplier<V> {
        V get();
    }

    // ------------------------- Helpers -------------------------

    private static List<Field> getCachedFields(Class<?> type) {
        return FIELD_CACHE.computeIfAbsent(type, t -> {
            List<Field> fields = new ArrayList<>();
            Class<?> c = t;
            while (c != null && c != Object.class) {
                for (Field f : c.getDeclaredFields()) {
                    int mod = f.getModifiers();
                    if (Modifier.isStatic(mod) || Modifier.isTransient(mod) || f.isSynthetic()) continue;
                    f.setAccessible(true); // Set accessible once upon caching
                    fields.add(f);
                }
                c = c.getSuperclass();
            }
            return fields;
        });
    }

    // Optimized converters: Avoid String instantiation if the type is already correct

    private static Integer toInteger(Object raw) {
        if (raw instanceof Integer) return (Integer) raw;
        if (raw instanceof Number) return ((Number) raw).intValue();
        try { return Integer.parseInt(String.valueOf(raw).trim()); } catch (Exception ignored) { return null; }
    }

    private static Long toLong(Object raw) {
        if (raw instanceof Long) return (Long) raw;
        if (raw instanceof Number) return ((Number) raw).longValue();
        try { return Long.parseLong(String.valueOf(raw).trim()); } catch (Exception ignored) { return null; }
    }

    private static Double toDouble(Object raw) {
        if (raw instanceof Double) return (Double) raw;
        if (raw instanceof Number) return ((Number) raw).doubleValue();
        try { return Double.parseDouble(String.valueOf(raw).trim()); } catch (Exception ignored) { return null; }
    }

    private static Float toFloat(Object raw) {
        if (raw instanceof Float) return (Float) raw;
        if (raw instanceof Number) return ((Number) raw).floatValue();
        try { return Float.parseFloat(String.valueOf(raw).trim()); } catch (Exception ignored) { return null; }
    }

    private static Short toShort(Object raw) {
        if (raw instanceof Short) return (Short) raw;
        if (raw instanceof Number) return ((Number) raw).shortValue();
        try { return Short.parseShort(String.valueOf(raw).trim()); } catch (Exception ignored) { return null; }
    }

    private static Byte toByte(Object raw) {
        if (raw instanceof Byte) return (Byte) raw;
        if (raw instanceof Number) return ((Number) raw).byteValue();
        try { return Byte.parseByte(String.valueOf(raw).trim()); } catch (Exception ignored) { return null; }
    }

    private static Character toChar(Object raw) {
        if (raw instanceof Character) return (Character) raw;
        String s = String.valueOf(raw);
        return s.isEmpty() ? null : s.charAt(0);
    }

    private static Boolean toBoolean(Object raw) {
        if (raw instanceof Boolean) return (Boolean) raw;
        if (raw instanceof Number) return ((Number) raw).intValue() != 0;
        String s = String.valueOf(raw).trim().toLowerCase(Locale.ROOT);
        return switch (s) {
            case "true", "t", "yes", "y", "on", "1" -> true;
            case "false", "f", "no", "n", "off", "0" -> false;
            default -> null;
        };
    }

    private Object convertMapKey(Object key, Type keyType) {
        if (keyType instanceof Class<?>) {
            Class<?> kc = (Class<?>) keyType;
            if (kc == String.class || kc == Object.class) return String.valueOf(key);
            if (kc.isEnum()) {
                if (kc.isInstance(key)) return key;
                String s = String.valueOf(key);
                for (Object c : kc.getEnumConstants()) {
                    if (((Enum<?>) c).name().equalsIgnoreCase(s)) return c;
                }
                return null;
            }
        }
        return String.valueOf(key);
    }
}
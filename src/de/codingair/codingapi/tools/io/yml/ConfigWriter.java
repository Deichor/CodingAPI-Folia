package de.codingair.codingapi.tools.io.yml;

import de.codingair.codingapi.files.ConfigFile;
import de.codingair.codingapi.tools.Location;
import de.codingair.codingapi.tools.io.DataWriter;
import de.codingair.codingapi.tools.io.JSON.JSON;
import de.codingair.codingapi.tools.io.JSON.JSONParser;
import de.codingair.codingapi.tools.io.Serializable;
import de.codingair.codingapi.tools.items.ItemBuilder;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.json.simple.JSONArray;
import org.json.simple.parser.ParseException;

import java.util.*;

public class ConfigWriter implements DataWriter {
    private String prefix;
    private ConfigFile file;

    public ConfigWriter(ConfigFile file) {
        this(file, null);
    }

    public ConfigWriter(ConfigFile file, String prefix) {
        this.file = file;
        this.prefix = prefix == null ? "" : prefix;
    }

    private FileConfiguration c() {
        return file.getConfig();
    }

    private String k(String key) {
        return (prefix.isEmpty() ? "" : prefix + ".") + key;
    }

    public ConfigFile getFile() {
        return this.file;
    }

    public String getPrefix() {
        return prefix;
    }

    @Override
    public Set keySet() {
        Set<String> o = c().getKeys(true);
        Set<String> data = new HashSet<>();

        for(String s : o) {
            if(prefix.isEmpty()) data.add(s);
            else if(s.startsWith(prefix + ".")) data.add(s.replace(prefix + ".", ""));
        }

        return data;
    }

    public void write(Serializable s, String key) {
        String oldPrefix = prefix;
        prefix = k(key);
        s.write(this);
        prefix = oldPrefix;
    }

    public void read(Serializable s, String key) throws Exception {
        String oldPrefix = prefix;
        prefix = k(key);
        s.read(this);
        prefix = oldPrefix;
    }

    @Override
    public Object finalCommit(String key, Object value) {
        Object prev;

        if(value instanceof Serializable) {
            write((Serializable) value, key);
            prev = null;
        } else {
            prev = c().get(k(key));
            c().set(k(key), value);
        }

        return prev;
    }

    @Override
    public Object remove(String key) {
        Object prev = c().get(k(key));
        c().set(k(key), null);
        return prev;
    }

    @Override
    public <T extends Serializable> T getSerializable(String key, Serializable serializable) {
        try {
            read(serializable, key);
        } catch(Exception e) {
            e.printStackTrace();
        }

        return (T) serializable;
    }

    @Override
    public Boolean getBoolean(String key) {
        return c().getBoolean(k(key));
    }

    @Override
    public Integer getInteger(String key) {
        return c().getInt(k(key));
    }

    @Override
    public JSONArray getList(String key) {
        JSONArray array = new JSONArray();
        List l = c().getList(k(key));

        if(l == null) return array;

        array.addAll(l);
        return array;
    }

    @Override
    public Long getLong(String key) {
        return c().getLong(k(key));
    }

    @Override
    public Date getDate(String key) {
        return new Date(c().getLong(k(key)));
    }

    @Override
    public Double getDouble(String key) {
        return c().getDouble(k(key));
    }

    @Override
    public Float getFloat(String key) {
        Double d = getDouble(key);
        return d == null ? null : (Float) d.floatValue();
    }

    @Override
    public <T> T get(String key, T def, boolean raw) {
        Object o = c().get(k(key));

        if(!raw) {
            if(o instanceof Long) {
                long l = (long) o;
                if(l <= Integer.MAX_VALUE && l >= Integer.MIN_VALUE) return (T) (Object) Math.toIntExact(l);
            }

            if(o instanceof String) {
                try {
                    Object result = new JSONParser().parse((String) o);
                    if(result != null) o = result;
                } catch(ParseException ignored) {
                }
            }

            if(o instanceof org.json.simple.JSONObject) return (T) new JSON((org.json.simple.JSONObject) o);
        }

        return o == null ? def : (T) o;
    }

    @Override
    public <T extends Enum<T>> T get(String key, Class<T> def) {
        Object o = c().get(k(key));

        if(o == null) return null;
        if(!(o instanceof String)) throw new IllegalArgumentException("Value isn't a String. Can't search for a enum!");
        String name = (String) o;

        for(T e : def.getEnumConstants()) {
            if(e.name().equals(name)) return e;
        }

        return null;
    }
}

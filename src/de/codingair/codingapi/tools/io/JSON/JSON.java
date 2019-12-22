package de.codingair.codingapi.tools.io.JSON;

import de.codingair.codingapi.tools.io.DataWriter;
import de.codingair.codingapi.tools.io.Serializable;
import org.json.simple.JSONArray;
import org.json.simple.parser.ParseException;

import java.util.*;

public class JSON extends org.json.simple.JSONObject implements DataWriter {
    private String prefix;

    public JSON() {
        this((String) null);
    }

    public JSON(String prefix) {
        this.prefix = prefix == null ? "" : prefix;
    }

    public JSON(Map map) {
        this(map, null);
    }

    public JSON(Map map, String prefix) {
        super(map);
        this.prefix = prefix == null ? "" : prefix;
    }

    @Override
    public String toJSONString() {
        return super.toJSONString().replace("\\\\\\\\\\\\\\\"", "\\7\"");
    }

    public static Collection<String> keySet(String prefix, Map<?, ?> map) {
        if(prefix == null) prefix = "";

        Set<String> set = new HashSet<>();

        for(Object o : map.entrySet()) {
            Entry e = (Entry) o;

            if(e.getValue() instanceof String) {
                try {
                    e.setValue(new JSONParser().parse((String) e.getValue()));
                } catch(ParseException ignored) {
                }
            }

            if(e.getValue() instanceof Map) {
                set.addAll(keySet((prefix.isEmpty() ? "" : prefix + ".") + e.getKey(), (Map<?, ?>) e.getValue()));
            } else {
                set.add((prefix.isEmpty() ? "" : prefix + ".") + e.getKey());
            }
        }

        return set;
    }

    @Override
    public Set<?> keySet() {
        Collection<String> o = keySet("", this);
        Set<String> data = new HashSet<>();

        for(String s : o) {
            if(prefix.isEmpty()) data.add(s);
            else if(s.startsWith(prefix + ".")) data.add(s.replace(prefix + ".", ""));
        }

        return data;
    }

    @Override
    public Object finalCommit(String key, Object value) {
        if(value instanceof Serializable) {
            write((Serializable) value, key);
            return null;
        }

        JSON section = getOrCreateSection(k(key));

        if(section == this) {
            return super.put(key, value);
        } else return section.put(getLastKey(key), value);
    }

    private static Map<?, ?> getSection(Map<?, ?> map, String key) {
        int i = key.indexOf(".");
        if(i == -1) return map;

        Object first = key.substring(0, i);

        Object o = map.get(first);

        if(o == null) {
            o = map.get(removeLastKey(key));
            if(o == null) o = map.get(key);
            if(o == null) return null;

            if(!(o instanceof Map<?, ?>)) return map; //no map found > old usage!
            return JSON.getSection((Map<?, ?>) o, key.substring(i + 1));
        }

        if(!(o instanceof Map<?, ?>)) return map; //no map found > old usage!
        return JSON.getSection((Map<?, ?>) o, key.substring(i + 1));
    }

    private JSON getOrCreateSection(String key) {
        int i = key.indexOf(".");
        if(i == -1) return this;

        String first = key.substring(0, i);

        Object o = super.get(first);
        if(o == null) super.put(first, o = new JSON());

        return ((JSON) o).getOrCreateSection(key.substring(i + 1));
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

    private String k(String key) {
        return (prefix.isEmpty() ? "" : prefix + ".") + key;
    }

    private String getLastKey(String key) {
        if(!key.contains(".")) return key;
        String[] a = key.split("\\.", -1);
        return a[a.length - 1];
    }

    private static String removeLastKey(String key) {
        if(!key.contains(".")) return key;
        return key.substring(0, key.lastIndexOf("."));
    }

    @Override
    public Object remove(String key) {
        return super.remove(key);
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

    public Boolean getBoolean(String key) {
        Boolean b = get(key);
        return b == null ? false : b;
    }

    public Integer getInteger(String key) {
        Object i = get(key);
        return i == null ? 0 : (i instanceof Number ? ((Number) i).intValue() : 0);
    }

    public JSONArray getList(String key) {
        List<?> i = get(key);

        if(i == null) return new JSONArray();
        else if(i instanceof JSONArray) return (JSONArray) i;

        JSONArray array = new JSONArray();
        array.addAll(i);
        return array;
    }

    public Long getLong(String key) {
        return get(key, 0L, true);
    }

    public Date getDate(String key) {
        Long l = getLong(key);
        return l == null ? null : new Date(l);
    }

    public Double getDouble(String key) {
        Double d = get(key);
        return d == null ? 0 : d;
    }

    public Float getFloat(String key) {
        Double d = getDouble(key);
        return d.floatValue();
    }

    public <T> T get(String key, T def, boolean raw) {
        Map<?, ?> map = getSection(this, k(key));
        if(map == null) return null;
        Object o = map.get((Object) getLastKey(key));

        if(o == null && map == this) {
            //old usage
            o = map.get((Object) (prefix.isEmpty() ? key : prefix));

            if(o instanceof String) {
                try {
                    o = new JSONParser().parse((String) o);
                } catch(ParseException ignored) {
                }
            }

            if(o instanceof JSON) {
                o = ((JSON) o).get((Object) key);
            }
        }

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

    public <T extends Enum<T>> T get(String key, Class<T> def) {
        Object o = super.get(key);

        if(o == null) return null;
        if(!(o instanceof String)) throw new IllegalArgumentException("Value isn't a String. Can't search for a enum!");
        String name = (String) o;

        for(T e : def.getEnumConstants()) {
            if(e.name().equals(name)) return e;
        }

        return null;
    }
}

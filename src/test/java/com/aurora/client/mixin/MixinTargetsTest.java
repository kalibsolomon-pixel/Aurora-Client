package com.aurora.client.mixin;

import com.aurora.client.SourceTree;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.lang.invoke.MethodType;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every explicit injector target in this line's mixins names a method that
 * exists. Injectors are {@code require = 0} (ADR 0004), so a stale descriptor
 * fails silently in game: Health Indicators' label and Item Physics' pose
 * both named {@code renderer.state.CameraRenderState} after it moved to
 * {@code renderer.state.level}, and View Bob Amplitude named a {@code bobView}
 * signature that no longer exists; all three quietly never ran (found 2026-11-18).
 *
 * <p>Checks {@code method = "name(desc)"} against the {@code @Mixin} target
 * and {@code target = "Lowner;name(desc)"} / {@code "Lowner;name:Ltype;"}
 * against its owner, both through the class hierarchy. Name-only and
 * wildcard selectors are not checked.
 */
class MixinTargetsTest {
    private static final Path MIXIN_CONFIG = SourceTree.resolve(SourceTree.ADAPTER + "/src/client/resources/aurora.mixins.json");
    private static final Pattern CONSTANT = Pattern.compile("static\\s+final\\s+String\\s+(\\w+)\\s*=\\s*\"([^\"]*)\"\\s*;");
    private static final Pattern IMPORT = Pattern.compile("^import\\s+([\\w.]+)\\.(\\w+)\\s*;", Pattern.MULTILINE);
    private static final Pattern MIXIN = Pattern.compile("@Mixin\\s*\\(([^)]*)\\)");
    private static final Pattern CLASS_LITERAL = Pattern.compile("([A-Z][\\w.]*)\\.class");
    private static final Pattern TARGETS = Pattern.compile("targets\\s*=\\s*(\\{[^}]*}|\"[^\"]*\")");
    private static final Pattern METHOD = Pattern.compile("\\bmethod\\s*=\\s*(\\{[^}]*}|\"[^\"]*\"|[A-Z_][A-Z0-9_]*)");
    private static final Pattern AT_TARGET = Pattern.compile("\\btarget\\s*=\\s*(\"[^\"]*\"|[A-Z_][A-Z0-9_]*)");
    private static final Pattern TOKEN = Pattern.compile("\"([^\"]*)\"|([A-Z_][A-Z0-9_]*)");

    @Test void everyExplicitInjectorTargetExists() throws Exception {
        JsonObject config = JsonParser.parseString(Files.readString(MIXIN_CONFIG)).getAsJsonObject();
        List<String> names = new ArrayList<>();
        for (String key : new String[]{"client", "mixins"}) {
            if (config.has(key)) for (JsonElement e : config.getAsJsonArray(key)) names.add(e.getAsString());
        }
        List<String> problems = new ArrayList<>();
        int checked = 0;
        for (String name : names) {
            String source = SourceTree.read("mixin/" + name.replace('.', '/') + ".java");
            Map<String, String> constants = new HashMap<>();
            Matcher c = CONSTANT.matcher(source);
            while (c.find()) constants.put(c.group(1), c.group(2));
            List<Class<?>> owners = mixinTargets(name, source, problems);
            if (owners == null) continue;

            Matcher m = METHOD.matcher(source);
            while (m.find()) {
                for (String selector : tokens(m.group(1), constants)) {
                    int paren = selector.indexOf('(');
                    if (paren < 0 || selector.contains("*")) continue;
                    checked++;
                    String method = selector.substring(0, paren), desc = selector.substring(paren);
                    if (owners.stream().noneMatch(o -> hasMethod(o, method, desc)))
                        problems.add(name + ": method " + selector + " is not in " + owners);
                }
            }
            Matcher t = AT_TARGET.matcher(source);
            while (t.find()) {
                for (String target : tokens(t.group(1), constants)) {
                    if (!target.startsWith("L") || !target.contains(";")) continue;
                    int semi = target.indexOf(';');
                    String owner = target.substring(1, semi).replace('/', '.'), member = target.substring(semi + 1);
                    if (member.isEmpty() || member.contains("*")) continue;
                    Class<?> cls = load(owner);
                    if (cls == null) {
                        problems.add(name + ": target owner " + owner + " does not exist");
                        continue;
                    }
                    checked++;
                    int paren = member.indexOf('('), colon = member.indexOf(':');
                    boolean ok = paren >= 0
                            ? hasMethod(cls, member.substring(0, paren), member.substring(paren))
                            : colon >= 0 && hasField(cls, member.substring(0, colon), member.substring(colon + 1));
                    if (!ok) problems.add(name + ": target " + target + " does not exist");
                }
            }
        }
        assertTrue(checked > 50, "checked only " + checked + " selectors; the parser lost track of the sources");
        assertTrue(problems.isEmpty(), "Stale mixin selectors (they would silently not apply):\n  " + String.join("\n  ", problems));
    }

    /** The {@code @Mixin} targets, or null for a {@code @Pseudo} mixin into another mod that is absent here. */
    private static List<Class<?>> mixinTargets(String name, String source, List<String> problems) {
        Matcher mixin = MIXIN.matcher(source);
        if (!mixin.find()) {
            problems.add(name + ": no @Mixin annotation found");
            return null;
        }
        Map<String, String> imports = new HashMap<>();
        Matcher i = IMPORT.matcher(source);
        while (i.find()) imports.put(i.group(2), i.group(1) + "." + i.group(2));
        List<String> classNames = new ArrayList<>();
        Matcher literal = CLASS_LITERAL.matcher(mixin.group(1));
        while (literal.find()) {
            String simple = literal.group(1);
            int dot = simple.indexOf('.');
            String head = dot < 0 ? simple : simple.substring(0, dot);
            String fq = imports.get(head);
            if (fq == null) continue;
            classNames.add(dot < 0 ? fq : fq + simple.substring(dot).replace('.', '$'));
        }
        Matcher targets = TARGETS.matcher(mixin.group(1));
        if (targets.find()) for (String t : tokens(targets.group(1), Map.of())) classNames.add(t.replace('/', '.'));
        List<Class<?>> out = new ArrayList<>();
        for (String cn : classNames) {
            Class<?> cls = load(cn);
            if (cls != null) out.add(cls);
            else if (!source.contains("@Pseudo")) problems.add(name + ": mixin target " + cn + " does not exist");
        }
        return out.isEmpty() ? null : out;
    }

    private static List<String> tokens(String expression, Map<String, String> constants) {
        List<String> out = new ArrayList<>();
        Matcher t = TOKEN.matcher(expression);
        while (t.find()) {
            if (t.group(1) != null) out.add(t.group(1));
            else if (constants.containsKey(t.group(2))) out.add(constants.get(t.group(2)));
        }
        return out;
    }

    private static Class<?> load(String name) {
        try {
            return Class.forName(name, false, MixinTargetsTest.class.getClassLoader());
        } catch (ClassNotFoundException | LinkageError e) {
            return null;
        }
    }

    private static boolean hasMethod(Class<?> start, String name, String desc) {
        for (Class<?> c : hierarchy(start)) {
            try {
                List<Executable> members = new ArrayList<>(List.of(c.getDeclaredMethods()));
                if (name.equals("<init>")) members.addAll(List.of(c.getDeclaredConstructors()));
                for (Executable e : members) {
                    boolean named = name.equals("<init>") ? !(e instanceof Method) : e.getName().equals(name);
                    if (!named) continue;
                    Class<?> ret = e instanceof Method method ? method.getReturnType() : void.class;
                    if (MethodType.methodType(ret, e.getParameterTypes()).toMethodDescriptorString().equals(desc)) return true;
                }
            } catch (LinkageError ignored) {
                // A class whose members reference something absent from the test classpath.
            }
            if (name.equals("<init>")) break;
        }
        return false;
    }

    private static boolean hasField(Class<?> start, String name, String desc) {
        for (Class<?> c : hierarchy(start)) {
            try {
                for (var f : c.getDeclaredFields())
                    if (f.getName().equals(name) && f.getType().descriptorString().equals(desc)) return true;
            } catch (LinkageError ignored) {
            }
        }
        return false;
    }

    private static List<Class<?>> hierarchy(Class<?> start) {
        List<Class<?>> out = new ArrayList<>();
        Set<Class<?>> seen = new HashSet<>();
        Deque<Class<?>> queue = new ArrayDeque<>(List.of(start));
        while (!queue.isEmpty()) {
            Class<?> c = queue.poll();
            if (!seen.add(c)) continue;
            out.add(c);
            if (c.getSuperclass() != null) queue.add(c.getSuperclass());
            queue.addAll(List.of(c.getInterfaces()));
        }
        return out;
    }
}

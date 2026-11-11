package com.aurora.client.health;

import org.objectweb.asm.Handle;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Finds injectors that applied to nothing (ADR 0004).
 *
 * <p>Mixin merges every injector handler into the target class, then inserts a
 * call to it at each matching injection point. A handler with no call site in
 * the transformed class therefore matched nothing. Aurora does not let Mixin
 * enforce this with {@code require}: a failed require check throws an
 * {@code Error} that Mixin treats as fatal, so one stale injection point after
 * a Minecraft update would stop the game instead of turning one feature off.
 *
 * <p>An injector marked {@code require = 0} is optional and never reported.
 * MixinExtras injectors ({@code @WrapOperation} and friends) are not judged:
 * MixinExtras applies them after this plugin's {@code postApply} runs, so
 * their call sites do not exist yet here (seen on 26.1 with the armor-trim
 * wrap, which the exported class showed applied).
 */
final class InjectionCheck {
    private InjectionCheck() {}

    private static final String SPONGE_INJECTORS = "Lorg/spongepowered/asm/mixin/injection/";

    /** Names of the mixin's required injectors that no code in {@code target} calls. Empty when all applied. */
    static List<String> unmatched(ClassNode target, ClassNode mixin) {
        List<String> out = new ArrayList<>();
        if (target == null || mixin == null || mixin.methods == null) return out;
        for (MethodNode handler : mixin.methods) {
            AnnotationNode injector = injector(handler);
            if (injector == null || optional(injector)) continue;
            String merged = mergedName(target, handler);
            if (merged == null) continue; // not merged under a name we can recognize: cannot judge
            if (!called(target, merged)) out.add(handler.name);
        }
        return out;
    }

    private static AnnotationNode injector(MethodNode m) {
        for (List<AnnotationNode> list : List.of(nonNull(m.visibleAnnotations), nonNull(m.invisibleAnnotations))) {
            for (AnnotationNode a : list) {
                if (a.desc.startsWith(SPONGE_INJECTORS)) return a;
            }
        }
        return null;
    }

    private static boolean optional(AnnotationNode injector) {
        if (injector.values == null) return false;
        for (int i = 0; i + 1 < injector.values.size(); i += 2) {
            if ("require".equals(injector.values.get(i)) && Integer.valueOf(0).equals(injector.values.get(i + 1))) {
                return true;
            }
        }
        return false;
    }

    /** Mixin renames handlers to {@code prefix$unique$originalName}; some keep the original name. */
    private static String mergedName(ClassNode target, MethodNode handler) {
        String suffix = "$" + handler.name;
        for (MethodNode m : target.methods) {
            if (m.desc.equals(handler.desc) && (m.name.equals(handler.name) || m.name.endsWith(suffix))) return m.name;
        }
        return null;
    }

    private static boolean called(ClassNode target, String name) {
        for (MethodNode m : target.methods) {
            if (m.name.equals(name) || m.instructions == null) continue;
            for (AbstractInsnNode insn : m.instructions) {
                if (insn instanceof MethodInsnNode call && call.name.equals(name) && call.owner.equals(target.name)) {
                    return true;
                }
                if (insn instanceof InvokeDynamicInsnNode indy && indy.bsmArgs != null) {
                    for (Object arg : indy.bsmArgs) {
                        if (arg instanceof Handle h && h.getName().equals(name) && h.getOwner().equals(target.name)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private static <T> List<T> nonNull(List<T> list) {
        return list == null ? List.of() : list;
    }
}

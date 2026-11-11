package com.aurora.client.health;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** An injector whose handler nothing calls matched no injection point (ADR 0004). */
class InjectionCheckTest {
    private static final String DESC = "(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V";

    private static MethodNode handler(String name, Integer require) {
        MethodNode m = new MethodNode(Opcodes.ACC_PRIVATE, name, DESC, null, null);
        AnnotationNode inject = new AnnotationNode("Lorg/spongepowered/asm/mixin/injection/Inject;");
        inject.values = new ArrayList<>(List.of("method", List.of("tick")));
        if (require != null) {
            inject.values.add("require");
            inject.values.add(require);
        }
        m.visibleAnnotations = new ArrayList<>(List.of(inject));
        return m;
    }

    private static ClassNode mixin(MethodNode... handlers) {
        ClassNode c = new ClassNode();
        c.name = "com/aurora/client/mixin/TestMixin";
        c.methods = new ArrayList<>(List.of(handlers));
        return c;
    }

    /** The target after Mixin merged the handlers; {@code calls} are the ones an injection point reached. */
    private static ClassNode target(List<String> merged, List<String> calls) {
        ClassNode c = new ClassNode();
        c.name = "net/minecraft/Target";
        c.methods = new ArrayList<>();
        MethodNode tick = new MethodNode(Opcodes.ACC_PUBLIC, "tick", "()V", null, null);
        for (String call : calls) {
            tick.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, c.name, call, DESC, false));
        }
        tick.instructions.add(new InsnNode(Opcodes.RETURN));
        c.methods.add(tick);
        for (String name : merged) c.methods.add(new MethodNode(Opcodes.ACC_PRIVATE, name, DESC, null, null));
        return c;
    }

    @Test void aHandlerWithACallSiteMatched() {
        String merged = "handler$zza000$aurora$onTick";
        assertEquals(List.of(), InjectionCheck.unmatched(target(List.of(merged), List.of(merged)),
                mixin(handler("aurora$onTick", null))));
    }

    @Test void aHandlerNothingCallsIsReported() {
        String merged = "handler$zza000$aurora$onTick";
        assertEquals(List.of("aurora$onTick"), InjectionCheck.unmatched(target(List.of(merged), List.of()),
                mixin(handler("aurora$onTick", null))));
    }

    @Test void anOptionalInjectorIsNeverReported() {
        String merged = "handler$zza000$aurora$maybe";
        assertEquals(List.of(), InjectionCheck.unmatched(target(List.of(merged), List.of()),
                mixin(handler("aurora$maybe", 0))));
    }

    @Test void anExplicitRequireOfOneIsStillChecked() {
        String merged = "handler$zza000$aurora$needed";
        assertEquals(List.of("aurora$needed"), InjectionCheck.unmatched(target(List.of(merged), List.of()),
                mixin(handler("aurora$needed", 1))));
    }

    @Test void aMethodThatIsNotAnInjectorIsIgnored() {
        MethodNode plain = new MethodNode(Opcodes.ACC_PRIVATE, "aurora$helper", DESC, null, null);
        assertEquals(List.of(), InjectionCheck.unmatched(target(List.of("aurora$helper"), List.of()), mixin(plain)));
    }
}

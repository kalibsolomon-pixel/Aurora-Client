package com.aurora.client.mixin;

import com.aurora.client.SourceTree;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An invoker named like the method it calls makes a public interface method with the target's
 * own signature. A subclass that overrides the target as protected then wins interface dispatch
 * and the JVM throws IllegalAccessError: Better Hitreg crashed on hitting an Enderman
 * (2026-11-18) through {@code EntityAccessor.getHurtSound}.
 */
class MixinInvokerNamingTest {
    private static final Pattern INVOKER = Pattern.compile(
            "@Invoker\\(\\s*(?:value\\s*=\\s*)?\"([A-Za-z0-9_$]+)\"[^)]*\\)\\s*(?:@[A-Za-z.]+(?:\\([^)]*\\))?\\s*)*"
                    + "(?:public\\s+|static\\s+)*[\\w.<>\\[\\], ?]+\\s+([A-Za-z0-9_$]+)\\s*\\(");

    @Test void noInvokerSharesItsTargetsName() throws Exception {
        int checked = 0;
        for (var path : (Iterable<java.nio.file.Path>) SourceTree.javaFiles()::iterator) {
            var matcher = INVOKER.matcher(Files.readString(path));
            while (matcher.find()) {
                checked++;
                assertTrue(!matcher.group(1).equals(matcher.group(2)),
                        path.getFileName() + ": invoker " + matcher.group(2) + " shares its target's name");
            }
        }
        assertTrue(checked >= 1, "named invokers found: " + checked);
    }
}

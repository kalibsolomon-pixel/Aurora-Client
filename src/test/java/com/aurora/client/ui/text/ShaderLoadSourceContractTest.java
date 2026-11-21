package com.aurora.client.ui.text;

import com.aurora.client.SourceTree;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Aurora's shader users wait for the Shader Loader's first apply before judging a compile (RRLS lets screens draw
 * during the first reload, when only the vanilla startup shaders exist). Each line's hook and callers must agree.
 */
class ShaderLoadSourceContractTest {
    private static final String MIXIN = "ShaderManagerAppliedMixin";

    @Test void theHookSetsTheFlagWhenTheApplyReturns() {
        String mixin = SourceTree.read("mixin/" + MIXIN + ".java");
        assertTrue(mixin.contains("@Mixin(ShaderManager.class)"), mixin);
        assertTrue(mixin.contains("@Inject(method = \"apply\", at = @At(\"RETURN\"), require = 1)"), mixin);
        assertTrue(mixin.contains("ShaderLoad.markApplied();"), mixin);
        assertTrue(SourceTree.read("ui/util/ShaderLoad.java").contains("static final String HOOK = \"" + MIXIN + "\";"),
                "ShaderLoad must ask MixinHealth about this mixin by its config name");
    }

    @Test void theHookIsListedOnEveryLine() throws Exception {
        for (String adapter : new String[]{"minecraft-26.1", "minecraft-26.2", "minecraft-26.3"}) {
            String json = Files.readString(Path.of("adapters", adapter, "src/client/resources/aurora.mixins.json"));
            assertTrue(json.contains("\"" + MIXIN + "\""), adapter + " does not list " + MIXIN);
        }
    }

    @Test void sdfTextDecidesOnlyThroughTheSharedCheck() throws Exception {
        for (String adapter : new String[]{"minecraft-26.1", "minecraft-26.2", "minecraft-26.3"}) {
            String source = Files.readString(Path.of("adapters", adapter,
                    "src/client/java/com/aurora/client/ui/text/NativeSdfText.java")).replace("\r\n", "\n");
            assertTrue(source.contains("validated = SdfTextSupport.checkPipeline(NativeSdfText::compiles);"), adapter);
            assertFalse(source.contains("SdfTextSupport.fail("), adapter + " must not judge a compile itself");
        }
        String support = SourceTree.read("ui/text/SdfTextSupport.java");
        assertTrue(support.contains("if (!ShaderLoad.settled()) {"), "the check must wait for the Shader Loader first");
        assertTrue(support.indexOf("ShaderLoad.settled()") < support.indexOf("compiles.getAsBoolean()"));
    }

    @Test void shaderDrawnShapesWaitToo() throws Exception {
        for (String adapter : new String[]{"minecraft-26.1", "minecraft-26.2", "minecraft-26.3"}) {
            String source = Files.readString(Path.of("adapters", adapter,
                    "src/client/java/com/aurora/client/ui/util/NativeShapeRenderer.java")).replace("\r\n", "\n");
            int wait = source.indexOf("if (!ShaderLoad.settled()) return false;");
            assertTrue(wait > 0, adapter + " must not compile Aurora's shape shader before the Shader Loader applied");
            // The wait comes before the compile whose failure sets the latch that gives up for the session.
            assertTrue(wait < source.indexOf("var compiled ="), adapter);
            assertTrue(wait > source.indexOf("if (shaderUnavailable) return false;"), adapter);
        }
    }
}

package com.aurora.client.diagnostics;

import com.aurora.client.feature.FeatureCatalog;
import com.aurora.client.health.FeatureHealth;
import com.aurora.client.health.MixinHealth;

import java.util.List;
import java.util.Locale;

/**
 * Formats the diagnostics report (ADR 0004): plain text a player copies and
 * sends by hand. No Minecraft imports; {@link DiagnosticsReport} gathers the
 * facts that need the game.
 */
public final class DiagnosticsText {
    private DiagnosticsText() {}

    /** Facts about the session that only the running game knows. */
    public record Environment(String auroraVersion, String minecraftVersion, String loaderVersion,
                              String renderBackend, String gpu, String window, String measuredOn,
                              boolean budgetApplies, List<String> settings) {}

    public static String format(Environment env, List<String> mods, FrameBenchmark.Stats stats,
                                List<String> recentErrors) {
        StringBuilder out = new StringBuilder(2048);
        Runtime runtime = Runtime.getRuntime();
        out.append("Aurora Client diagnostics\n");
        out.append("Aurora ").append(env.auroraVersion()).append(" | Minecraft ").append(env.minecraftVersion())
                .append(" | Fabric Loader ").append(env.loaderVersion()).append('\n');
        out.append("Java ").append(System.getProperty("java.version", "?")).append(" | ")
                .append(System.getProperty("os.name", "?")).append(' ').append(System.getProperty("os.version", ""))
                .append(" (").append(System.getProperty("os.arch", "?")).append(") | ")
                .append(runtime.availableProcessors()).append(" CPU threads | ")
                .append(runtime.maxMemory() / (1024 * 1024)).append(" MB max memory\n");
        out.append("Renderer: ").append(env.renderBackend()).append(" | ").append(env.gpu()).append('\n');
        out.append("Window: ").append(env.window()).append('\n');

        appendHealth(out);
        appendBenchmark(out, env, stats);

        out.append("\nRecent Aurora warnings and errors (").append(recentErrors.size()).append(")\n");
        if (recentErrors.isEmpty()) out.append("- none\n");
        for (String line : recentErrors) out.append("- ").append(line).append('\n');

        out.append("\nMods (").append(mods.size()).append(")\n");
        for (String mod : mods) out.append("- ").append(mod).append('\n');

        out.append("\nThis report contains no account, world or server names.\n");
        return scrub(out.toString(), System.getProperty("user.home"), System.getProperty("user.name"));
    }

    private static void appendHealth(StringBuilder out) {
        int total = FeatureCatalog.mixins().size();
        List<MixinHealth.Failure> failures = MixinHealth.failures();
        if (failures.isEmpty()) {
            out.append(MixinHealth.audited()
                    ? "\nAll " + total + " Aurora mixins applied.\n"
                    : "\nNo Aurora mixin has failed so far (startup check not finished).\n");
            return;
        }
        List<String> features = FeatureHealth.affectedFeatures();
        out.append("\nFeatures not fully working (").append(features.size()).append(")\n");
        for (String id : features) {
            FeatureCatalog.Feature feature = FeatureCatalog.feature(id);
            out.append("- ").append(feature == null ? id : feature.name()).append(": ")
                    .append(FeatureHealth.noteFor(id)).append('\n');
        }
        for (FeatureHealth.Issue issue : FeatureHealth.generalIssues()) {
            out.append("- Aurora in general: ").append(issue.provides()).append('\n');
        }
        out.append("\nMixins not working (").append(failures.size()).append(" of ").append(total).append(")\n");
        for (MixinHealth.Failure f : failures) {
            out.append("- ").append(f.mixin()).append(' ').append(f.cause().phrase);
            if (!f.detail().isEmpty()) out.append(" - ").append(f.detail());
            out.append('\n');
        }
    }

    private static void appendBenchmark(StringBuilder out, Environment env, FrameBenchmark.Stats stats) {
        out.append("\nBenchmark");
        if (stats == null) {
            out.append("\n- not available: no frames were measured\n");
        } else {
            out.append(String.format(Locale.ROOT, " (%.1f s, %s)\n", stats.seconds(), env.measuredOn()));
            out.append(String.format(Locale.ROOT, "- %d frames, %.1f fps average, %.1f fps 1%% low\n",
                    stats.frames(), stats.averageFps(), stats.onePercentLowFps()));
            out.append(String.format(Locale.ROOT, "- frame time: %.2f ms median, %.2f ms p95, %.2f ms p99, %.2f ms worst\n",
                    stats.medianMs(), stats.p95Ms(), stats.p99Ms(), stats.maxMs()));
            out.append(String.format(Locale.ROOT, "- Aurora CPU per frame: %.3f ms mean, %.3f ms median, %.3f ms p95\n",
                    stats.auroraMeanMs(), stats.auroraMedianMs(), stats.auroraP95Ms()));
            out.append(String.format(Locale.ROOT, "- Aurora UI fills per frame: %.1f, glass captures per frame: %.1f\n",
                    stats.fillsPerFrame(), stats.glassPerFrame()));
            out.append(budgetLine(env, stats));
        }
        if (!env.settings().isEmpty()) out.append("- settings: ").append(String.join(", ", env.settings())).append('\n');
    }

    /** The ADR 0005 verdict. The budget is defined in a world with no screen open, so only judged there. */
    private static String budgetLine(Environment env, FrameBenchmark.Stats stats) {
        if (!env.budgetApplies()) return "- overhead budget: not judged (only measured in a world with no screen open)\n";
        List<String> over = OverheadBudget.exceeded(stats);
        return over.isEmpty()
                ? String.format(Locale.ROOT, "- overhead budget: within (CPU mean %.2f ms, p95 %.2f ms, no glass, %.0f fills)\n",
                        OverheadBudget.CPU_MEAN_MS, OverheadBudget.CPU_P95_MS, OverheadBudget.FILLS_PER_FRAME)
                : "- overhead budget: OVER: " + String.join(", ", over) + "\n";
    }

    /** Remove the home directory and the account name of the operating-system user. */
    static String scrub(String text, String userHome, String userName) {
        String result = text;
        if (userHome != null && userHome.length() > 3) {
            result = result.replace(userHome, "~").replace(userHome.replace('\\', '/'), "~");
        }
        if (userName != null && userName.length() > 2) result = result.replace(userName, "<user>");
        return result;
    }
}

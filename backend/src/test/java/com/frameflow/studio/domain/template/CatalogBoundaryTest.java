package com.frameflow.studio.domain.template;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.regex.Pattern;
import java.util.spi.ToolProvider;
import org.junit.jupiter.api.Test;
import com.frameflow.studio.domain.template.model.entity.Template;

import static org.assertj.core.api.Assertions.*;

class CatalogBoundaryTest {
    @Test
    void templateAndAssetKeepDomainPureAndCasesAndTriggersWithinTheirBoundaries() throws Exception {
        Path classes = Path.of(Template.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        var output = new StringWriter();
        int exit = ToolProvider.findFirst("jdeps").orElseThrow().run(new PrintWriter(output), new PrintWriter(output),
                "--ignore-missing-deps", "-verbose:class", "-filter:none", "-include",
                "com\\.frameflow\\.studio\\..*\\.(template|asset)\\..*", classes.toString());
        assertThat(exit).as(output.toString()).isZero();
        var pattern = Pattern.compile("^\\s+(com\\.frameflow\\.studio\\.\\S+)\\s+->\\s+(\\S+)\\s+.*$");
        var dependencies = output.toString().lines().map(pattern::matcher).filter(match -> match.matches()).toList();
        assertThat(dependencies).isNotEmpty();
        for (var match : dependencies) {
            String source = match.group(1);
            String target = match.group(2);
            for (String feature : new String[]{"template", "asset"}) {
                if (source.startsWith("com.frameflow.studio.domain." + feature + ".")) {
                    assertThat(target.startsWith("java.") || target.startsWith("com.frameflow.studio.domain." + feature + "."))
                            .as(source + " -> " + target).isTrue();
                }
            }
            if (source.startsWith("com.frameflow.studio.cases.")) {
                assertThat(target).doesNotStartWith("com.frameflow.studio.api.")
                        .doesNotStartWith("com.frameflow.studio.trigger.")
                        .doesNotStartWith("com.frameflow.studio.infrastructure.");
            }
            if (source.startsWith("com.frameflow.studio.trigger.")) {
                assertThat(target).doesNotStartWith("com.frameflow.studio.domain.")
                        .doesNotStartWith("com.frameflow.studio.infrastructure.")
                        .doesNotContain(".impl.");
            }
        }
    }
}

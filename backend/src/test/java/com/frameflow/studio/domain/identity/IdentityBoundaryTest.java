package com.frameflow.studio.domain.identity;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.regex.Pattern;
import java.util.spi.ToolProvider;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Controller;
import com.frameflow.studio.domain.identity.model.entity.Account;

import static org.assertj.core.api.Assertions.assertThat;

class IdentityBoundaryTest {
    @Test
    void compiledIdentityLayersAndInfrastructureRespectTheirDependencyBoundaries() throws Exception {
        Path classes = Path.of(Account.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        StringWriter output = new StringWriter();
        int exit = ToolProvider.findFirst("jdeps").orElseThrow().run(new PrintWriter(output), new PrintWriter(output),
                "--ignore-missing-deps", "-verbose:class", "-filter:none", "-include", "com\\.frameflow\\.studio\\..*", classes.toString());
        assertThat(exit).as(output.toString()).isZero();
        Pattern dependency = Pattern.compile("^\\s+(com\\.frameflow\\.studio\\.\\S+)\\s+->\\s+(\\S+)\\s+.*$");
        var dependencies = output.toString().lines().map(dependency::matcher).filter(m -> m.matches()).toList();
        assertThat(dependencies).isNotEmpty();
        for (var dependencyMatch : dependencies) {
            String source = dependencyMatch.group(1);
            String target = dependencyMatch.group(2);
            if (source.contains(".domain.identity.")) {
                assertThat(target.startsWith("java.") || target.startsWith("com.frameflow.studio.domain.identity."))
                        .as(source + " -> " + target).isTrue();
            } else if (source.startsWith("com.frameflow.studio.api.")) {
                assertThat(target.startsWith("java.") || target.startsWith("com.frameflow.studio.api.")
                        || target.startsWith("com.frameflow.studio.types.")).as(source + " -> " + target).isTrue();
            } else if (source.contains(".cases.identity.")) {
                assertThat(target).doesNotStartWith("com.frameflow.studio.api.")
                        .doesNotStartWith("com.frameflow.studio.infrastructure.")
                        .doesNotStartWith("com.frameflow.studio.trigger.");
            } else if (source.startsWith("com.frameflow.studio.trigger.")) {
                assertThat(target).doesNotStartWith("com.frameflow.studio.infrastructure.")
                        .doesNotStartWith("com.frameflow.studio.domain.")
                        .doesNotStartWith("com.frameflow.studio.cases.identity.impl.");
            } else if (source.startsWith("com.frameflow.studio.infrastructure.")) {
                assertThat(target).as(source + " -> " + target).doesNotStartWith("com.frameflow.studio.api.");
                if (target.startsWith("com.frameflow.studio.cases.")) {
                    boolean dispatchOptions = source.equals("com.frameflow.studio.infrastructure.adapter.port.task.ExecutorTaskDispatcherPort")
                            && target.equals("com.frameflow.studio.cases.task.model.ExecutionOptions");
                    assertThat(dispatchOptions || target.matches("com\\.frameflow\\.studio\\.cases\\.[^.]+\\.port\\..*"))
                            .as(source + " -> " + target).isTrue();
                }
                if (target.startsWith("com.frameflow.studio.trigger.")) {
                    // Only the composition root may wire inbound adapters; Controllers remain entry points.
                    assertThat(source).as(source + " -> " + target)
                            .startsWith("com.frameflow.studio.infrastructure.config.");
                    Class<?> adapter = Class.forName(target, false, Account.class.getClassLoader());
                    assertThat(AnnotatedElementUtils.hasAnnotation(adapter, Controller.class))
                            .as("Infrastructure must not depend on Controller: " + source + " -> " + target).isFalse();
                }
            }
        }
    }
}

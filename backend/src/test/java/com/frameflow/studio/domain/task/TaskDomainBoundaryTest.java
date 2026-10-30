package com.frameflow.studio.domain.task;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.regex.Pattern;
import java.util.spi.ToolProvider;

import com.frameflow.studio.domain.task.model.aggregate.ProductionTask;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TaskDomainBoundaryTest {

    @Test
    void compiledTaskDomainDependsOnlyOnJavaAndItsOwnDomain() throws Exception {
        Path classes = Path.of(ProductionTask.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        StringWriter output = new StringWriter();
        ToolProvider jdeps = ToolProvider.findFirst("jdeps").orElseThrow();
        int exit = jdeps.run(new PrintWriter(output), new PrintWriter(output),
                "--ignore-missing-deps", "-verbose:class", "-filter:none",
                "-include", "com\\.frameflow\\.studio\\.domain\\.task\\..*", classes.toString());
        assertThat(exit).as(output.toString()).isZero();

        Pattern dependency = Pattern.compile("^\\s+com\\.frameflow\\.studio\\.domain\\.task\\.\\S+\\s+->\\s+(\\S+)\\s+.*$");
        var targets = output.toString().lines().map(dependency::matcher)
                .filter(matcher -> matcher.matches()).map(matcher -> matcher.group(1)).toList();
        assertThat(targets).as("Actual compiled dependencies must be inspected").isNotEmpty();
        assertThat(targets).allSatisfy(target -> assertThat(
                target.startsWith("java.") || target.startsWith("com.frameflow.studio.domain.task."))
                .as("Forbidden Domain dependency: %s", target).isTrue());
    }

    @Test
    void aggregateExposesNoPublicConstructorOrStateSetterAndKeepsIdentityFinal() {
        assertThat(ProductionTask.class.getConstructors()).isEmpty();
        assertThat(Arrays.stream(ProductionTask.class.getMethods()).map(method -> method.getName()))
                .noneMatch(name -> name.startsWith("set"));
        assertThat(Arrays.stream(ProductionTask.class.getDeclaredFields())
                .filter(field -> Arrays.asList("taskId", "ownerId", "createdAt", "input").contains(field.getName())))
                .hasSize(4)
                .allSatisfy(field -> assertThat(Modifier.isPrivate(field.getModifiers())
                        && Modifier.isFinal(field.getModifiers())).isTrue());
    }
}

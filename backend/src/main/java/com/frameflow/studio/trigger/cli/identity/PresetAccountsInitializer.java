package com.frameflow.studio.trigger.cli.identity;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import com.frameflow.studio.cases.identity.IInitializeAccountsCase;

@Component
@ConditionalOnProperty(name = "app.accounts.initialize", havingValue = "true")
public class PresetAccountsInitializer implements ApplicationRunner {
    private final IInitializeAccountsCase initializer;
    private final Environment environment;

    public PresetAccountsInitializer(IInitializeAccountsCase initializer, Environment environment) {
        this.initializer = initializer;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        initializer.initialize(environment.getProperty("DEMO_A_PASSWORD"),
                environment.getProperty("DEMO_B_PASSWORD"));
    }
}

package net.einself.mu.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.jmolecules.archunit.JMoleculesDddRules;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DddArchitectureTest {

    private static JavaClasses classes;

    @BeforeAll
    static void setup() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("net.einself.mu");
    }

    @Test
    void should_follow_jmolecules_ddd_conventions() {
        JMoleculesDddRules.all().check(classes);
    }
}

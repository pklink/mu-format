package net.einself.mu.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

class ModulithArchitectureTest {

    private static JavaClasses classes;

    @BeforeAll
    static void setup() {
        classes = new ClassFileImporter()
                                        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                                        .importPackages("net.einself.mu");
    }

    @Test
    void modules_should_be_free_of_cycles() {
        slices()
                                        .matching("net.einself.mu.(*)..")
                                        .should().beFreeOfCycles()
                                        .check(classes);
    }

    @Test
    void shared_should_only_depend_on_java_and_itself() {
        classes()
                                        .that().resideInAPackage("net.einself.mu.shared")
                                        .should().onlyDependOnClassesThat()
                                        .resideInAnyPackage("net.einself.mu.shared", "java..", "org.jspecify..")
                                        .because("Shared kernel must be free of project dependencies "
                                                                        + "(org.jspecify is an annotation-only exception)")
                                        .check(classes);
    }

    @Test
    void collection_internal_should_not_be_accessed_from_outside() {
        noClasses()
                                        .that().resideOutsideOfPackage("net.einself.mu.collection..")
                                        .should().dependOnClassesThat()
                                        .resideInAPackage("net.einself.mu.collection.internal..")
                                        .because("collection.internal is private to the collection module")
                                        .check(classes);
    }

    @Test
    void naming_internal_should_not_be_accessed_from_outside() {
        noClasses()
                                        .that().resideOutsideOfPackage("net.einself.mu.naming..")
                                        .should().dependOnClassesThat()
                                        .resideInAPackage("net.einself.mu.naming.internal..")
                                        .because("naming.internal is private to the naming module")
                                        .check(classes);
    }

    @Test
    void storage_internal_should_not_be_accessed_from_outside() {
        noClasses()
                                        .that().resideOutsideOfPackage("net.einself.mu.storage..")
                                        .should().dependOnClassesThat()
                                        .resideInAPackage("net.einself.mu.storage.internal..")
                                        .because("storage.internal is private to the storage module")
                                        .check(classes);
    }

    @Test
    void metadata_internal_should_not_be_accessed_from_outside() {
        noClasses()
                                        .that().resideOutsideOfPackage("net.einself.mu.metadata..")
                                        .should().dependOnClassesThat()
                                        .resideInAPackage("net.einself.mu.metadata.internal..")
                                        .because("metadata.internal is private to the metadata module")
                                        .check(classes);
    }

    @Test
    void importcontext_internal_should_not_be_accessed_from_outside() {
        noClasses()
                                        .that().resideOutsideOfPackage("net.einself.mu.importcontext..")
                                        .should().dependOnClassesThat()
                                        .resideInAPackage("net.einself.mu.importcontext.internal..")
                                        .because("importcontext.internal is private to the import module")
                                        .check(classes);
    }

    @Test
    void searchcontext_internal_should_not_be_accessed_from_outside() {
        noClasses()
                                        .that().resideOutsideOfPackage("net.einself.mu.searchcontext..")
                                        .should().dependOnClassesThat()
                                        .resideInAPackage("net.einself.mu.searchcontext.internal..")
                                        .because("searchcontext.internal is private to the search module")
                                        .check(classes);
    }

    @Test
    void cli_should_not_depend_on_internal_packages() {
        noClasses()
                                        .that().resideInAPackage("net.einself.mu.cli")
                                        .should().dependOnClassesThat()
                                        .resideInAnyPackage(
                                                                        "net.einself.mu.collection.internal..",
                                                                        "net.einself.mu.naming.internal..",
                                                                        "net.einself.mu.storage.internal..",
                                                                        "net.einself.mu.metadata.internal..",
                                                                        "net.einself.mu.importcontext.internal..",
                                                                        "net.einself.mu.searchcontext.internal..")
                                        .because("CLI should only depend on module APIs")
                                        .check(classes);
    }
}

package com.pela;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.UUID;

@AnalyzeClasses(packages = "com.pela", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule domainIsPure = noClasses()
            .that().resideInAPackage("com.pela..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..",
                    "java.sql..",
                    "javax.sql..",
                    "com.pela..adapter..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule applicationDoesNotDependOnAdapters = noClasses()
            .that().resideInAPackage("com.pela..application..")
            .should().dependOnClassesThat().resideInAPackage("com.pela..adapter..")
            .allowEmptyShould(true);

    /** Thời gian và id đi qua ClockPort / IdGeneratorPort để test cố định được. */
    @ArchTest
    static final ArchRule noSystemTimeOrRandomIdInCore = noClasses()
            .that().resideInAnyPackage("com.pela..domain..", "com.pela..application..")
            .should().callMethod(Instant.class, "now")
            .orShould().callMethod(LocalDate.class, "now")
            .orShould().callMethod(LocalDateTime.class, "now")
            .orShould().callMethod(OffsetDateTime.class, "now")
            .orShould().callMethod(ZonedDateTime.class, "now")
            .orShould().callMethod(Clock.class, "systemUTC")
            .orShould().callMethod(Clock.class, "systemDefaultZone")
            .orShould().callMethod(System.class, "currentTimeMillis")
            .orShould().callMethod(UUID.class, "randomUUID")
            .allowEmptyShould(true);
}

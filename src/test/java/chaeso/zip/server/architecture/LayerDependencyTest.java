package chaeso.zip.server.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.Architectures;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

/**
 * 계층/패키지 의존 방향을 코드로 강제하는 아키텍처 테스트.
 *
 * <p>계층 규칙: presentation → application → domain (역방향 의존 금지)
 */
@AnalyzeClasses(
    packages = "chaeso.zip.server",
    importOptions = ImportOption.DoNotIncludeTests.class)
class LayerDependencyTest {

  @ArchTest
  static final ArchRule 계층_의존_방향을_지킨다 = Architectures.layeredArchitecture()
      .consideringOnlyDependenciesInLayers()
      .layer("Presentation").definedBy("..presentation..")
      .layer("Application").definedBy("..application..")
      .layer("Domain").definedBy("..domain..")
      .whereLayer("Presentation").mayNotBeAccessedByAnyLayer()
      .whereLayer("Application").mayOnlyBeAccessedByLayers("Presentation")
      .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Presentation");

  @ArchTest
  static final ArchRule 도메인은_애플리케이션_표현_계층에_의존하지_않는다 = noClasses()
      .that().resideInAPackage("..domain..")
      .should().dependOnClassesThat().resideInAnyPackage("..application..", "..presentation..")
      .as("도메인 계층은 상위 계층(application/presentation)에 의존해서는 안 된다");

  @ArchTest
  static final ArchRule 도메인은_웹_계층에_의존하지_않는다 = noClasses()
      .that().resideInAPackage("..domain..")
      .should().dependOnClassesThat().resideInAnyPackage("org.springframework.web..")
      .as("도메인 계층은 Spring Web 에 의존해서는 안 된다");

  @ArchTest
  static final ArchRule 컨트롤러는_표현_계층에만_위치한다 = classes()
      .that().areAnnotatedWith(RestController.class)
      .should().resideInAPackage("..presentation..")
      .as("@RestController 는 presentation 패키지에만 위치해야 한다");

  @ArchTest
  static final ArchRule 서비스는_애플리케이션_계층에만_위치한다 = classes()
      .that().areAnnotatedWith(Service.class)
      .should().resideInAPackage("..application..")
      .as("@Service 는 application 패키지에만 위치해야 한다");
}

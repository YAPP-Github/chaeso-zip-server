package chaeso.zip.server.support;

import java.sql.DriverManager;

/**
 * 로컬 PostgreSQL DB 연결 가능 여부를 검증하는 JUnit 5 조건 헬퍼 클래스.
 */
public final class PostgresCondition {

  private PostgresCondition() {
  }

  /**
   * 로컬 PostgreSQL DB 연결 가능 여부를 검증한다.
   */
  public static boolean postgresAvailable() {
    try (var ignored = DriverManager.getConnection(
        "jdbc:postgresql://localhost:5432/chaeso_zip", "postgres", "postgres")) {
      return true;
    } catch (Exception e) {
      return false;
    }
  }
}

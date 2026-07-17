package chaeso.zip.server.user.application;

import chaeso.zip.server.user.domain.ConsentVersions;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.consent")
public record ConsentProperties(String termsVersion) {

  public ConsentVersions toVersions() {
    return new ConsentVersions(termsVersion);
  }
}

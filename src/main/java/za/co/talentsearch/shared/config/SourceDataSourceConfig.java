package za.co.talentsearch.shared.config;

import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@Configuration
public class SourceDataSourceConfig {
  @Bean
  @Primary
  JdbcTemplate jdbcTemplate(DataSource dataSource) {
    return new JdbcTemplate(dataSource);
  }

  @Bean("sourceJdbc")
  JdbcTemplate sourceJdbc(
      @Value("${app.source.url}") String url,
      @Value("${app.source.username}") String user,
      @Value("${app.source.password}") String pass) {
    DriverManagerDataSource ds = new DriverManagerDataSource(url, user, pass);
    return new JdbcTemplate(ds);
  }
}

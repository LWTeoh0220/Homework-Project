package com.bigtwo.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.io.File;

@Configuration
public class SQLiteConfig {

    @Value("${app.sqlite.url:jdbc:sqlite:${user.home}/.bigtwo/bigtwo.db}")
    private String sqliteUrl;

    @Bean
    public DataSource sqliteDataSource() {
        ensureDbDirExists(sqliteUrl);
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.sqlite.JDBC");
        dataSource.setUrl(sqliteUrl);
        return dataSource;
    }

    @Bean
    public JdbcTemplate jdbcTemplate(DataSource sqliteDataSource) {
        return new JdbcTemplate(sqliteDataSource);
    }

    private void ensureDbDirExists(String jdbcUrl) {
        final String prefix = "jdbc:sqlite:";
        if (!jdbcUrl.startsWith(prefix)) {
            return;
        }

        String path = jdbcUrl.substring(prefix.length());
        File dbFile = new File(path);
        File parent = dbFile.getAbsoluteFile().getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
    }
}
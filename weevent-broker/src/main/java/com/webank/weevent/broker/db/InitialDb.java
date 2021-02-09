package com.webank.weevent.broker.db;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.Charset;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import lombok.extern.slf4j.Slf4j;
import org.springframework.util.Assert;

/**
 * tool to initdb
 */
@Slf4j
public class InitialDb implements AutoCloseable {

    private String user;
    private String password;
    private String dbName;
    private static String databaseType = "h2";
    private static Properties properties;


    public static void main(String[] args) {
        try (InitialDb initialDb = new InitialDb()) {
            properties = initialDb.getProperties();
            initialDb.createDataBase();
        } catch (ClassNotFoundException | IOException | SQLException e) {
            log.error("create database failed", e);
        }
    }

    private void createDataBase() throws ClassNotFoundException, IOException, SQLException {
        String goalUrl = properties.getProperty("spring.datasource.url");
        this.user = properties.getProperty("spring.datasource.username");
        this.password = properties.getProperty("spring.datasource.password");
        String driverName = properties.getProperty("spring.datasource.driver-class-name");
        boolean flag = driverName.contains("mariadb");
        if (flag) {
            databaseType = "mysql";
        }
        // first use dbself database
        int first = goalUrl.lastIndexOf("/");
        int end = goalUrl.lastIndexOf("?");
        this.dbName = flag ? goalUrl.substring(first + 1, end) : goalUrl.substring(first + 1);
        // get mysql default url like jdbc:mysql://127.0.0.1:3306
        String defaultUrl = flag ? goalUrl.substring(0, first) : goalUrl;

        Class.forName(driverName);

        List<String> tableSqlList = readSql();

        runScript(defaultUrl, flag, tableSqlList);
    }

    private Properties getProperties() throws IOException {
        Properties properties = new Properties();
        URL url = InitialDb.class.getClassLoader().getResource("application-prod.properties");
        Assert.notNull(url, "url is empty");
        properties.load(new FileInputStream(url.getFile()));
        return properties;
    }

    private static List<String> readSql() throws IOException {
        InputStream resourceAsStream = InitialDb.class.getResourceAsStream("/script/broker_" + databaseType + ".sql");
        StringBuffer sqlBuffer = new StringBuffer();
        List<String> sqlList = new ArrayList<>();
        byte[] buff = new byte[1024];
        int byteRead = 0;
        while ((byteRead = resourceAsStream.read(buff)) != -1) {
            sqlBuffer.append(new String(buff, 0, byteRead, Charset.defaultCharset()));
        }
        String[] sqlArr = sqlBuffer.toString().split("(;\\s*\\r\\n)|(;\\s*\\n)");

        for (String s : sqlArr) {
            String sql = s.replaceAll("--.*", "").trim();
            if (!("").equals(sql)) {
                sqlList.add(sql);
            }
            resourceAsStream.close();
        }
        return sqlList;
    }

    private void runScript(String defaultUrl, Boolean flag, List<String> tableSqlList) throws SQLException {
        try (Connection conn = DriverManager.getConnection(defaultUrl, this.user, this.password);
             Statement stat = conn.createStatement()) {
            if (flag) {
                String dbSql = "CREATE DATABASE IF NOT EXISTS " + this.dbName + " DEFAULT CHARACTER SET UTF8 COLLATE UTF8_GENERAL_CI;";
                String useDataBase = "USE " + this.dbName + ";";
                tableSqlList.add(0, dbSql);
                tableSqlList.add(1, useDataBase);
            }
            for (String sql : tableSqlList) {
                stat.executeUpdate(sql);
            }
            log.info("create database {} {}", this.dbName, " success!");
        } catch (SQLException e) {
            log.error("execute sql fail,message: {}", e.getMessage());
            throw e;
        }
    }

    @Override
    public void close() {
        log.info("resource is close");
    }
}

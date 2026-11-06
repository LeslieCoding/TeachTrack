package com.leslie.teachtrack.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leslie.teachtrack.common.Ids;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class MigrationServiceTest {
    private static final String HOST = System.getenv().getOrDefault("TEACHTRACK_PG_HOST", "127.0.0.1");
    private static final String PORT = System.getenv().getOrDefault("TEACHTRACK_PG_PORT", "55432");
    private static final String USER = System.getenv().getOrDefault("TEACHTRACK_TEST_DB_USER", "teachtrack");
    private static final String PASSWORD = System.getenv().getOrDefault("TEACHTRACK_TEST_DB_PASSWORD", "");
    private static final String DB = "teachtrack_migration_test";
    private static final String ADMIN_URL = "jdbc:postgresql://" + HOST + ":" + PORT + "/postgres";
    private static final String URL = "jdbc:postgresql://" + HOST + ":" + PORT + "/" + DB;

    private JdbcTemplate jdbc;
    private MigrationService service;

    @BeforeEach
    void setUp() throws Exception {
        try (Connection connection = DriverManager.getConnection(ADMIN_URL, USER, PASSWORD)) {
            connection.setAutoCommit(true);
            connection.createStatement().execute(
                    "select pg_terminate_backend(pid) from pg_stat_activity where datname = '" + DB + "'");
            connection.createStatement().execute("drop database if exists " + DB);
            connection.createStatement().execute("create database " + DB);
        }
        Flyway.configure().dataSource(URL, USER, PASSWORD).locations("classpath:db/migration").load().migrate();
        DriverManagerDataSource dataSource = new DriverManagerDataSource(URL, USER, PASSWORD);
        jdbc = new JdbcTemplate(dataSource);
        service = new MigrationService(jdbc, new ObjectMapper(), new DataSourceTransactionManager(dataSource));
    }

    @Test
    void seedImportIsIdempotentAndPreservesLegacyIds() {
        Path seed = seedPath();
        MigrationService.Result first = service.migrate(seed, "SEED_V1", false, "test");
        assertEquals(29, first.students());
        assertEquals(30, first.courses());
        assertTrue(first.knowledgePoints() > 0);

        long studentsAfterFirst = count("select count(*) from students where legacy_id like 'seed-student-%'");
        long coursesAfterFirst = count("select count(*) from course_sessions where legacy_id like 'seed-course-%'");
        assertEquals(29, studentsAfterFirst);
        assertEquals(30, coursesAfterFirst);

        MigrationService.Result second = service.migrate(seed, "SEED_V1", false, "test");
        assertEquals(29, second.students());
        assertEquals(studentsAfterFirst, count("select count(*) from students where legacy_id like 'seed-student-%'"));
        assertEquals(coursesAfterFirst, count("select count(*) from course_sessions where legacy_id like 'seed-course-%'"));

        UUID expected = Ids.fromLegacy("student", "seed-student-004");
        UUID actual = jdbc.queryForObject(
                "select id from students where legacy_id = 'seed-student-004'", UUID.class);
        assertEquals(expected, actual);

        assertTrue(count("select count(*) from student_profiles") >= 29);
        assertTrue(count("select count(*) from knowledge_points where legacy_id like 'seed-kp-%'") > 0);
        assertEquals(2, count("select count(*) from import_jobs where status = 'COMPLETED'"));
    }

    @Test
    void dryRunRollsBackEverything() {
        Path seed = seedPath();
        service.migrate(seed, "SEED_V1", false, "test");
        long studentsBefore = count("select count(*) from students");
        long jobsBefore = count("select count(*) from import_jobs");

        MigrationService.Result dry = service.migrate(seed, "SEED_V1", true, "test");
        assertTrue(dry.dryRun());
        assertEquals(29, dry.students());
        assertEquals(studentsBefore, count("select count(*) from students"));
        assertEquals(jobsBefore, count("select count(*) from import_jobs"));
    }

    @Test
    void backupJsonImportKeepsScoresAndCourses(@TempDir Path tempDir) throws Exception {
        String json = """
                {
                  "format": "TeachTrack Backup",
                  "students": [
                    {"id":"b-1","name":"备份学生","displayName":"备份","status":"ACTIVE","subject":"初中科学","grade":"八年级"}
                  ],
                  "exams": [{"id":"e-1","name":"期中","date":"2026-04-20","subject":"初中科学"}],
                  "courses": [
                    {"id":"c-1","studentId":"b-1","date":"2026-04-21","title":"浮力","content":"复习"}
                  ],
                  "scores": [
                    {"id":"s-1","studentId":"b-1","examId":"e-1","score":88,"fullScore":100,"scoreLabel":"88","fullScoreLabel":"100"}
                  ],
                  "problemRecords": [
                    {"id":"p-1","studentId":"b-1","title":"浮力计算","description":"单位错误","date":"2026-04-21","status":"待复习"}
                  ]
                }
                """;
        Path file = tempDir.resolve("backup.json");
        Files.writeString(file, json, StandardCharsets.UTF_8);

        MigrationService.Result first = service.migrate(file, "BACKUP_JSON", false, "test");
        assertEquals(1, first.students());
        assertEquals(1, first.courses());
        assertEquals(1, first.scores());
        assertEquals(1, first.problems());
        assertEquals(88.0, jdbc.queryForObject(
                "select score from student_scores where legacy_id = 's-1'", Double.class));

        service.migrate(file, "BACKUP_JSON", false, "test");
        assertEquals(1, count("select count(*) from students where legacy_id = 'b-1'"));
        assertEquals(1, count("select count(*) from course_sessions where legacy_id = 'c-1'"));
        assertEquals(1, count("select count(*) from student_scores where legacy_id = 's-1'"));
    }

    @Test
    void ttseedImportsAliasesProfilesContactsAndSessions(@TempDir Path tempDir) throws Exception {
        String json = """
                {
                  "format": "TeachTrack .ttseed",
                  "students": [
                    {
                      "id": "tt-001",
                      "displayName": "测试甲",
                      "fullName": "测试甲",
                      "alias": ["甲", "小甲"],
                      "status": "ACTIVE",
                      "gender": "男",
                      "grade": "八年级",
                      "subject": "初中科学",
                      "parentContact": {"name":"家长甲","phone":"13800000000","relation":"母亲"},
                      "academicProfile": {"level":"中等"},
                      "learningCharacteristics": {"style":"需要复述"},
                      "strengths": ["计算"],
                      "weaknesses": ["液体压强"],
                      "riskFlags": [],
                      "specialRules": ["先复习再新授"],
                      "knowledgeProgress": [{"name":"液体压强","level":2,"notes":"课堂讲过"}],
                      "courseSessions": [
                        {"id":"tt-c-1","date":"2026-09-01","title":"液体压强","content":"公式与实验"}
                      ],
                      "coursePlans": [
                        {"plannedDate":"2026-09-08","focus":"巩固液体压强"}
                      ],
                      "scores": [
                        {"examName":"周测","date":"2026-09-05","score":72,"fullScore":100}
                      ]
                    },
                    {
                      "id": "tt-002",
                      "displayName": "测试乙",
                      "alias": ["乙"],
                      "status": "PAUSED",
                      "grade": "九年级",
                      "subject": "初中科学",
                      "courseSessions": [
                        {"id":"tt-c-2","date":"2026-09-02","title":"酸碱盐","content":"复分解反应"}
                      ]
                    }
                  ]
                }
                """;
        Path file = tempDir.resolve("sample.ttseed.json");
        Files.writeString(file, json, StandardCharsets.UTF_8);

        MigrationService.Result first = service.migrate(file, "TTSEED", false, "test");
        assertEquals(2, first.students());
        assertEquals(2, first.courses());
        assertEquals(1, first.scores());
        assertNotNull(jdbc.queryForObject(
                "select id from students where legacy_id = 'tt-001'", UUID.class));
        assertEquals(3, count("select count(*) from student_aliases"));
        assertEquals(1, count("select count(*) from parent_contacts where legacy_id like 'contact-%'"));
        assertEquals(2, count("select count(*) from student_profiles"));
        assertEquals(1, count("select count(*) from lesson_plans where legacy_id like 'ttseed-plan-%'"));

        service.migrate(file, "TTSEED", false, "test");
        assertEquals(2, count("select count(*) from students where legacy_id like 'tt-00%'"));
        assertEquals(2, count("select count(*) from course_sessions where legacy_id like 'tt-c-%'"));
        assertEquals(3, count("select count(*) from student_aliases"));
    }

    private long count(String sql) {
        Long value = jdbc.queryForObject(sql, Long.class);
        return value == null ? 0 : value;
    }

    private static Path seedPath() {
        Path direct = Path.of("..", "seed", "teacher_seed_v1.json");
        if (Files.isRegularFile(direct)) {
            return direct;
        }
        Path fallback = Path.of("seed", "teacher_seed_v1.json");
        assertTrue(Files.isRegularFile(fallback), "seed file not found");
        return fallback;
    }
}

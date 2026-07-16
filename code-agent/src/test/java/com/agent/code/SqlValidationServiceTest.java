package com.agent.code;

import com.agent.code.config.CodeAgentProperties;
import com.agent.code.service.MetadataCacheService;
import com.agent.code.service.SqlValidationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
@DisplayName("SQL 白名单校验器")
class SqlValidationServiceTest {

    @Mock
    private MetadataCacheService metadataCacheService;

    @Mock
    private CodeAgentProperties properties;

    @InjectMocks
    private SqlValidationService validator;

    @BeforeEach
    void setUp() {
        // Mock whitelist configuration
        CodeAgentProperties.WhitelistConfig whitelist = new CodeAgentProperties.WhitelistConfig();
        whitelist.setAllowedOperations(List.of("SELECT"));
        whitelist.setForbiddenKeywords(List.of(
                "DROP", "DELETE", "INSERT", "UPDATE", "ALTER",
                "TRUNCATE", "CREATE", "EXEC", "EXECUTE", "UNION",
                "INTO", "LOAD_FILE", "OUTFILE", "DUMPFILE"
        ));
        whitelist.setMaxTablesPerQuery(3);
        whitelist.setMaxConditions(10);
        when(properties.getWhitelist()).thenReturn(whitelist);

        // Mock metadata
        when(metadataCacheService.getAllowedTableNames())
                .thenReturn(Set.of("sys_user", "meeting_room", "meeting_schedule",
                        "bank_customer", "bank_account", "bank_transaction"));
        when(metadataCacheService.getAllowedColumnNames("sys_user"))
                .thenReturn(Set.of("id", "username", "password", "real_name", "role", "status"));
        when(metadataCacheService.getAllowedColumnNames("meeting_room"))
                .thenReturn(Set.of("id", "room_name", "floor", "capacity", "facilities", "status"));
        when(metadataCacheService.getAllowedColumnNames("bank_customer"))
                .thenReturn(Set.of("id", "customer_no", "name", "id_card", "phone", "risk_level", "status"));
        when(metadataCacheService.getAllowedColumnNames("bank_account"))
                .thenReturn(Set.of("id", "account_no", "customer_id", "account_type", "balance", "currency", "open_date", "status"));
        when(metadataCacheService.getAllowedColumnNames("bank_transaction"))
                .thenReturn(Set.of("id", "txn_no", "account_id", "txn_type", "amount", "balance_after", "counterparty_account", "remark", "txn_time"));
        when(metadataCacheService.getAllowedColumnNames("meeting_schedule"))
                .thenReturn(Set.of("id", "room_id", "booker", "start_time", "end_time", "topic", "status"));
    }

    @Nested
    @DisplayName("第一层：操作类型校验")
    class OperationValidation {

        @Test
        @DisplayName("SELECT 应通过")
        void shouldAllowSelect() {
            var result = validator.validate("SELECT * FROM meeting_room");
            assertThat(result.passed()).isTrue();
        }

        @Test
        @DisplayName("SELECT 大小写不敏感")
        void shouldAllowSelectCaseInsensitive() {
            var result = validator.validate("select * from meeting_room");
            assertThat(result.passed()).isTrue();
        }

        @Test
        @DisplayName("DELETE 应被拒绝")
        void shouldRejectDelete() {
            var result = validator.validate("DELETE FROM meeting_room WHERE id=1");
            assertThat(result.passed()).isFalse();
            assertThat(result.message()).contains("仅允许");
        }

        @Test
        @DisplayName("INSERT 应被拒绝")
        void shouldRejectInsert() {
            var result = validator.validate("INSERT INTO meeting_room VALUES (1)");
            assertThat(result.passed()).isFalse();
        }

        @Test
        @DisplayName("空 SQL 应拒绝")
        void shouldRejectEmptySql() {
            var result = validator.validate("");
            assertThat(result.passed()).isFalse();
        }

        @Test
        @DisplayName("null SQL 应拒绝")
        void shouldRejectNullSql() {
            var result = validator.validate(null);
            assertThat(result.passed()).isFalse();
        }
    }

    @Nested
    @DisplayName("第二层：禁用关键字校验")
    class ForbiddenKeywordValidation {

        @Test
        @DisplayName("包含 DROP 的 SELECT 应被拦截")
        void shouldRejectSqlWithDrop() {
            var result = validator.validate("SELECT * FROM meeting_room; DROP TABLE meeting_room");
            assertThat(result.passed()).isFalse();
            assertThat(result.message()).contains("DROP");
        }

        @Test
        @DisplayName("包含 UNION 应被拦截")
        void shouldRejectSqlWithUnion() {
            var result = validator.validate("SELECT id FROM sys_user UNION SELECT id FROM meeting_room");
            assertThat(result.passed()).isFalse();
        }

        @Test
        @DisplayName("包含 INSERT 应被拦截")
        void shouldRejectSqlWithInsert() {
            var result = validator.validate("SELECT * FROM bank_customer; INSERT INTO bank_customer VALUES (1)");
            assertThat(result.passed()).isFalse();
        }

        @Test
        @DisplayName("普通 SELECT 不触发关键字误判")
        void shouldNotTriggerFalsePositive() {
            // INT should not trigger the IN keyword
            var result = validator.validate("SELECT id FROM meeting_room WHERE floor = 3");
            assertThat(result.passed()).isTrue();
        }
    }

    @Nested
    @DisplayName("第三层：表名白名单校验")
    class TableNameValidation {

        @Test
        @DisplayName("白名单内的表应通过")
        void shouldAllowWhitelistedTable() {
            var result = validator.validate("SELECT * FROM meeting_room");
            assertThat(result.passed()).isTrue();
        }

        @Test
        @DisplayName("不存在的表应被拒绝")
        void shouldRejectUnknownTable() {
            var result = validator.validate("SELECT * FROM secret_table");
            assertThat(result.passed()).isFalse();
            assertThat(result.message()).contains("secret_table");
        }

        @Test
        @DisplayName("多表 JOIN 应在白名单内")
        void shouldAllowMultiTableJoin() {
            var result = validator.validate(
                    "SELECT c.name, a.balance FROM bank_customer c JOIN bank_account a ON c.id = a.customer_id");
            assertThat(result.passed()).isTrue();
        }
    }

    @Nested
    @DisplayName("第四层：列名白名单校验")
    class ColumnNameValidation {

        @Test
        @DisplayName("白名单内的列应通过")
        void shouldAllowWhitelistedColumns() {
            var result = validator.validate("SELECT id, room_name, capacity FROM meeting_room");
            assertThat(result.passed()).isTrue();
        }

        @Test
        @DisplayName("SELECT * 应通过（宽松处理）")
        void shouldAllowSelectAll() {
            var result = validator.validate("SELECT * FROM meeting_room");
            assertThat(result.passed()).isTrue();
        }

        @Test
        @DisplayName("COUNT(*) 应通过")
        void shouldAllowCount() {
            var result = validator.validate("SELECT COUNT(*) FROM meeting_room");
            assertThat(result.passed()).isTrue();
        }
    }

    @Nested
    @DisplayName("第五层：复杂度限制")
    class ComplexityValidation {

        @Test
        @DisplayName("正常复杂度应通过")
        void shouldAllowNormalComplexity() {
            var result = validator.validate(
                    "SELECT * FROM meeting_room WHERE floor = 3 AND capacity > 10");
            assertThat(result.passed()).isTrue();
        }

        @Test
        @DisplayName("超过最大表数应拒绝")
        void shouldRejectTooManyTables() {
            var result = validator.validate(
                    "SELECT * FROM a JOIN b ON a.id=b.id JOIN c ON b.id=c.id JOIN d ON c.id=d.id");
            assertThat(result.passed()).isFalse();
        }
    }

    @Nested
    @DisplayName("综合场景")
    class IntegrationScenarios {

        @Test
        @DisplayName("银行查询：查客户余额")
        void bankQueryCustomerBalance() {
            var result = validator.validate(
                    "SELECT c.name, a.balance FROM bank_customer c JOIN bank_account a ON c.id = a.customer_id WHERE c.name = '张三'");
            assertThat(result.passed()).isTrue();
        }

        @Test
        @DisplayName("银行查询：交易统计")
        void bankQueryTransactionStats() {
            var result = validator.validate(
                    "SELECT COUNT(*) AS total, SUM(amount) AS sum_amount FROM bank_transaction WHERE txn_time >= '2026-06-01'");
            assertThat(result.passed()).isTrue();
        }

        @Test
        @DisplayName("危险 SQL 综合场景")
        void dangerousSqlCombo() {
            var result = validator.validate(
                    "SELECT * FROM bank_customer; UPDATE bank_account SET balance = 0");
            assertThat(result.passed()).isFalse();
        }
    }
}

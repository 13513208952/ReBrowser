import io.github.rebrowser.control.IdempotencyStore;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** HOST bridge: only returns compiled literal DDL; NEVER calls Android SQLite methods. */
public final class DumpAdminJournalSchema {
    public static void main(String[] args) {
        for(String sql:IdempotencyStore.schemaStatements())
            System.out.println(Base64.getUrlEncoder().withoutPadding().encodeToString(sql.getBytes(StandardCharsets.UTF_8)));
    }
}

import io.github.rebrowser.control.IdempotencyStore;
import io.github.rebrowser.core.TaskJournal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Actual compiled constants only; no Android SQLite method is invoked. */
public final class DumpTaskJournalSchema {
    public static void main(String[] args) {
        for(String[] group:new String[][]{IdempotencyStore.schemaStatements(),TaskJournal.schemaStatements()})
            for(String sql:group)System.out.println(Base64.getUrlEncoder().withoutPadding().encodeToString(sql.getBytes(StandardCharsets.UTF_8)));
        System.out.println(Base64.getUrlEncoder().withoutPadding().encodeToString(TaskJournal.lostProcessRecoveryStatement().getBytes(StandardCharsets.UTF_8)));
    }
}

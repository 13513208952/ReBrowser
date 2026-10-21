package io.github.rebrowser.outlook;
import org.junit.Test;
import static org.junit.Assert.*;
import java.nio.file.*;
import java.lang.reflect.*;
import java.util.*;
/** Actual fixed APK-source parser only; HOST SQLite execution is an independent Python probe.
 * Does not execute Android SQLite/openExisting/transactions/actual keystore/account scopes. */
public final class MailDatabaseSchemaTest {
 private static byte[] source()throws Exception{Path root=Paths.get("").toAbsolutePath();for(int i=0;i<4 && root!=null;i++,root=root.getParent()){Path p=root.resolve("app/src/main/assets/sql/mail-v1.sql");if(Files.isRegularFile(p))return Files.readAllBytes(p);}throw new AssertionError("Owned native SQL source missing");}
 @SuppressWarnings("unchecked") private static Map<String,String> decode(byte[] b)throws Throwable{Method m=MailDatabase.class.getDeclaredMethod("decodeFixedSchema",byte[].class);m.setAccessible(true);try{return (Map<String,String>)m.invoke(null,(Object)b);}catch(InvocationTargetException e){throw e.getCause();}}
 @Test public void actualPinnedSourceHasComplete48Definitions()throws Throwable{Map<String,String> m=decode(source());assertEquals(48,m.size());assertTrue(m.containsKey("table:account_meta"));assertTrue(m.containsKey("table:object_reservations"));assertTrue(m.containsKey("view:actual_object_references"));}
 @Test public void internalTriggerSemicolonsRemainWhole()throws Throwable{Map<String,String> m=decode(source());String trigger=m.get("trigger:item_change_refs");assertTrue(trigger.contains("UPDATE content_objects"));assertTrue(trigger.indexOf(';')!=trigger.lastIndexOf(';'));assertTrue(trigger.endsWith("END"));}
 @Test public void editedSourceCannotBecomeRuntimeSql()throws Throwable{byte[] raw=source();raw[raw.length-2]^=1;assertThrows(java.io.IOException.class,()->decode(raw));}
 @Test public void appendedSqlCannotBeParsedWithoutNewReview()throws Throwable{byte[] raw=source(),bad=Arrays.copyOf(raw,raw.length+1);bad[bad.length-1]=';';assertThrows(java.io.IOException.class,()->decode(bad));}
 @Test public void nullAndOverBoundFailBeforeParsing()throws Throwable{assertThrows(java.io.IOException.class,()->decode(null));assertThrows(java.io.IOException.class,()->decode(new byte[65537]));}
 @Test public void fixedDefinitionsHaveNoCallerDirectoriesOrNativeCommands()throws Throwable{for(String sql:decode(source()).values()){assertTrue(sql.startsWith("CREATE "));assertFalse(sql.endsWith(";"));assertFalse(sql.contains("ATTACH DATABASE"));}}
}

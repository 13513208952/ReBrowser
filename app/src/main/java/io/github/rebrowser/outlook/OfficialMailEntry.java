package io.github.rebrowser.outlook;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Fixed OFFICIAL Outlook mail entry directory. No domain guessing, no network probe/redirect,
 * no account detection and no arbitrary URL input. "Recent success" is a native display
 * preference supplied by its owner; using it neither proves a login nor writes any record.
 */
public final class OfficialMailEntry {
    public static final String PERSONAL_MAIL = "https://outlook.live.com/mail/";
    public static final String WORK_SCHOOL_MAIL = "https://outlook.office.com/mail/";
    public enum Kind { PERSONAL, WORK_SCHOOL, RECENT_SUCCESS }
    public static final class Choice {
        private final Kind kind;private final String url,label;
        private Choice(Kind kind,String url,String label){this.kind=kind;this.url=url;this.label=label;}
        public Kind kind(){return kind;}
        public String url(){return url;}
        public String label(){return label;}
    }
    private static final Choice PERSONAL=new Choice(Kind.PERSONAL,PERSONAL_MAIL,"个人 Outlook");
    private static final Choice WORK=new Choice(Kind.WORK_SCHOOL,WORK_SCHOOL_MAIL,"工作或学校 Outlook");

    /** Exactly the two official entries, in a stable order. */
    public static List<Choice> fixedEntries(){return Collections.unmodifiableList(new ArrayList<>(java.util.Arrays.asList(PERSONAL,WORK)));}

    /** Recent entry appears ONLY with an exact previously observed official identity. */
    public static List<Choice> entriesWithRecent(Choice observedRecent){
        List<Choice> entries=new ArrayList<>(fixedEntries());
        if(observedRecent!=null)entries.add(0,recent(observedRecent));
        return Collections.unmodifiableList(entries);
    }
    private static Choice recent(Choice observed){
        if(observed.kind==Kind.RECENT_SUCCESS)throw new IllegalArgumentException("Nested recent entry");
        if(!observed.url.equals(PERSONAL_MAIL) && !observed.url.equals(WORK_SCHOOL_MAIL))throw new IllegalArgumentException("Recent entry is not an official mail URL");
        return new Choice(Kind.RECENT_SUCCESS,observed.url,"最近成功：" + observed.label);
    }
    public static Choice require(Kind kind){
        if(kind==null)throw new IllegalArgumentException("Missing entry kind");
        switch(kind){case PERSONAL:return PERSONAL;case WORK_SCHOOL:return WORK;default:throw new IllegalArgumentException("Recent entry requires an observed official choice");}
    }
    /** Only the exact official URLs are accepted as an entry target. */
    public static Choice fromOfficialUrl(String url){
        if(PERSONAL_MAIL.equals(url))return PERSONAL;
        if(WORK_SCHOOL_MAIL.equals(url))return WORK;
        throw new IllegalArgumentException("Not a fixed official Outlook mail URL");
    }
    private OfficialMailEntry(){}
}

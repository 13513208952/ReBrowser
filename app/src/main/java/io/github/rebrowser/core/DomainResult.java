package io.github.rebrowser.core;

/** Display projection ONLY: no actor, grant, persistence, effect or work-ended receipt. */
public final class DomainResult {
    public enum Kind { OBSERVATION, WAITING, UNAVAILABLE, REJECTED, UNKNOWN }
    public enum Evidence { UNVERIFIED, NATIVE_REPORTED }
    public enum Reason { NONE, NOT_CONNECTED, SOURCE_LOCKED, RESOURCE_LIMIT, UNSUPPORTED, STALE, INVALID_INPUT, OUTCOME_UNKNOWN }
    private final Kind kind; private final Evidence evidence; private final Reason reason;
    private final String waitCondition;
    public DomainResult(Kind kind,Evidence evidence,Reason reason,String waitCondition){
        if(kind==null||evidence==null||reason==null)throw new IllegalArgumentException("Missing projection kind");
        if((kind==Kind.WAITING)!=(waitCondition!=null))throw new IllegalArgumentException("Waiting condition mismatch");
        this.kind=kind;this.evidence=evidence;this.reason=reason;
        this.waitCondition=waitCondition==null?null:text(waitCondition,256,false);
    }
    public Kind kind(){return kind;} public Evidence evidence(){return evidence;} public Reason reason(){return reason;}
    public String waitingFor(){return waitCondition;}
    /** Fixed non-private UI labels; never includes raw exception, URI, account or file data. */
    public String label(){switch(kind){case WAITING:return "等待原生状态";case OBSERVATION:return "观察值，非完成凭据";case REJECTED:return "请求未获接纳";case UNAVAILABLE:return "当前不可用";default:return "操作结果未知";}}
    /** Bounded display text validation, not sanitisation or an authority check. */
    public static String text(String value,int max,boolean multiline){
        if(value==null||max<0||max>1_048_576||value.length()>max)throw new IllegalArgumentException("Invalid bounded display text");
        for(int i=0;i<value.length();i++){char c=value.charAt(i);if(Character.isISOControl(c)&&!(multiline&&(c=='\n'||c=='\r'||c=='\t')))throw new IllegalArgumentException("Control in display text");if(Character.isHighSurrogate(c)){if(++i>=value.length()||!Character.isLowSurrogate(value.charAt(i)))throw new IllegalArgumentException("Invalid UTF16");}else if(Character.isLowSurrogate(c))throw new IllegalArgumentException("Invalid UTF16");}
        return value;
    }
}

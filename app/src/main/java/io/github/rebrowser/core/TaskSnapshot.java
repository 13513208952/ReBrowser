package io.github.rebrowser.core;

/**
 * Immutable native evidence projection, NEVER authority or an input completion receipt.
 * Client waiting, actual whole-chain work, external effect and journal durability are
 * independent. A timeout, stale document, cancellation request or journal commit does
 * not prove actual end. Only the trusted execution owner may supply end observations.
 */
public final class TaskSnapshot {
    public enum Phase {
        RESERVED(0), WORK_STARTED(1), DISPATCH_INTENT(2), OUTCOME_OBSERVED(3),
        ENDED_BEFORE_DISPATCH(4), OUTCOME_UNKNOWN(5), OWNER_LOST_BEFORE_DISPATCH(6);
        private final int code;
        Phase(int code){this.code=code;}
        public int code(){return code;}
        public static Phase fromCode(int code){for(Phase p:values())if(p.code==code)return p;throw invalid();}
    }
    public enum Work {
        NOT_STARTED(0), RUNNING(1), ENDED(2), UNKNOWN(3);
        private final int code;
        Work(int code){this.code=code;}
        public int code(){return code;}
        public static Work fromCode(int code){for(Work p:values())if(p.code==code)return p;throw invalid();}
    }
    public enum Effect {
        NOT_DISPATCHED(0), UNKNOWN(1), OBSERVED_SUCCESS(2), OBSERVED_FAILURE(3);
        private final int code;
        Effect(int code){this.code=code;}
        public int code(){return code;}
        public static Effect fromCode(int code){for(Effect p:values())if(p.code==code)return p;throw invalid();}
    }
    public enum Durability { NOT_COMMITTED, COMMITTED, UNKNOWN }
    public enum ClientWait { WAITING, DEADLINE_EXCEEDED, DETACHED }
    private final Phase phase;
    private final Work work;
    private final Effect effect;
    private final Durability durability;
    private final ClientWait wait;
    private final boolean cancellationRequested,currentForPublication;

    private TaskSnapshot(Phase phase,Work work,Effect effect,Durability durability,
            ClientWait wait,boolean cancellationRequested,boolean currentForPublication){
        if(phase==null || work==null || effect==null || durability==null || wait==null)throw invalid();
        boolean valid;
        switch(phase){
            case RESERVED:valid=work==Work.NOT_STARTED && effect==Effect.NOT_DISPATCHED;break;
            case WORK_STARTED:valid=work==Work.RUNNING && effect==Effect.NOT_DISPATCHED;break;
            case DISPATCH_INTENT:valid=work==Work.RUNNING && effect==Effect.UNKNOWN;break;
            case OUTCOME_OBSERVED:valid=work!=Work.NOT_STARTED && (effect==Effect.OBSERVED_SUCCESS || effect==Effect.OBSERVED_FAILURE);break;
            case ENDED_BEFORE_DISPATCH:valid=work==Work.ENDED && effect==Effect.NOT_DISPATCHED;break;
            case OUTCOME_UNKNOWN:valid=work!=Work.NOT_STARTED && effect==Effect.UNKNOWN;break;
            case OWNER_LOST_BEFORE_DISPATCH:valid=work==Work.UNKNOWN && effect==Effect.NOT_DISPATCHED;break;
            default:valid=false;
        }
        if(!valid)throw invalid();
        this.phase=phase;this.work=work;this.effect=effect;this.durability=durability;
        this.wait=wait;this.cancellationRequested=cancellationRequested;this.currentForPublication=currentForPublication;
    }
    /** Projection only; accepting an IPC-created copy as trusted evidence is forbidden. */
    public static TaskSnapshot fromNativeEvidence(Phase phase,Work work,Effect effect,
            Durability durability,ClientWait wait,boolean cancellationRequested,boolean currentForPublication){
        return new TaskSnapshot(phase,work,effect,durability,wait,cancellationRequested,currentForPublication);
    }
    public Phase phase(){return phase;}
    public Work work(){return work;}
    public Effect effect(){return effect;}
    public Durability durability(){return durability;}
    public ClientWait clientWait(){return wait;}
    public boolean cancellationRequested(){return cancellationRequested;}
    public boolean currentForPublication(){return currentForPublication;}
    /** Does NOT itself release any slot, stream, worker, or partition-quiescence fence. */
    public boolean actualWorkEnded(){return work==Work.ENDED;}
    public TaskSnapshot withClientWait(ClientWait next){return new TaskSnapshot(phase,work,effect,durability,next,cancellationRequested,currentForPublication);}
    public TaskSnapshot withCancellationRequested(){return new TaskSnapshot(phase,work,effect,durability,wait,true,currentForPublication);}
    public TaskSnapshot withoutPublication(){return new TaskSnapshot(phase,work,effect,durability,wait,cancellationRequested,false);}
    private static IllegalArgumentException invalid(){return new IllegalArgumentException("Invalid native task evidence projection");}
}

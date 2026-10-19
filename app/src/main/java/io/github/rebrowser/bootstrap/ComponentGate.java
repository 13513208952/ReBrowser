package io.github.rebrowser.bootstrap;

/**
 * All native entries share one source/data decision. Shell visibility is not business
 * unlock. Evaluator is trusted in-process continuity code, never IPC/client assertions.
 * A permit is bound to this gate and rechecked on every use, not a permanent boolean.
 */
public final class ComponentGate {
    public enum SourceState { INITIALIZING, UNCONFIGURED, LOCKED, TRUSTED }
    public interface SourceEvaluator { SourceState current(); }
    private final ProcessRole role;
    private final SourceEvaluator source;
    public ComponentGate(ProcessRole role, SourceEvaluator source) {
        if (role == null || source == null) throw new IllegalArgumentException("Native source decision required");
        this.role = role; this.source = source;
    }
    public boolean mayShowNativeShell() { return role == ProcessRole.MAIN; }
    public SourceState sourceState() {
        if (role != ProcessRole.MAIN) return SourceState.LOCKED;
        try { SourceState value = source.current(); return value == null ? SourceState.LOCKED : value; }
        catch (RuntimeException unavailable) { return SourceState.LOCKED; }
    }
    public static final class BusinessPermit {
        private final ComponentGate issuer;
        private BusinessPermit(ComponentGate issuer) { this.issuer = issuer; }
    }
    public BusinessPermit requireBusiness() {
        if (sourceState() != SourceState.TRUSTED) throw locked();
        return new BusinessPermit(this);
    }
    public void checkCurrent(BusinessPermit permit) {
        if (permit == null || permit.issuer != this || sourceState() != SourceState.TRUSTED) throw locked();
    }
    private static SecurityException locked() { return new SecurityException("Business source/data gate locked"); }
}

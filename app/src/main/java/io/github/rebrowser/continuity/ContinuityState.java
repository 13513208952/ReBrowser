package io.github.rebrowser.continuity;

/** Native flow vocabulary/view data only; enum/step is NEVER consent, authority or erasure evidence. */
public final class ContinuityState {
    public enum Phase {
        UNCONFIGURED, FRESH_INSTALL_LOCKED, NORMAL_TRUSTED, MISMATCH_LOCKED,
        CONFIRMING, WAITING_OWNER_AUTH, ERASURE_AUTHORIZED_LOCKED,
        ERASING_LOCKED, ERASURE_FAILED_LOCKED, VERIFIED_EMPTY_EPOCH_PENDING_COMMIT,
        RECOVERY_REQUIRED_LOCKED
    }
    public enum Recoverability { PRESERVED, IRREVERSIBLE_DESTRUCTION_STARTED, UNKNOWN }
    public enum Confirmation {
        TRUST_SOURCE("该软件来源异常，是否信任该来源并使用",false),
        FORCE_1("当前包与上一个包来源不匹配，强行时候会销毁之前的全部软件数据，是否继续？",false),
        FORCE_2("强行使用将会丢失此前该软件的所有数据，如果重新安装正确来源的包将仍然可以正常继承使用，确定要强行继续吗？",true),
        FORCE_3("这是最后一次警告，您可以安装正确来源的包来维持数据继续使用，但是如果您坚持将会丢失全部数据而且不可找回，再次强调这是最后一次机会，您确定要放弃原先的数据吗？",true);
        private final String warning;
        private final boolean androidOwnerVerification;
        Confirmation(String warning,boolean verification){this.warning=warning;androidOwnerVerification=verification;}
        /** Exact owner O16 wording, including original typo; do not "correct" it. */
        public String warning(){return warning;}
        public boolean requiresSeparateAndroidOwnerVerification(){return androidOwnerVerification;}
        public Confirmation next(){switch(this){case TRUST_SOURCE:return FORCE_1;case FORCE_1:return FORCE_2;case FORCE_2:return FORCE_3;default:return null;}}
    }
    private ContinuityState(){}
}

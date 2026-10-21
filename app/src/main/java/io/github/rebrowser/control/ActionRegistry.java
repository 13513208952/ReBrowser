package io.github.rebrowser.control;

import io.github.rebrowser.authority.CompiledPrincipals;
import io.github.rebrowser.crypto.CryptoEncoding;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Executable policy metadata for the FOUR implemented schemas, not 178 pretend handlers.
 * No transport/package/source discount; all currently supported signed channels have floor 1.
 * Opening an OS consent flow is not performing the protected operation or receiving consent. */
public final class ActionRegistry {
    public static final class Rule {
        private final ActionSchemas.Kind kind;private final int level;
        private Rule(ActionSchemas.Kind kind,int level){this.kind=kind;this.level=level;}
        public ActionSchemas.Kind kind(){return kind;}
        public int requiredLevel(){return Math.max(level,1);}
        public boolean needsOwnerGrant(){return false;} // Only these four ORDINARY actions.
    }
    private static final Rule[] RULES={new Rule(ActionSchemas.Kind.OPEN_DEFAULT_BROWSER_FLOW,3),new Rule(ActionSchemas.Kind.OPEN_PERMISSION_SETTINGS,3),new Rule(ActionSchemas.Kind.OPEN_INSTALLER,3),new Rule(ActionSchemas.Kind.WINDOW_BACK,2)};
    private ActionRegistry(){}
    public static Rule require(String id){for(Rule r:RULES)if(r.kind.actionId().equals(id))return r;throw new IllegalArgumentException("Unimplemented control action");}
    /** Stable policy commitment, including rules/floors, not just public-key bytes. */
    public static byte[] policySha256(CompiledPrincipals principals){
        if(principals==null)throw new IllegalArgumentException("Compiled policy required");
        try{ByteArrayOutputStream b=new ByteArrayOutputStream();DataOutputStream o=new DataOutputStream(b);o.write("ReBrowser.Control.Policy.1\0".getBytes(StandardCharsets.US_ASCII));o.write(principals.identityPolicySha256());
            for(Rule r:RULES){o.writeUTF(r.kind.actionId());o.writeInt(r.requiredLevel());o.writeBoolean(r.needsOwnerGrant());}return CryptoEncoding.sha256(b.toByteArray());
        }catch(IOException impossible){throw new AssertionError(impossible);}
    }
}

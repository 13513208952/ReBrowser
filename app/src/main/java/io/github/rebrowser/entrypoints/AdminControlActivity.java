package io.github.rebrowser.entrypoints;

import android.app.Activity;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import io.github.rebrowser.ReBrowserApplication;
import io.github.rebrowser.authority.AdminProofVerifier;
import io.github.rebrowser.bootstrap.StartupCoordinator;
import io.github.rebrowser.control.ControlDecoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;

/** Internal native control console; parsing/key possession is never command admission. */
public final class AdminControlActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (!(getApplication() instanceof ReBrowserApplication)) { finish(); return; }
        StartupCoordinator startup=((ReBrowserApplication)getApplication()).primaryStartup();
        if (!startup.gate().mayShowNativeShell()) { finish(); return; }
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        int pad=Math.round(16*getResources().getDisplayMetrics().density);root.setPadding(pad,pad,pad,pad);
        TextView explanation=new TextView(this);
        explanation.setText("管理员调试：当前接入严格协议和编译身份验签。来源/作用域/挑战/撤销/本人授权及原子准入未完成前，不执行命令。");root.addView(explanation);
        EditText request=new EditText(this);request.setHint("输入 rebrowser.control/1 包络");
        request.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        request.setSaveEnabled(false);request.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        root.addView(request,new LinearLayout.LayoutParams(-1,0,1));
        TextView result=new TextView(this);root.addView(result);
        Button inspect=new Button(this);inspect.setText("验证协议与持钥证明（不执行动作）");root.addView(inspect);
        inspect.setOnClickListener(v->{
            if(request.length()>ControlDecoder.MAX_WIRE_BYTES){result.setText("请求超过资源上限");return;}
            try {
                ControlDecoder.UnverifiedProof proof=ControlDecoder.decode(request.getText().toString().getBytes(StandardCharsets.UTF_8));
                if(!startup.hasCompiledAdministratorPolicy()){result.setText("协议有效；尚无正式编译管理员身份，未认证、未执行。");return;}
                AdminProofVerifier.KeyProof key=startup.proofVerifier().verifyCompiledKey(proof);
                result.setText("持钥签名有效；编译等级 "+key.compiledPrincipal().level()+"。尚未进行权限/挑战准入，未执行。");
            } catch(IllegalArgumentException|GeneralSecurityException rejected){result.setText("请求格式或持钥证明无效；未执行。");}
        });
        setContentView(root);
        // No clipboard read, external request extras, account data, caller-source penalty or consent click.
    }
}

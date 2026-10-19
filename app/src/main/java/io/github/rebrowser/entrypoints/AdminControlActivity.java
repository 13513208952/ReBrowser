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
import io.github.rebrowser.control.ActionSchemas;
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
        explanation.setText("管理员调试：严格协议、四项原生窗口动作参数及编译身份验签。实际来源/作用域/挑战/撤销/本人授权与任务协调器完整接通前，不执行命令。");root.addView(explanation);
        EditText request=new EditText(this);request.setHint("输入 rebrowser.control/1 包络");
        request.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        request.setSaveEnabled(false);request.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        root.addView(request,new LinearLayout.LayoutParams(-1,0,1));
        TextView result=new TextView(this);root.addView(result);
        Button inspect=new Button(this);inspect.setText("检查协议、四项动作参数及持钥证明（不执行）");root.addView(inspect);
        inspect.setOnClickListener(v->{
            if(request.length()>ControlDecoder.MAX_WIRE_BYTES){result.setText("请求超过资源上限");return;}
            try {
                ControlDecoder.UnverifiedProof proof=ControlDecoder.decode(request.getText().toString().getBytes(StandardCharsets.UTF_8));
                ActionSchemas.parse(proof); // typed claims only; native target/revision/authority still required
                if(!startup.hasCompiledAdministratorPolicy()){result.setText("协议及动作参数有效；尚无正式编译管理员身份，未认证、未执行。");return;}
                AdminProofVerifier.KeyProof key=startup.proofVerifier().verifyCompiledKey(proof);
                result.setText("持钥签名有效；编译等级 "+key.compiledPrincipal().level()+"。尚未进行权限/挑战准入，未执行。");
            } catch(IllegalArgumentException|GeneralSecurityException rejected){result.setText("请求格式、支持的动作参数或持钥证明无效；未执行。");}
        });
        setContentView(root);
        // No clipboard read, external request extras, account data, caller-source penalty or consent click.
    }
}

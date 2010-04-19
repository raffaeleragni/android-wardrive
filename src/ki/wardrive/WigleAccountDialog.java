package ki.wardrive;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

public class WigleAccountDialog extends Dialog
{
	public interface WigleAccountDialogOKListener
    {
        public void ok(String username, String password);
    }
	
	private String username;
	
	private String password;	
	
	private WigleAccountDialogOKListener listener;
	
	@Override
	protected void onCreate(Bundle icicle)
	{
		super.onCreate(icicle);

        setContentView(R.layout.wigle_account_dialog);

        setTitle(R.string.WIGLE_ACCOUNT_DIALOG_TITLE);
		EditText editUsername = (EditText) findViewById(R.id.wad_editUsername);
		EditText editPassword = (EditText) findViewById(R.id.wad_editPassword);
		Button buttonOK = (Button) findViewById(R.id.wad_buttonOK);
		editUsername.setText(username);
		editPassword.setText(password);
		
		buttonOK.setOnClickListener(new android.view.View.OnClickListener()
        {
            public void onClick(View arg0)
            {
            	EditText editUsername = (EditText) findViewById(R.id.wad_editUsername);
        		EditText editPassword = (EditText) findViewById(R.id.wad_editPassword);
        		listener.ok(editUsername.getText().toString(), editPassword.getText().toString());
        		WigleAccountDialog.this.dismiss();
            }
        });
	}
	
	public WigleAccountDialog(Context ctx, String username, String password, WigleAccountDialogOKListener listener)
	{
		super(ctx);
		this.username = username;
		this.password = password;
		this.listener = listener;
	}
}

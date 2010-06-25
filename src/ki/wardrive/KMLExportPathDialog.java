package ki.wardrive;

import java.io.File;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

public class KMLExportPathDialog extends Dialog
{
	public interface KMLExportPathDialogOKListener
    {
        public void ok(String path);
    }

    private KMLExportPathDialogOKListener ok_listener;

    private String path;
    
    @Override
    public void onCreate(Bundle icicle)
    {
    	super.onCreate(icicle);

        setContentView(R.layout.kml_export_path_dialog);

        setTitle(R.string.KML_EXPORT_PATH_DIALOG_TITLE);
		EditText editPath = (EditText) findViewById(R.id.kepd_editPath);
		Button buttonOK = (Button) findViewById(R.id.kepd_buttonOK);
		editPath.setText(path);
		
		buttonOK.setOnClickListener(new android.view.View.OnClickListener()
        {
            public void onClick(View arg0)
            {
            	EditText editPath = (EditText) findViewById(R.id.kepd_editPath);
            	
            	// try the path, make sure its valid
            	File file = new File( Environment.getExternalStorageDirectory(), editPath.getText().toString());
            	if( !file.exists() || !file.canWrite() )
            	{
            		TextView errorMsg = (TextView) findViewById(R.id.kepd_errorMsg);
            		errorMsg.setText(R.string.KML_EXPORT_PATH_DIALOG_ERROR);
            	}
            	else
            	{
            		ok_listener.ok(editPath.getText().toString());
            		KMLExportPathDialog.this.dismiss();
            	}
            }
        });
    }

    public KMLExportPathDialog(Context context, String path, KMLExportPathDialogOKListener ok_listener)
    {
        super(context);
        this.path = path;
        this.ok_listener = ok_listener;
    }
}

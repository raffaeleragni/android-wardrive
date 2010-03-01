/*
 *   wardrive - android wardriving application
 *   Copyright (C) 2009 Raffaele Ragni
 *   http://code.google.com/p/wardrive-android/
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package ki.wardrive;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Dialog used to modify filter regexp and to enable/disable it.
 * 
 * @author Raffaele Ragni raffaele.ragni@gmail.com
 */
public class WifiFillterDialog extends Dialog
{
    public interface WifiFillterDialogOKListener
    {
        public void ok(boolean filter_enabled, boolean filter_inverse, String filter_regexp);
    }

    private WifiFillterDialogOKListener ok_listener;

    private boolean filter_enabled;

    private boolean filter_inverse;

    private String filter_regexp;

    @Override
    public void onCreate(Bundle icicle)
    {
        super.onCreate(icicle);

        setContentView(R.layout.wifi_filter_dialog);
        setTitle(R.string.WIFI_FILTER_DIALOG_TITLE);

        CheckBox ckFilterEnabled = (CheckBox) findViewById(R.id.filterEnabled);
        CheckBox ckFilterInverse = (CheckBox) findViewById(R.id.filterInverse);
        EditText editFilterRegexp = (EditText) findViewById(R.id.editFilter);
        Button buttonOK = (Button) findViewById(R.id.buttonOK);

        ckFilterEnabled.setChecked(filter_enabled);
        ckFilterInverse.setChecked(filter_inverse);
        editFilterRegexp.setText(filter_regexp);
        buttonOK.setOnClickListener(new android.view.View.OnClickListener()
        {
            public void onClick(View arg0)
            {
                CheckBox ckFilterEnabled = (CheckBox) findViewById(R.id.filterEnabled);
                CheckBox ckFilterInverse = (CheckBox) findViewById(R.id.filterInverse);
                EditText editFilterRegexp = (EditText) findViewById(R.id.editFilter);
                String regexp = editFilterRegexp.getText().toString();

                try
                {
                    Pattern.compile(regexp);
                    ok_listener.ok(ckFilterEnabled.isChecked(), ckFilterInverse.isChecked(), editFilterRegexp.getText().toString());
                }
                catch (PatternSyntaxException e)
                {
                    Toast.makeText(
                        WifiFillterDialog.this.getContext(),
                        "\""+regexp+"\" " + WifiFillterDialog.this.getContext().getResources().getString(R.string.THIS_REGEXP_IS_AN_INVALID_ONE),
                        Toast.LENGTH_SHORT)
                        .show();
                }

                WifiFillterDialog.this.dismiss();
            }
        });
    }

    public WifiFillterDialog(Context context, boolean filter_enabled, boolean filter_inverse, String filter_regexp, WifiFillterDialogOKListener ok_listener)
    {
        super(context);
        this.filter_enabled = filter_enabled;
        this.filter_inverse = filter_inverse;
        this.filter_regexp = filter_regexp;
        this.ok_listener = ok_listener;
    }
}

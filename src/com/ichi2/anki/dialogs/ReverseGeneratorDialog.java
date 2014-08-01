package com.ichi2.anki.dialogs;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.Button;

import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.CardEditor;
import com.ichi2.anki.R;
import com.ichi2.themes.Themes;


public class ReverseGeneratorDialog extends DialogFragment{
    public static final int NORMAL_DIALOG = 0;
    public static final int REVERSE_ALREADY_EXISTS_DIALOG = 1;
    public static final int MULTIPLE_CARDS_DIALOG = 2;
    
    
    private int mType;
    private int mNumCards;
    private String mNoteType;
    
    private WebView mWebView;
    /**
     * Create a new instance of ReverseGeneratorDialog
     */
    public static ReverseGeneratorDialog newInstance(int dialogType, int numCards, String noteType) {
        ReverseGeneratorDialog f = new ReverseGeneratorDialog();
        Bundle args = new Bundle();
        args.putInt("dialogType", dialogType);
        args.putInt("numCards", numCards);
        args.putString("noteType", noteType);
        f.setArguments(args);
        return f;
    }
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mType = getArguments().getInt("dialogType");
        mNumCards = getArguments().getInt("numCards");
        mNoteType = getArguments().getString("noteType");
    }
    
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        Resources res = getResources();
        getDialog().setTitle(res.getString(R.string.card_editor_reverse_cards));
        View v = inflater.inflate(R.layout.reverse_generator_dialog, container, false);
        // Setup main WebView
        WebView mWebView = (WebView) v.findViewById(R.id.reverse_generator_main_text);
        mWebView.setBackgroundColor(res.getColor(Themes.getBackgroundColor()));
        StringBuilder sb = new StringBuilder();
        sb.append("<html><body text=\"#000000\" link=\"#E37068\" alink=\"#E37068\" vlink=\"#E37068\">");
        String content;
        switch (mType) {
            case NORMAL_DIALOG:
                content = res.getString(R.string.reverse_generator_main);
                sb.append(String.format(content, mNumCards, mNoteType, res.getString(R.string.link_reverse_help)));
                break;
            case REVERSE_ALREADY_EXISTS_DIALOG:
                content = res.getString(R.string.reverse_generator_already_exists);
                sb.append(String.format(content, mNoteType, res.getString(R.string.link_reverse_help)));
                break;
            case MULTIPLE_CARDS_DIALOG:
                content = res.getString(R.string.reverse_generator_multiple_cards);
                sb.append(String.format(content, mNoteType, res.getString(R.string.link_reverse_help)));
                break;
        }
        sb.append("</body></html>");
        mWebView.loadDataWithBaseURL("", sb.toString(), "text/html", "utf-8", null);
        // Setup OK button
        Button positiveButton = (Button) v.findViewById(R.id.positive_button);
        positiveButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                // Load AnkiWeb username
                String hkey = AnkiDroidApp.getSharedPrefs(getActivity().getBaseContext()).getString("hkey", "");
                if (hkey.length() > 0) {
                    // show a warning about full-sync if the user is logged in
                    showFullSyncWarningDialog();
                } else {
                    // generate reverse cards
                    ((CardEditor) getActivity()).generateReverseCards();
                    dismiss();
                }
            }
        });
        // Disable OK button unless reverse cards can actually be generated
        if (mType != NORMAL_DIALOG) {
            positiveButton.setEnabled(false);
        }
        // Setup cancel button
        Button negativeButton = (Button)v.findViewById(R.id.negative_button);
        negativeButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                // clear the dialog
                dismiss();
            }
        });
        return v;
    }
    
    private void showFullSyncWarningDialog() {
        // Show a warning dialog explaining that a full-sync will be required
        Dialog fullSyncCheckDialog = new AlertDialog.Builder(ReverseGeneratorDialog.this.getActivity())
        .setTitle(R.string.dialog_full_sync_required_title)
        .setMessage(R.string.dialog_full_sync_required_text)
        .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener()
        {
            @Override
            public void onClick(DialogInterface dialog, int which)
            {
                ((CardEditor) getActivity()).generateReverseCards();
                ReverseGeneratorDialog.this.dismiss();
            }
        })
        .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener()
        {
            @Override
            public void onClick(DialogInterface dialog, int which)
            {
                ReverseGeneratorDialog.this.dismiss();
            }
        })
        .create();
        fullSyncCheckDialog.show();
    }
}
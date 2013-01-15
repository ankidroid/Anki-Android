package com.ichi2.anki;

import java.util.ArrayList;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.widget.ArrayAdapter;

public class PickStringDialogFragment extends DialogFragment
{
    private ArrayList<String> mPossibleChoices;
    private android.content.DialogInterface.OnClickListener mListener;
    private String mTitle;

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState)
    {
        // Use the Builder class for convenient dialog construction
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(mTitle);

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this.getActivity(),
                android.R.layout.simple_list_item_1, mPossibleChoices);

        builder.setAdapter(adapter, mListener);

        return builder.create();
    }

    public void setTitle(String mTitle)
    {
        this.mTitle = mTitle;
    }
    
    public void setChoices(ArrayList<String> possibleClones)
    {
        mPossibleChoices = possibleClones;
    }

    public void setOnclickListener(DialogInterface.OnClickListener listener)
    {
        mListener = listener;
    }

}

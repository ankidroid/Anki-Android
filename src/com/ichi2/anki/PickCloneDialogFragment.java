package com.ichi2.anki;

import java.util.ArrayList;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.widget.ArrayAdapter;

public class PickCloneDialogFragment extends DialogFragment
{
    private ArrayList<String> mPossibleChoices;
    private android.content.DialogInterface.OnClickListener mListener;

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState)
    {
        // Use the Builder class for convenient dialog construction
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        // TODO : Translation
        builder.setTitle("Pick");

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this.getActivity(),
                android.R.layout.simple_list_item_1, mPossibleChoices);

        builder.setAdapter(adapter, mListener);

        return builder.create();
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

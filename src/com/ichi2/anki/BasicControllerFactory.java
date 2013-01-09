package com.ichi2.anki;

import com.ichi2.anki.multimediacard.EFieldType;
import com.ichi2.anki.multimediacard.IField;

public class BasicControllerFactory implements IControllerFactory
{

    private BasicControllerFactory()
    {

    }

    public static IControllerFactory getInstance()
    {
        return new BasicControllerFactory();
    }

    @Override
    public IFieldController createControllerForField(IField field)
    {

        EFieldType type = field.getType();

        switch (type)
        {
            case TEXT:
                return new BasicTextFieldController();

            case IMAGE:
                return new BasicImageFieldController();

            default:

                break;
        }

        return null;
    }

}

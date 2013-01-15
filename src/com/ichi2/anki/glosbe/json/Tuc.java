package com.ichi2.anki.glosbe.json;

import java.util.List;

public class Tuc
{
    private List<Number> authors;
    private Number meaningId;
    private List<Meaning> meanings;
    private Phrase phrase;

    public List<Number> getAuthors()
    {
        return this.authors;
    }

    public void setAuthors(List<Number> authors)
    {
        this.authors = authors;
    }

    public Number getMeaningId()
    {
        return this.meaningId;
    }

    public void setMeaningId(Number meaningId)
    {
        this.meaningId = meaningId;
    }

    public List<Meaning> getMeanings()
    {
        return this.meanings;
    }

    public void setMeanings(List<Meaning> meanings)
    {
        this.meanings = meanings;
    }

    public Phrase getPhrase()
    {
        return this.phrase;
    }

    public void setPhrase(Phrase phrase)
    {
        this.phrase = phrase;
    }
}
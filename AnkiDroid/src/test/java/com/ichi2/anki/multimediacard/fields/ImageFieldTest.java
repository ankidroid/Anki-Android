package com.ichi2.anki.multimediacard.fields;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;

import java.io.File;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

@RunWith(AndroidJUnit4.class)
public class ImageFieldTest {

    /** #5237 - quotation marks on Android differed from Windows */
    @Test
    public void imageValueIsConsistentWithAnkiDesktop() {
        //Arrange
        File mockedFile = Mockito.mock(File.class);
        Mockito.when(mockedFile.exists()).thenReturn(true);
        Mockito.when(mockedFile.getName()).thenReturn("paste-abc.jpg");

        //Act
        String actual = ImageField.formatImageFileName(mockedFile);

        //Assert
        //This differs between AnkDesktop Version 2.0.51 and 2.1.22
        //2.0:  "<img src=\"paste-abc.jpg\" />";
        //2.1: (note: no trailing slash or space)
        String expected = "<img src=\"paste-abc.jpg\">";
        assertThat(actual, equalTo(expected));
    }
}

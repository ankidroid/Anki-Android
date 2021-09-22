/*
 *  Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.multimediacard.fields;

import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Media;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;

import java.io.File;

import androidx.annotation.CheckResult;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

    @Test
    public void validImageParses() {
        String goodImage = "<img src='img_202003291657428441724378214970132.png'/>";
        String imageSrc = ImageField.parseImageSrcFromHtml(goodImage);
        assertThat(imageSrc, equalTo("img_202003291657428441724378214970132.png"));
    }

    @Test
    public void testImageSubstringParsing() {
        //5874 - previously failed
        String previouslyBadImage = "<img src='img_202003291657428441724378214970132.png'/>aaaa'/>";
        String imageSrc = ImageField.parseImageSrcFromHtml(previouslyBadImage);
        assertThat(imageSrc, equalTo("img_202003291657428441724378214970132.png"));
    }

    @Test
    public void firstImageIsSelected() {
        String goodImage = "<img src='1.png'/>aa<img src='2.png'/>";
        String imageSrc = ImageField.parseImageSrcFromHtml(goodImage);
        assertThat(imageSrc, equalTo("1.png"));
    }

    @Test
    public void testNoImage() {
        String knownBadImage = "<br />";
        String imageSrc = ImageField.parseImageSrcFromHtml(knownBadImage);
        assertThat(imageSrc, equalTo(""));
    }

    @Test
    public void testEmptyImage() {
        String knownBadImage = "<img />";
        String imageSrc = ImageField.parseImageSrcFromHtml(knownBadImage);
        assertThat(imageSrc, equalTo(""));
    }

    @Test
    public void testNoImagePathIsNothing() {
        String knownBadImage = "<br />";
        Collection col = collectionWithMediaFolder("media");

        String imageSrc = ImageField.getImageFullPath(col, knownBadImage);

        assertThat("no media should return no paths", imageSrc, equalTo(""));
    }

    @Test
    public void testNoImagePathConcat() {
        String goodImage = "<img src='1.png'/>";
        Collection col = collectionWithMediaFolder("media");

        String imageSrc = ImageField.getImageFullPath(col, goodImage);

        assertThat("Valid media should have path", imageSrc, equalTo("media/1.png"));
    }

    @CheckResult
    protected Collection collectionWithMediaFolder(String dir) {
        Media media = mock(Media.class);
        when(media.dir()).thenReturn(dir);

        Collection collectionMock = mock(Collection.class);
        when(collectionMock.getMedia()).thenReturn(media);
        return collectionMock;
    }
}

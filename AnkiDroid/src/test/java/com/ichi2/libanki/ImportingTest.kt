/*
 *  Copyright (c) 2020 Arthur Milchior <arthur@milchior.fr>
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
package com.ichi2.libanki

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImportingTest : RobolectricTest() {
    @Test
    fun empty_test() {
        // A test should occurs in the file, otherwise travis rejects. This remains here until we can uncomment the real tests.
    }
    /****************
     * Importing    *
     */
    /*
      private void clear_tempfile(tf) {
      ;
      " https://stackoverflow.com/questions/23212435/permission-denied-to-write-to-my-temporary-file ";
      try {
      tf.close();
      os.unlink(tf.name);
      } catch () {
      }
      }

      @Test
      public void test_anki2_mediadupes(){
      Collection col = getCol();
      // add a note that references a sound
      Note n = tmp.newNote();
      n.setItem("Front", "[sound:foo.mp3]");
      mid = n.model().getLong("id");
      col.addNote(n);
      // add that sound to media directory
      with open(os.path.join(col.getMedia().dir(), "foo.mp3"), "w") as note:
      note.write("foo");
      col.close();
      // it should be imported correctly into an empty deck
      Collection empty = getCol();
      Anki2Importer imp = Anki2Importer(empty, col.getPath());
      imp.run();
      assertEqualsArrayList(new String [] {"foo.mp3"}, os.listdir(empty.getMedia().dir()));
      // and importing again will not duplicate, as the file content matches
      empty.remCards(empty.getDb().test_removequeryLongList("select id from cards"));
      Anki2Importer imp = Anki2Importer(empty, col.getPath());
      imp.run();
      assertEqualsArrayList(new String [] {"foo.mp3"}, os.listdir(empty.getMedia().dir()));
      Note n = empty.getNote(empty.getDb().queryLongScalar("select id from notes"));
      assertThat(n.fields[0], containsString("foo.mp3"));
      // if the local file content is different, and import should trigger a
      // rename
      empty.remCards(empty.getDb().queryLongList("select id from cards"));
      with open(os.path.join(empty.getMedia().dir(), "foo.mp3"), "w") as note:
      note.write("bar");
      Anki2Importer imp = Anki2Importer(empty, col.getPath());
      imp.run();
      assertEqualsArrayList(new String [] {"foo.mp3", "foo_"+mid+".mp3"}, sorted(os.listdir(empty.getMedia().dir())));
      Note n = empty.getNote(empty.getDb().queryLongScalar("select id from notes"));
      assertThat(n.fields[0], containsString("_"));
      // if the localized media file already exists, we rewrite the note and
      // media
      empty.remCards(empty.getDb().queryLongList("select id from cards"));
      with open(os.path.join(empty.getMedia().dir(), "foo.mp3"), "w") as note:
      note.write("bar");
      Anki2Importer imp = Anki2Importer(empty, col.getPath());
      imp.run();
      assertEqualsArrayList(new String [] {"foo.mp3", "foo_"+mid+".mp3" }, sorted(os.listdir(empty.getMedia().dir())));
      assertEqualsArrayList(new String [] {"foo.mp3", "foo_"+mid+".mp3"}, sorted(os.listdir(empty.getMedia().dir())));
      Note n = empty.getNote(empty.getDb().queryLongScalar("select id from notes"));
      assertThat(n.fields[0], containsString("_"));
      }

      @Test
      public void test_apkg(){
      Collection col = getCol();
      String apkg = str(os.path.join(testDir, "support/media.apkg"));
      AnkiPackageImporter imp = AnkiPackageImporter(col, apkg);
      assertEqualsArrayList(new String [] {}, os.listdir(col.getMedia().dir()));
      imp.run();
      assertEqualsArrayList(new String [] {"foo.wav"}, os.listdir(col.getMedia().dir()));
      // importing again should be idempotent in terms of media
      col.remCards(col.getDb().queryLongList("select id from cards"));
      AnkiPackageImporter imp = AnkiPackageImporter(col, apkg);
      imp.run();
      assertEqualsArrayList(new String [] {"foo.wav"}, os.listdir(col.getMedia().dir()));
      // but if the local file has different data, it will rename
      col.remCards(col.getDb().queryLongList("select id from cards"));
      with open(os.path.join(col.getMedia().dir(), "foo.wav"), "w") as note:
      note.write("xyz");
      imp = AnkiPackageImporter(col, apkg);
      imp.run();
      assertEquals(2, os.listdir(col.getMedia().dir()).size());
      }

      @Test
      public void test_anki2_diffmodel_templates(){
      // different from the above as this one tests only the template text being
      // changed, not the number of cards/fields
      Collection dst = getCol();
      // import the first version of the model
      Collection col = getUpgradeDeckPath("diffmodeltemplates-1.apkg");
      AnkiPackageImporter imp = AnkiPackageImporter(dst, col);
      imp.dupeOnSchemaChange = true;
      imp.run();
      // then the version with updated template
      Collection col = getUpgradeDeckPath("diffmodeltemplates-2.apkg");
      imp = AnkiPackageImporter(dst, col);
      imp.dupeOnSchemaChange = true;
      imp.run();
      // collection should contain the note we imported
      assertEquals(1, dst.noteCount());
      // the front template should contain the text added in the 2nd package
      tlong cid = dst.findCards("")[0]  // only 1 note in collection
      tNote note = dst.getCard(tcid).note(col);
      assertThat(tnote.cards().get(0).template(col).getString("qfmt"), containsString("Changed Front Template"));
      }

      @Test
      public void test_anki2_updates(){
      // create a new empty deck
      dst = getCol();
      Collection col = getUpgradeDeckPath("update1.apkg");
      AnkiPackageImporter imp = AnkiPackageImporter(dst, col);
      imp.run();
      assertEquals(0, imp.dupes);
      assertEquals(1, imp.added);
      assertEquals(0, imp.updated);
      // importing again should be idempotent
      imp = AnkiPackageImporter(dst, col);
      imp.run();
      assertEquals(1, imp.dupes);
      assertEquals(0, imp.added);
      assertEquals(0, imp.updated);
      // importing a newer note should update
      assertEquals(1, dst.noteCount());
      assertTrue(dst.getDb().queryLongScalar("select flds from notes").startswith("hello"));
      Collection col = getUpgradeDeckPath("update2.apkg");
      imp = AnkiPackageImporter(dst, col);
      imp.run();
      assertEquals(0, imp.dupes);
      assertEquals(0, imp.added);
      assertEquals(1, imp.updated);
      assertEquals(1, dst.noteCount());
      assertTrue(dst.getDb().queryLongScalar("select flds from notes").startswith("goodbye"));
      }

      @Test
      public void test_csv(){
      Collection col = getCol();
      file = str(os.path.join(testDir, "support/text-2fields.txt"));
      i = TextImporter(col, file);
      i.initMapping();
      i.run();
      // four problems - too many & too few fields, a missing front, and a
      // duplicate entry
      assertEquals(5, i.log.size());
      assertEquals(5, i.total);
      // if we run the import again, it should update instead
      i.run();
      assertEquals(10, i.log.size());
      assertEquals(5, i.total);
      // but importing should not clobber tags if they're unmapped
      Note n = col.getNote(col.getDb().queryLongScalar("select id from notes"));
      n.addTag("test");
      n.flush();
      i.run();
      n.load();
      assertEqualsArrayList(new String [] {"test"}, n.tags);
      // if add-only mode, count will be 0
      i.importMode = 1;
      i.run();
      assertEquals(0, i.total);
      // and if dupes mode, will reimport everything
      assertEquals(5, col.cardCount());
      i.importMode = 2;
      i.run();
      // includes repeated field
      assertEquals(6, i.total);
      assertEquals(11, col.cardCount());
      col.close();
      }

      @Test
      public void test_csv2(){
      Collection col = getCol();
      Models mm = col.getModels();
      Model m = mm.current();
      Note note = mm.newField("Three");
      mm.addField(m, note);
      mm.save(m);
      Note n = col.newNote();
      n.setItem("Front", "1");
      n.setItem("Back", "2");
      n.setItem("Three", "3");
      col.addNote(n);
      // an update with unmapped fields should not clobber those fields
      file = str(os.path.join(testDir, "support/text-update.txt"));
      TextImporter i = TextImporter(col, file);
      i.initMapping();
      i.run();
      n.load();
      assertTrue(n.setItem("Front",= "1"));
      assertTrue(n.setItem("Back",= "x"));
      assertTrue(n.setItem("Three",= "3"));
      col.close();
      }

      @Test
      public void test_tsv_tag_modified(){
      Collection col = getCol();
      Models mm = col.getModels();
      Model m = mm.current();
      Note note = mm.newField("Top");
      mm.addField(m, note);
      mm.save(m);
      Note n = col.newNote();
      n.setItem("Front", "1");
      n.setItem("Back", "2");
      n.setItem("Top", "3");
      n.addTag("four");
      col.addNote(n);

      // https://stackoverflow.com/questions/23212435/permission-denied-to-write-to-my-temporary-file
      with NamedTemporaryFile(mode="w", delete=false) as tf:
      tf.write("1\tb\tc\n");
      tf.flush();
      TextImporter i = TextImporter(col, tf.name);
      i.initMapping();
      i.tagModified = "boom";
      i.run();
      clear_tempfile(tf);

      n.load();
      assertTrue(n.setItem("Front",= "1"));
      assertTrue(n.setItem("Back",= "b"));
      assertTrue(n.setItem("Top",= "c"));
      assertThat(n.getTags(), containsString("four"));
      assertThat(n.getTags(), containsString("boom"));
      assertEquals(2, n.getTags().size());
      assertEquals(1, i.updateCount);

      col.close();
      }

      @Test
      public void test_tsv_tag_multiple_tags(){
      Collection col = getCol();
      Models mm = col.getModels();
      Model m = mm.current();
      Note note = mm.newField("Top");
      mm.addField(m, note);
      mm.save(m);
      Note n = col.newNote();
      n.setItem("Front", "1");
      n.setItem("Back", "2");
      n.setItem("Top", "3");
      n.addTag("four");
      n.addTag("five");
      col.addNote(n);

      // https://stackoverflow.com/questions/23212435/permission-denied-to-write-to-my-temporary-file
      with NamedTemporaryFile(mode="w", delete=false) as tf:
      tf.write("1\tb\tc\n");
      tf.flush();
      TextImporter i = TextImporter(col, tf.name);
      i.initMapping();
      i.tagModified = "five six";
      i.run();
      clear_tempfile(tf);

      n.load();
      assertTrue(n.setItem("Front",= "1"));
      assertTrue(n.setItem("Back",= "b"));
      assertTrue(n.setItem("Top",= "c"));
      assertEquals(list(sorted(new String [] {"four", "five", "six"}, list(sorted(n.getTags())))));

      col.close();
      }

      @Test
      public void test_csv_tag_only_if_modified(){
      Collection col = getCol();
      Models mm = col.getModels();
      Model m = mm.current();
      Note note = mm.newField("Left");
      mm.addField(m, note);
      mm.save(m);
      Note n = col.newNote();
      n.setItem("Front", "1");
      n.setItem("Back", "2");
      n.setItem("Left", "3");
      col.addNote(n);

      // https://stackoverflow.com/questions/23212435/permission-denied-to-write-to-my-temporary-file
      with NamedTemporaryFile(mode="w", delete=false) as tf:
      tf.write("1,2,3\n");
      tf.flush();
      TextImporter i = TextImporter(col, tf.name);
      i.initMapping();
      i.tagModified = "right";
      i.run();
      clear_tempfile(tf);

      n.load();
      assertEqualsArrayList(new String [] {}, n.tags);
      assertEquals(0, i.updateCount);

      col.close();
      }

      @pytest.mark.filterwarnings("ignore:Using or importing the ABCs")
      @Test
      public void test_supermemo_xml_01_unicode(){
      Collection col = getCol();
      String file = str(os.path.join(testDir, "support/supermemo1.xml"));
      SupermemoXmlImporter i = SupermemoXmlImporter(col, file);
      // i.META.logToStdOutput = true
      i.run();
      assertEquals(1, i.total);
      long cid = col.getDb().queryLongScalar("select id from cards");
      Card c = col.getCard(cid);
      // Applies A Factor-to-E Factor conversion
      assertEquals(2879, c.getFactor());
      assertEquals(7, c.getReps());
      col.close();
      }

      @Test
      public void test_mnemo(){
      Collection col = getCol();
      String file = str(os.path.join(testDir, "support/mnemo.getDb()"));
      MnemosyneImporter i = MnemosyneImporter(col, file);
      i.run();
      assertEquals(7, col.cardCount());
      assertThat(col.getTags().all(), containsString("a_longer_tag"));
      assertEquals(1, col.getDb().queryScalar("select count() from cards where type = 0"));
      col.close()
      }
    */
}

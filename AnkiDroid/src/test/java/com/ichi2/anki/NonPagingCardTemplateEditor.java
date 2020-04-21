package com.ichi2.anki;

import androidx.fragment.app.FragmentManager;

// Robolectric is great, but ViewPager support is very incomplete.
// We have to avoid paging or we get exceptions
// https://github.com/robolectric/robolectric/issues/3698
class NonPagingCardTemplateEditor extends CardTemplateEditor {
    public static int pagerCount = 2;


    public void selectTemplate(int idx) { /* do nothing */ }


    public TemplatePagerAdapter getNewTemplatePagerAdapter(FragmentManager fm) {
        return new TestTemplatePagerAdapter(fm);
    }


    class TestTemplatePagerAdapter extends CardTemplateEditor.TemplatePagerAdapter {
        private TestTemplatePagerAdapter(FragmentManager fm) {
            super(fm);
        }


        public int getCount() {
            return pagerCount;
        }
    }
}
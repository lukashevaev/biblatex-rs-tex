package com.ols.ruslan.neo;

import de.undercouch.citeproc.ItemDataProvider;
import de.undercouch.citeproc.csl.CSLItemData;
import de.undercouch.citeproc.csl.CSLItemDataBuilder;
import de.undercouch.citeproc.csl.CSLType;

public class MyItemProvider implements ItemDataProvider {
    @Override
    public CSLItemData retrieveItem(String s) {
        return new CSLItemDataBuilder()
                .id(String.valueOf(3))
                .type(CSLType.ARTICLE_JOURNAL)
                .title("A dummy journal article")
                .author("John", "Smith")
                .issued(2013, 9, 6)
                .containerTitle("Dummy journal")
                .build();
    }

    @Override
    public String[] getIds() {
        return new String[] {"ID-0", "ID-1", "ID-2"};
    }
}

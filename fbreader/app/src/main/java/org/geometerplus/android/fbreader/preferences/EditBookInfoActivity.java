/*
 * Copyright (C) 2009-2015 FBReader.ORG Limited <contact@fbreader.org>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 * 02110-1301, USA.
 */

package org.geometerplus.android.fbreader.preferences;

import java.util.*;

import android.content.Context;
import android.content.Intent;
import android.preference.Preference;

import org.geometerplus.zlibrary.core.encodings.Encoding;
import org.geometerplus.zlibrary.core.language.Language;
import org.geometerplus.zlibrary.core.resources.ZLResource;

import org.geometerplus.zlibrary.text.hyphenation.ZLTextHyphenator;

import org.geometerplus.fbreader.Paths;
import org.geometerplus.fbreader.book.*;
import org.geometerplus.fbreader.formats.*;

import org.geometerplus.android.fbreader.api.FBReaderIntents;
import org.geometerplus.android.fbreader.libraryService.BookCollectionShadow;
import org.geometerplus.android.util.*;

class BookTitlePreference extends ZLStringPreference {
	private final Book myBook;

	BookTitlePreference(Context context, ZLResource rootResource, String resourceKey, Book book) {
		super(context, rootResource, resourceKey);
		myBook = book;
		super.setValue(book.getTitle());
	}

	@Override
	protected void setValue(String value) {
		super.setValue(value);
		myBook.setTitle(value);
		((EditBookInfoActivity)getContext()).saveBook();
	}
}

class BookLanguagePreference extends LanguagePreference {
	private final Book myBook;

	private static List<Language> languages() {
		final TreeSet<Language> set = new TreeSet<Language>();
		for (String code : ZLTextHyphenator.Instance().languageCodes()) {
			set.add(new Language(code));
		}
		set.add(new Language(Language.OTHER_CODE));
		return new ArrayList<Language>(set);
	}

	BookLanguagePreference(Context context, ZLResource resource, Book book) {
		super(context, resource, languages());
		myBook = book;
		final String language = myBook.getLanguage();
		if (language == null || !setInitialValue(language)) {
			setInitialValue(Language.OTHER_CODE);
		}
	}

	@Override
	protected void init() {
	}

	@Override
	protected void setLanguage(String code) {
		myBook.setLanguage(code.length() > 0 ? code : null);
		((EditBookInfoActivity)getContext()).saveBook();
	}
}

class EncodingPreference extends ZLStringListPreference {
	private final PluginCollection myPluginCollection;
	private final Book myBook;

	EncodingPreference(Context context, ZLResource resource, Book book) {
		super(context, resource);
		myBook = book;
		myPluginCollection = PluginCollection.Instance(Paths.systemInfo(context));

		final FormatPlugin plugin;
		try {
			plugin = BookUtil.getPlugin(myPluginCollection, book);
		} catch (BookReadingException e) {
			return;
		}

		final List<Encoding> encodings =
			new ArrayList<Encoding>(plugin.supportedEncodings().encodings());
		Collections.sort(encodings, new Comparator<Encoding>() {
			public int compare(Encoding e1, Encoding e2) {
				return e1.DisplayName.compareTo(e2.DisplayName);
			}
		});
		final String[] codes = new String[encodings.size()];
		final String[] names = new String[encodings.size()];
		int index = 0;
		for (Encoding e : encodings) {
			//addItem(e.Family, e.Name, e.DisplayName);
			codes[index] = e.Name;
			names[index] = e.DisplayName;
			++index;
		}
		setLists(codes, names);
		if (encodings.size() == 1) {
			setInitialValue(codes[0]);
			setEnabled(false);
		} else {
			final String bookEncoding = BookUtil.getEncoding(book, myPluginCollection);
			if (bookEncoding != null) {
				setInitialValue(bookEncoding.toLowerCase());
			}
		}
	}

	@Override
	protected void onDialogClosed(boolean result) {
		super.onDialogClosed(result);
		if (result) {
			final String value = getValue();
			if (!value.equalsIgnoreCase(BookUtil.getEncoding(myBook, myPluginCollection))) {
				myBook.setEncoding(value);
				((EditBookInfoActivity)getContext()).saveBook();
			}
		}
	}
}

class EditTagsPreference extends Preference {
	private static final int SUMMARY_LEN = 3;
	protected final ZLResource myResource;
	private final Book myBook;
	private ArrayList<String> myTagsList;

	EditTagsPreference(Context context, ZLResource rootResource, String resourceKey, Book book) {
		super(context);
		myBook = book;
		myResource = rootResource.getResource(resourceKey);
		updateTagsList();

		setTitle(myResource.getValue());
		setSummary(getTagsSummary());
	}

	private void updateTagsList(){
		myTagsList = new ArrayList<String>();
		if(myBook != null){
			for (Tag tag : myBook.tags()) {
				myTagsList.add(tag.Name);
			}
		}
	}

	private String getTagsSummary(){
		String tagsStr = new String();
		int i = 0;
		for(String t : myTagsList){
			if(i < SUMMARY_LEN){
				if(i != 0) tagsStr +=", ";
				tagsStr += t;
				i++;
			}else{
				break;
			}
		}
		if(myTagsList.size() > SUMMARY_LEN){
			tagsStr += "...";
		}
		return tagsStr;
	}

	void saveTags(final ArrayList<String> tags) {
		if(tags.size() == 0)
			return;

		myBook.removeAllTags();
		for (String t : tags) {
			myBook.addTag(t);
		}
		((EditBookInfoActivity)getContext()).saveBook();
		updateTagsList();
		setSummary(getTagsSummary());
	}

	@Override
	protected void onClick() {
		Intent intent = new Intent(getContext(), EditTagsDialogActivity.class);
		intent.putExtra(EditListDialogActivity.Key.ACTIVITY_TITLE, myResource.getValue());

		ArrayList<String> allTags = new ArrayList<String>();
		for (Tag tag : ((EditBookInfoActivity)getContext()).tags()) {
			allTags.add(tag.Name);
		}
		intent.putExtra(EditListDialogActivity.Key.LIST, myTagsList);
		intent.putExtra(EditListDialogActivity.Key.ALL_ITEMS_LIST, allTags);
		((EditBookInfoActivity)getContext()).startActivityForResult(intent, EditTagsDialogActivity.REQ_CODE);
	}
}

class EditAuthorsPreference extends Preference {
	private static final int SUMMARY_LEN = 2;
	protected final ZLResource myResource;
	private final Book myBook;
	private ArrayList<String> myAuthorsList;

	EditAuthorsPreference(Context context, ZLResource rootResource, String resourceKey, Book book) {
		super(context);
		myBook = book;
		myResource = rootResource.getResource(resourceKey);
		updateAuthorsList();

		setTitle(myResource.getValue());
		setSummary(getAuthorsSummary());
	}

	private void updateAuthorsList(){
		myAuthorsList = new ArrayList<String>();
		if(myBook != null){
			for (Author author : myBook.authors()) {
				myAuthorsList.add(author.DisplayName);
			}
		}
	}

	private String getAuthorsSummary(){
		String authorsStr = new String();
		int i = 0;
		for(String t : myAuthorsList){
			if(i < SUMMARY_LEN){
				if(i != 0) authorsStr +=", ";
				authorsStr += t;
				i++;
			}else{
				break;
			}
		}
		if(myAuthorsList.size() > SUMMARY_LEN){
			authorsStr += "...";
		}
		return authorsStr;
	}

	void saveAuthors(final ArrayList<String> authors) {
		if (authors.size() == 0) {
			return;
		}

		myBook.removeAllAuthors();
		for (String a : authors) {
			myBook.addAuthor(a);
		}
		((EditBookInfoActivity)getContext()).saveBook();
		updateAuthorsList();
		setSummary(getAuthorsSummary());
	}

	@Override
	protected void onClick() {
		final Intent intent = new Intent(getContext(), EditAuthorsDialogActivity.class);
		intent.putExtra(EditListDialogActivity.Key.ACTIVITY_TITLE, myResource.getValue());

		((EditBookInfoActivity)getContext()).saveBook();
		final ArrayList<String> allAuthors = new ArrayList<String>();
		for (Author author : ((EditBookInfoActivity)getContext()).authors()) {
			allAuthors.add(author.DisplayName);
		}
		intent.putExtra(EditListDialogActivity.Key.LIST, myAuthorsList);
		intent.putExtra(EditListDialogActivity.Key.ALL_ITEMS_LIST, allAuthors);
		((EditBookInfoActivity)getContext()).startActivityForResult(intent, EditAuthorsDialogActivity.REQ_CODE);
	}
}

class EditSeriesPreference extends Preference {
	private static final int SUMMARY_LEN = 2;
	protected final ZLResource myResource;
	private final Book myBook;
	private ArrayList<String> mySeriesList;

	EditSeriesPreference(Context context, ZLResource rootResource, String resourceKey, Book book) {
		super(context);
		myBook = book;
		myResource = rootResource.getResource(resourceKey);

		setTitle(myResource.getValue());
		setSummary(getSeriesSummary());
	}
	
	private String getSeriesSummary(){
		String seriesStr = new String();
        if(myBook.getSeriesInfo() != null){
            if(!myBook.getSeriesInfo().Series.getTitle().isEmpty()){
                seriesStr = myBook.getSeriesInfo().Series.getTitle();
                if(myBook.getSeriesInfo().Index.intValue() > 0){
                    seriesStr += ", "+String.valueOf(myBook.getSeriesInfo().Index.intValue());
                }
            }
		}
		return seriesStr;
	}

	void saveSeries(String seriesName, int seriesIndex) {
		if (seriesIndex < 0) {
			return;
		}
        myBook.setSeriesInfo(seriesName, Integer.toString(seriesIndex));

		((EditBookInfoActivity)getContext()).saveBook();
		setSummary(getSeriesSummary());
	}

	@Override
	protected void onClick() {
		final Intent intent = new Intent(getContext(), EditSeriesDialogActivity.class);
		intent.putExtra(EditSeriesDialogActivity.Key.ACTIVITY_TITLE, myResource.getValue());

		//TODO: Get a series from a book
		SeriesInfo info = myBook.getSeriesInfo();
		String seriesName = (info != null) ? info.Series.getTitle() : "";
		int seriesIndex = (info != null) ? info.Index.intValue() : 0;

		intent.putExtra(EditSeriesDialogActivity.Key.SERIES_NAME, seriesName);
		intent.putExtra(EditSeriesDialogActivity.Key.SERIES_INDEX, seriesIndex);
		intent.putExtra(EditSeriesDialogActivity.Key.ALL_SERIES, new ArrayList(((EditBookInfoActivity)getContext()).series()));
		((EditBookInfoActivity)getContext()).startActivityForResult(intent, EditSeriesDialogActivity.REQ_CODE);
	}
}

public class EditBookInfoActivity extends ZLPreferenceActivity {
	private final BookCollectionShadow myCollection = new BookCollectionShadow();
	private volatile boolean myInitialized;

	private EditTagsPreference myEditTagsPreference;
	private EditAuthorsPreference myEditAuthorsPreference;
	private EditSeriesPreference myEditSeriesPreference;
	private Book myBook;

	public EditBookInfoActivity() {
		super("BookInfo");
	}

	void saveBook() {
		myCollection.bindToService(this, new Runnable() {
			public void run() {
				myCollection.saveBook(myBook);
			}
		});
	}

	List<Author> authors() {
		return myCollection.authors();
	}

	List<Tag> tags() {
		return myCollection.tags();
	}
	
	List<String> series() {
		return myCollection.series();
	}

	@Override
	protected void init(Intent intent) {
	}

	@Override
	protected void onStart() {
		super.onStart();

		myBook = FBReaderIntents.getBookExtra(getIntent(), myCollection);

		if (myBook == null) {
			finish();
			return;
		}

		myCollection.bindToService(this, new Runnable() {
			public void run() {
				if (myInitialized) {
					return;
				}
				myInitialized = true;

				addPreference(new BookTitlePreference(EditBookInfoActivity.this, Resource, "title", myBook));
				myEditAuthorsPreference = (EditAuthorsPreference)addPreference(new EditAuthorsPreference(EditBookInfoActivity.this, Resource, "authors", myBook));
				myEditTagsPreference = (EditTagsPreference)addPreference(new EditTagsPreference(EditBookInfoActivity.this, Resource, "tags", myBook));
				myEditSeriesPreference = (EditSeriesPreference)addPreference(new EditSeriesPreference(EditBookInfoActivity.this, Resource, "series", myBook));
				addPreference(new BookLanguagePreference(EditBookInfoActivity.this, Resource.getResource("language"), myBook));
				addPreference(new EncodingPreference(EditBookInfoActivity.this, Resource.getResource("encoding"), myBook));
			}
		});
	}

	@Override
	protected void onStop() {
		myCollection.unbind();
		super.onStop();
	}

	@Override
	protected void onActivityResult(int reqCode, int resultCode, Intent data) {
		if (resultCode == RESULT_OK) {
			switch (reqCode) {
				case EditTagsDialogActivity.REQ_CODE:
					myEditTagsPreference.saveTags(data.getStringArrayListExtra(EditListDialogActivity.Key.LIST));
					break;
				case EditAuthorsDialogActivity.REQ_CODE:
					myEditAuthorsPreference.saveAuthors(data.getStringArrayListExtra(EditListDialogActivity.Key.LIST));
					break;
				case EditSeriesDialogActivity.REQ_CODE:
					myEditSeriesPreference.saveSeries(
                        data.getStringExtra(EditSeriesDialogActivity.Key.SERIES_NAME),
                        data.getIntExtra(EditSeriesDialogActivity.Key.SERIES_INDEX, 0));
					break;
			}
		}
	}
}

package org.texttechnologylab.uima.conll.extractor;

import java.util.ArrayList;

public interface IConllFeatures {
	void name(String name);
	
	String name();
	
	void prependTag(String tag);
	
	String getPrependTag();
	
	boolean isNameInvalid();
	
	ArrayList<String> build();
	
	boolean isOut();
}

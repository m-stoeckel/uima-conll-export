package org.texttechnologylab.uima.conll.extractor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

public class SingleConllFeatures extends ArrayList<String> implements IConllFeatures {
	
	private String prependTag = "";
	
	public SingleConllFeatures() {
		super(Collections.singletonList("O"));
	}
	
	public SingleConllFeatures(String initalElement) {
		super(Collections.singletonList(""));
		this.name(initalElement);
	}
	
	@Override
	public void name(String name) {
		this.set(0, name.replaceAll("([IB]-)*", ""));
	}
	
	@Override
	public String name() {
		return this.get(0);
	}
	
	@Override
	public void prependTag(String tag) {
		this.prependTag = tag;
	}
	
	@Override
	public String getPrependTag() {
		return prependTag;
	}
	
	@Override
	public boolean isNameInvalid() {
		return this.get(0) == null || this.get(0).isEmpty();
	}
	
	@Override
	public ArrayList<String> build() {
		return new ArrayList<>(Collections.singletonList(this.prependTag + this.name()));
	}
	
	@Override
	public boolean isOut() {
		return this.name() == null || this.name().isEmpty() || this.name().equals("O");
	}
}

package org.texttechnologylab.uima.conll.extractor;

import java.util.ArrayList;
import java.util.Arrays;

public class TTLabConllFeatures extends ArrayList<String> implements IConllFeatures {
	
	private String prependTag = "";
	
	public TTLabConllFeatures() {
		super(Arrays.asList("O", "", ""));
	}
	
	public TTLabConllFeatures(String initalElement) {
		super(Arrays.asList("", "", ""));
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
	
	public void setAbstract(boolean b) {
		if (b)
			this.set(1, "<ABSTRACT>");
		else
			this.set(1, "<CONCRETE>");
	}
	
	public boolean isAbstract() {
		return this.get(1).equals("<ABSTRACT>");
	}
	
	public void setMetaphor(boolean b) {
		if (b)
			this.set(2, "<METAPHOR>");
		else
			this.set(2, "<DIRECT>");
		
	}
	
	public boolean isMetaphor() {
		return this.get(2).equals("<ABSTRACT>");
	}
	
	@Override
	public ArrayList<String> build() {
		ArrayList<String> retList = new ArrayList<>();
		retList.add(this.prependTag + this.name());
		for (int i = 1; i < this.size(); i++) {
			if (this.get(i) != null && !this.get(i).isEmpty())
				retList.add(this.get(i));
		}
		return retList;
	}
	
	@Override
	public boolean isOut() {
		return this.get(0) == null || this.get(0).isEmpty() || this.get(0).equals("O");
	}
}

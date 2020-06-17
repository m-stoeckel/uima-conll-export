package org.texttechnologylab.uima.conll.iobencoder;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token;
import org.apache.uima.UIMAException;
import org.apache.uima.cas.Type;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;
import org.jetbrains.annotations.NotNull;
import org.texttechnologylab.annotation.AbstractNamedEntity;
import org.texttechnologylab.annotation.NamedEntity;
import org.texttechnologylab.uima.conll.extractor.IConllFeatures;

import java.util.*;
import java.util.stream.Collectors;

import static org.apache.uima.fit.util.JCasUtil.indexCovered;
import static org.apache.uima.fit.util.JCasUtil.select;

public class TTLabOneColumnPerClassEncoder extends TTLabHierarchicalIobEncoder {
	
	private ArrayList<String> namedEntityTypes = Lists.newArrayList("Animal_Fauna",
			"Archaea", "Bacteria", "Chromista", "Fungi",
//			"Habitat",
			"Lichen", "Plant_Flora", "Protozoa", "Taxon", "Viruses");
	private ArrayList<String> presentNamedEntityTypes = namedEntityTypes;
	private LinkedHashSet<Annotation> namedEntities;
	
	public TTLabOneColumnPerClassEncoder(JCas jCas) throws UIMAException {
		super(jCas);
	}
	
	public TTLabOneColumnPerClassEncoder(JCas jCas, ImmutableSet<String> annotatorSet) throws UIMAException {
		super(jCas, annotatorSet);
	}
	
	public TTLabOneColumnPerClassEncoder(JCas jCas, ArrayList<Class<? extends Annotation>> includeAnnotations, ImmutableSet<String> annotatorSet) throws UIMAException {
		super(jCas, includeAnnotations, annotatorSet);
	}
	
	@Override
	public void build() {
		try {
			if (jCas.getDocumentText() == null)
				return;
			
			mergeViews();
			
			namedEntities = new LinkedHashSet<>();
			namedEntities.addAll(select(mergedCas, NamedEntity.class));
			namedEntities.addAll(select(mergedCas, AbstractNamedEntity.class));
			
			// Initialize the hierarchy
			if (onlyPrintPresent) {
				presentNamedEntityTypes = namedEntities.stream()
						.map(Annotation::getType)
						.map(Type::getShortName)
						.distinct()
						.filter(namedEntityTypes::contains)
						.sorted()
						.collect(Collectors.toCollection(ArrayList::new));
			}
			
			// Flatten the new view by removing identical duplicates
			if (removeDuplicateSameType) {
				getLogger().debug("Removing duplicates");
				removeDuplicates(namedEntities);
			}
			
			getLogger().debug("Initializing hierarchy");
			// Create an empty list for all layers of NEs for each Token
			ArrayList<Token> tokens = new ArrayList<>(select(mergedCas, Token.class));
			for (int i = 0; i < tokens.size(); i++) {
				Token token = tokens.get(i);
				tokenIndexMap.put(i, token);
				hierachialTokenNamedEntityMap.put(token, getEmptyConllFeatureList());
			}
			
			getLogger().debug("Building indices");
			Map<Annotation, Collection<Token>> tokenNeIndex = indexCovered(mergedCas, Annotation.class, Token.class);
			
			getLogger().debug("Populating hierarchy");
			for (Annotation namedEntity : namedEntities) {
				int index = presentNamedEntityTypes.indexOf(namedEntity.getType().getShortName());
				if (index < 0)
					continue;
				for (Token coveredToken : tokenNeIndex.get(namedEntity)) {
					hierachialTokenNamedEntityMap.get(coveredToken).set(index, getConllFeatures(namedEntity, coveredToken));
				}
			}
		} catch (UIMAException e) {
			e.printStackTrace();
		}
	}
	
	@NotNull
	ArrayList<IConllFeatures> getEmptyConllFeatureList() {
		ArrayList<IConllFeatures> iConllFeatures = new ArrayList<>(presentNamedEntityTypes.size());
		for (int i = 0; i < presentNamedEntityTypes.size(); i++) {
			iConllFeatures.add(getEmptyConllFeatures());
		}
		return iConllFeatures;
	}
	
	public ArrayList<String> getFeaturesForNColumns(Token token, Strategy strategy, int nColumns) {
		ArrayList<String> retList = new ArrayList<>();
		for (int i = 0; i < presentNamedEntityTypes.size(); i++) {
			ArrayList<IConllFeatures> iConllFeatures = this.hierachialTokenNamedEntityMap.get(token);
			IConllFeatures conllFeatures = iConllFeatures.get(i);
			retList.addAll(conllFeatures.build());
		}
		return retList;
	}
	
	public int getNamedEntitiyCount() {
		return Objects.isNull(namedEntities) ? 0 : namedEntities.size();
	}
	
	public ArrayList<String> getNamedEntityTypes() {
		return presentNamedEntityTypes;
	}
}

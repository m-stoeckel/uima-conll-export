package BIOfid.BioEncoder;

import BIOfid.ConllFeature.ConllFeatures;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import de.tudarmstadt.ukp.dkpro.core.api.metadata.type.DocumentMetaData;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token;
import org.apache.uima.UIMAException;
import org.apache.uima.cas.CASException;
import org.apache.uima.fit.factory.JCasFactory;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.TOP;
import org.apache.uima.jcas.tcas.Annotation;
import org.apache.uima.util.CasCopier;
import org.texttechnologielab.annotation.type.Fingerprint;
import org.texttechnologylab.annotation.AbstractNamedEntity;
import org.texttechnologylab.annotation.NamedEntity;
import org.texttechnologylab.annotation.type.Other;
import org.texttechnologylab.annotation.type.Taxon;

import java.util.*;
import java.util.stream.Collectors;

import static org.apache.uima.fit.util.JCasUtil.indexCovered;
import static org.apache.uima.fit.util.JCasUtil.select;

public class TTLabHierarchicalBioEncoder extends GenericBioEncoder<Annotation> {
	
	/**
	 * DKProHierarchicalBioEncoder that filters for fingerprinted annotations and includes all {@link Taxon} annotations by default
	 * <p>See {@link TTLabHierarchicalBioEncoder#TTLabHierarchicalBioEncoder(JCas, boolean, ArrayList, ImmutableSet, boolean)}.
	 *
	 * @param jCas The JCas to process.
	 */
	public TTLabHierarchicalBioEncoder(JCas jCas) {
		this(jCas, true, Lists.newArrayList(NamedEntity.class, AbstractNamedEntity.class), ImmutableSet.of(), false);
	}
	
	/**
	 * DKProHierarchicalBioEncoder that includes all {@link Taxon} annotations by default
	 * <p>See {@link TTLabHierarchicalBioEncoder#TTLabHierarchicalBioEncoder(JCas, boolean, ArrayList, ImmutableSet, boolean)}.
	 *
	 * @param jCas                 The JCas to process.
	 * @param pFilterFingerprinted If true, only fingerprinted {@link NamedEntity NamedEntities} are processed.
	 * @param annotatorSet
	 * @param annotatorRelation
	 */
	public TTLabHierarchicalBioEncoder(JCas jCas, boolean pFilterFingerprinted, ImmutableSet<String> annotatorSet, boolean annotatorRelation) {
		this(jCas, pFilterFingerprinted, Lists.newArrayList(NamedEntity.class, AbstractNamedEntity.class), annotatorSet, annotatorRelation);
	}
	
	/**
	 * An encoder for the BIO-/IOB2-format that can handle an arbitrary number of stacked annotations.
	 *
	 * @param jCas                 The JCas to process.
	 * @param pFilterFingerprinted If true, only fingerprinted {@link NamedEntity NamedEntities} are processed.
	 * @param includeAnnotations   Include all annotations of these classes.
	 * @param annotatorSet
	 * @param annotatorRelation
	 */
	public TTLabHierarchicalBioEncoder(JCas jCas, boolean pFilterFingerprinted, ArrayList<Class<? extends Annotation>> includeAnnotations, ImmutableSet<String> annotatorSet, boolean annotatorRelation) {
		super(jCas, pFilterFingerprinted, includeAnnotations, annotatorSet, annotatorRelation);
		this.type = Annotation.class;
		this.build();
	}
	
	/**
	 * Builds the encoders indexes. Called by constructor.
	 */
	public void build() {
		try {
			if (jCas.getDocumentText() == null)
				return;
			
			mergedCas = JCasFactory.createJCas();
			mergeViews();
			
			final LinkedHashSet<Annotation> namedEntities = new LinkedHashSet<>();
			namedEntities.addAll(select(mergedCas, NamedEntity.class));
			namedEntities.addAll(select(mergedCas, AbstractNamedEntity.class));
			
			// Flatten the new view by removing identical duplicates
			final LinkedHashSet<Annotation> flattenedNamedEntities = new LinkedHashSet<>(namedEntities);
			for (Annotation parentNamedEntity : namedEntities) {
				JCasUtil.subiterate(mergedCas, type, parentNamedEntity, false, true)
						.forEach(childNamedEntity -> {
							if (flattenedNamedEntities.contains(childNamedEntity)
									&& parentNamedEntity.getBegin() == childNamedEntity.getBegin()
									&& parentNamedEntity.getEnd() == childNamedEntity.getEnd()
									&& parentNamedEntity.getType() == childNamedEntity.getType())
								flattenedNamedEntities.remove(childNamedEntity);
						});
			}
			
			// Initialize the hierarchy
			flattenedNamedEntities.forEach(key -> namedEntityHierachy.put(key, 0L));
			
			// Iterate over all NEs that are being covered by another NE
			// and set their hierarchy level to their parents level + 1
			for (Annotation parentNamedEntity : flattenedNamedEntities) {
				JCasUtil.subiterate(mergedCas, NamedEntity.class, parentNamedEntity, true, false)
						.forEach(childNamedEntity -> {
							if (flattenedNamedEntities.contains(childNamedEntity))
								namedEntityHierachy.put(childNamedEntity, namedEntityHierachy.get(parentNamedEntity) + 1);
						});
				JCasUtil.subiterate(mergedCas, AbstractNamedEntity.class, parentNamedEntity, true, false)
						.forEach(childNamedEntity -> {
							if (flattenedNamedEntities.contains(childNamedEntity))
								namedEntityHierachy.put(childNamedEntity, namedEntityHierachy.get(parentNamedEntity) + 1);
						});
			}
			
			// Put all NEs into a Map<Integer, TreeSet> by their rank, with all sets ordered by the begin of the entities
			namedEntityHierachy.forEach((ne, rank) -> {
				TreeSet<Annotation> orderedTreeSetOfRank = namedEntityByRank.getOrDefault(rank, new TreeSet<>(beginComparator));
				orderedTreeSetOfRank.add(ne);
				namedEntityByRank.put(rank, orderedTreeSetOfRank);
			});
			
			// Create an empty list for all layers of NEs for each Token
			ArrayList<Token> tokens = new ArrayList<>(select(mergedCas, Token.class));
			for (int i = 0; i < tokens.size(); i++) {
				Token token = tokens.get(i);
				tokenIndexMap.put(i, token);
				hierachialTokenNamedEntityMap.put(token, new ArrayList<>());
			}
			
			if (namedEntityByRank.values().size() != 0) {
				// TODO: parametrize the approach selection
				breadthFirstSearch(mergedCas, tokens);
			} else {
				for (Token token : tokens) {
					ArrayList<ConllFeatures> namedEntityStringTreeMap = hierachialTokenNamedEntityMap.get(token);
					namedEntityStringTreeMap.add(new ConllFeatures());
				}
			}
			
			createMaxCoverageLookup();
		} catch (UIMAException e) {
			e.printStackTrace();
		}
	}
	
	@Override
	void mergeViews() throws CASException {
		CasCopier.copyCas(jCas.getCas(), mergedCas.getCas(), true, true);
		jCas.removeAllIncludingSubtypes(NamedEntity.type);
		jCas.removeAllIncludingSubtypes(AbstractNamedEntity.type);
		
		DocumentMetaData oDocumentMetaData = DocumentMetaData.get(jCas);
		DocumentMetaData nDocumentMetaData = DocumentMetaData.create(mergedCas);
		nDocumentMetaData.setDocumentId(oDocumentMetaData.getDocumentId());
		nDocumentMetaData.setDocumentUri(oDocumentMetaData.getDocumentUri());
		nDocumentMetaData.setDocumentBaseUri(oDocumentMetaData.getDocumentBaseUri());
		nDocumentMetaData.setDocumentTitle(oDocumentMetaData.getDocumentTitle());
		nDocumentMetaData.setCollectionId(oDocumentMetaData.getCollectionId());
		nDocumentMetaData.setIsLastSegment(oDocumentMetaData.getIsLastSegment());
		
		jCas.getViewIterator().forEachRemaining(viewCas -> {
			if (annotatorRelation == annotatorSet.contains(viewCas.getViewName())) {
				// Get all fingerprinted TOPs
				HashSet<TOP> fingerprinted = select(viewCas, Fingerprint.class).stream()
						.map(Fingerprint::getReference)
						.collect(Collectors.toCollection(HashSet::new));
				
				for (NamedEntity oNamedEntity : select(viewCas, NamedEntity.class)) {
					if (!fingerprinted.contains(oNamedEntity)) continue;
					
					NamedEntity nNamedEntity = (NamedEntity) mergedCas.getCas().createAnnotation(oNamedEntity.getType(), oNamedEntity.getBegin(), oNamedEntity.getEnd());
					nNamedEntity.setValue(oNamedEntity.getValue());
					nNamedEntity.setMetaphor(oNamedEntity.getMetaphor());
					nNamedEntity.setMetonym(oNamedEntity.getMetonym());
					
					nNamedEntity.addToIndexes();
				}
				
				for (AbstractNamedEntity oNamedEntity : select(viewCas, AbstractNamedEntity.class)) {
					if (!fingerprinted.contains(oNamedEntity)) continue;
					
					AbstractNamedEntity nNamedEntity = (AbstractNamedEntity) mergedCas.getCas().createAnnotation(oNamedEntity.getType(), oNamedEntity.getBegin(), oNamedEntity.getEnd());
					nNamedEntity.setValue(oNamedEntity.getValue());
					nNamedEntity.setMetaphor(oNamedEntity.getMetaphor());
					nNamedEntity.setSpecific(oNamedEntity.getSpecific());
					nNamedEntity.setMetonym(oNamedEntity.getMetonym());
					
					nNamedEntity.addToIndexes();
				}
			}
		});
	}
	
	
	/**
	 * Create a NE hierarchy by breadth-first search.
	 * <p>
	 * Given a list of tokens and the rank of each Named Entity, iterate over all NEs by rank, sorted by their begin.
	 * This sorts top level annotations first, as longer annotations precede others in the iteration order returned by
	 * {@link JCasUtil#select(JCas, Class)}.
	 * </p><p>
	 * For each rank, get all token covered by a NE and add the BIO code to the tokens hierarchy in the
	 * {@link DKProHierarchicalBioEncoder#hierachialTokenNamedEntityMap}. After each iteration, check all <i>higher</i> ranks
	 * for annotations, that cover annotations which are still unvisited in at this rank.
	 * At the end of each iteration over a rank, add an "O" to all not covered tokens.
	 * </p><p>
	 * This approach <b>will</b> "fill" holes created by three or more annotations overlapping, ie. given:
	 * <pre>
	 * Token:       t1  t2  t3
	 * Entities:    A   AB  BC</pre>
	 * The corresponding ranks will be:
	 * <pre>
	 * Token:       t1  t2  t3
	 * Rank 1:      A   A
	 * Rank 2:          B   B
	 * Rank 3:              C</pre>
	 * The resulting hierarchy will be:
	 * <pre>
	 * Token:       t1  t2  t3
	 * Level 1:     A   A   C
	 * Level 2:     B   B   O</pre>
	 * </p>
	 *
	 * @param jCas   The JCas containing the annotations.
	 * @param tokens A list of token to be considered.
	 * @see DKProHierarchicalBioEncoder#naiveStackingApproach(JCas, ArrayList) naiveStackingApproach(JCas, ArrayList)
	 */
	public void breadthFirstSearch(JCas jCas, ArrayList<Token> tokens) {
		Map<Annotation, Collection<Token>> tokenNeIndex = indexCovered(jCas, Annotation.class, Token.class);
		LinkedHashSet<Annotation> visitedEntities = new LinkedHashSet<>();
		ArrayList<TreeSet<Annotation>> rankSets = Lists.newArrayList(namedEntityByRank.values());
		for (int i = 0; i < rankSets.size(); i++) {
			TreeSet<Annotation> rankSet = rankSets.get(i);
			rankSet.removeAll(visitedEntities);
			
			// A set to collect all tokens, that have been covered by an annotation
			HashSet<Token> visitedTokens = new HashSet<>();
			
			for (Annotation namedEntity : rankSet) {
				// Get all tokens covered by this NE
				Collection<Token> coveredTokens = tokenNeIndex.get(namedEntity);
				// If its not already covered, add this Named Entity to the tokens NE hierarchy
				for (Token coveredToken : coveredTokens) {
					ArrayList<ConllFeatures> namedEntityStringTreeMap = hierachialTokenNamedEntityMap.get(coveredToken);
					namedEntityStringTreeMap.add(getFeatures(namedEntity, coveredToken));
				}
				visitedTokens.addAll(coveredTokens);
				visitedEntities.add(namedEntity);
			}
			
			// Run breadth-first search over all higher ranks for all remaining token
			for (int j = i + 1; j < rankSets.size(); j++) {
				TreeSet<Annotation> rankSetBDSearch = rankSets.get(j);
				rankSet.removeAll(visitedEntities);
				for (Annotation namedEntity : rankSetBDSearch) {
					// Get all tokens covered by this NE
					ArrayList<Token> coveredTokens = new ArrayList<>(tokenNeIndex.get(namedEntity));
					// Check if any covered token is already covered by another NE annotation
					if (!coveredTokens.isEmpty() && coveredTokens.stream().anyMatch(visitedTokens::contains))
						continue;
					// If its not already covered, add this Named Entity to the tokens NE hierarchy
					for (Token coveredToken : coveredTokens) {
						ArrayList<ConllFeatures> namedEntityStringTreeMap = hierachialTokenNamedEntityMap.get(coveredToken);
						namedEntityStringTreeMap.add(getFeatures(namedEntity, coveredToken));
					}
					visitedTokens.addAll(coveredTokens);
					visitedEntities.add(namedEntity);
				}
			}
			
			// Iterate over all tokens, that have not been covered in this iteration
			// and fill their hierarchy with an "O". This levels all
			ArrayList<Token> notCoveredTokens = new ArrayList<>(tokens);
			notCoveredTokens.removeAll(visitedTokens);
			for (Token notCoveredToken : notCoveredTokens) {
				ArrayList<ConllFeatures> namedEntityStringTreeMap = hierachialTokenNamedEntityMap.get(notCoveredToken);
				namedEntityStringTreeMap.add(new ConllFeatures());
			}
		}
		
		int lastIndex = rankSets.size() - 1;
		if (lastIndex >= 0 && hierachialTokenNamedEntityMap.values().stream().allMatch(l -> l.get(lastIndex).equals(new ConllFeatures())))
			hierachialTokenNamedEntityMap.values().forEach(l -> l.remove(lastIndex));
	}
	
	/**
	 * Return the BIO-code of the given annotation over the given as a string.
	 *
	 * @param namedEntity
	 * @param token
	 * @return BIO-code of the annotation over the token as string.
	 */
	public ConllFeatures getFeatures(Annotation namedEntity, Token token) {
		ConllFeatures features = new ConllFeatures();
		if (namedEntity instanceof org.texttechnologylab.annotation.AbstractNamedEntity) {
			features.name(namedEntity.getType().getShortName());
			
			AbstractNamedEntity ne = (AbstractNamedEntity) namedEntity;
			features.setAbstract(true);
			features.setMetaphor(ne.getMetaphor());
		} else if (namedEntity instanceof org.texttechnologylab.annotation.type.Other) {
			features.name(namedEntity.getType().getShortName());
			
			Other ne = (Other) namedEntity;
			features.setAbstract(ne.getValue() != null && !ne.getValue().isEmpty());
			features.setMetaphor(ne.getMetaphor());
		} else if (namedEntity instanceof org.texttechnologylab.annotation.NamedEntity) {
			features.name(namedEntity.getType().getShortName());
			
			NamedEntity ne = (NamedEntity) namedEntity;
			features.setAbstract(false);
			features.setMetaphor(ne.getMetaphor());
		} else if (namedEntity instanceof org.texttechnologielab.annotation.type.TexttechnologyNamedEntity) {
			features.name(((org.texttechnologielab.annotation.type.TexttechnologyNamedEntity) namedEntity).getValue());
		} else {
			features.name("<UNK>");
		}
		if (features.isNameInvalid())
			return new ConllFeatures("O");
		if (namedEntity.getBegin() == token.getBegin() == useIOB2) {
			features.prependTag("B-");
		} else {
			features.prependTag("I-");
		}
		return features;
	}
	
}

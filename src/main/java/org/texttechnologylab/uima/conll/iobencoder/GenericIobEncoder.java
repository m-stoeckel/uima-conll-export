package org.texttechnologylab.uima.conll.iobencoder;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token;
import org.apache.commons.lang3.StringUtils;
import org.apache.uima.UIMAException;
import org.apache.uima.cas.CASException;
import org.apache.uima.cas.text.AnnotationFS;
import org.apache.uima.fit.factory.JCasFactory;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.TOP;
import org.apache.uima.jcas.tcas.Annotation;
import org.apache.uima.util.CasCopier;
import org.jetbrains.annotations.NotNull;
import org.texttechnologylab.annotation.type.Fingerprint;
import org.texttechnologylab.annotation.type.TexttechnologyNamedEntity;
import org.texttechnologylab.uima.conll.extractor.SingleConllFeatures;
import org.texttechnologylab.uima.conll.extractor.IConllFeatures;
import org.texttechnologylab.utilities.collections.CountMap;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.apache.uima.fit.util.JCasUtil.*;

public abstract class GenericIobEncoder<T extends Annotation> {
	final HashMap<Token, ArrayList<IConllFeatures>> hierachialTokenNamedEntityMap;
	final CountMap<T> namedEntityHierachy;
	final JCas jCas;
	final ArrayList<Class<? extends Annotation>> forceAnnotations;
	final TreeMap<Integer, Token> tokenIndexMap;
	
	protected JCas mergedCas;
	
	/**
	 * The base type of {@link Annotation Annotations} to iterate over.
	 */
	Class<T> type;
	
	/**
	 * Set false to use IOB-1 format.
	 */
	boolean useIOB2 = true; // FIXME
	
	/**
	 * If true, filter all annotations by those covered by a {@link Fingerprint} annotation. Default: true.
	 */
	boolean filterFingerprinted = true;
	
	/**
	 * If true, remove overlapping duplicate entries from multiple views of the same type. Default: true.
	 */
	boolean removeDuplicateSameType = true;
	
	/**
	 * If true, remove overlapping duplicate entries from multiple views of the same type <b>only</b> if they start on
	 * the same token. Default: false.
	 * <p>
	 * Example:<table>
	 * <tr><td>A1</td><td>A2</td><td>M(true)</td><td>M(false)</td></tr>
	 * <tr><td>B-X</td><td>O</td><td>B-X,O</td><td>B-X</td></tr>
	 * <tr><td>I-X</td><td>B-X</td><td>B-X,B-X</td><td>I-X</td></tr>
	 * <tr><td>B-Y</td><td>O</td><td>B-Y,O</td><td>B-Y</td></tr>
	 * <tr><td>I-Y</td><td>B-Y</td><td>I-Y</td><td>I-Y</td></tr>
	 * </table>
	 */
	boolean removeDuplicateConstraintBegin = false;
	
	/**
	 * If true, remove overlapping duplicate entries from multiple views of the same type <b>only</b> if they end on the
	 * same token. Default: false
	 * <p>
	 * Example:<table>
	 * <tr><td>A1</td><td>A2</td><td>M(true)</td><td>M(false)</td></tr>
	 * <tr><td>B-X</td><td>B-X</td><td>B-X,B-X</td><td>B-X</td></tr>
	 * <tr><td>I-X</td><td>O</td><td>B-X,O</td><td>I-X</td></tr>
	 * </table>
	 */
	boolean removeDuplicateConstraintEnd = false;
	
	/**
	 * The annotator relation can either be to {@link #BLACKLIST} or to {@link #WHITELIST} the anntotators in the {@link
	 * #annotatorSet}. Default: {@link #BLACKLIST}
	 */
	boolean annotatorRelation = BLACKLIST;
	public static boolean WHITELIST = true;
	public static boolean BLACKLIST = false;
	
	TreeMap<Long, TreeSet<T>> namedEntityByRank;
	ArrayList<Integer> maxCoverageOrder;
	
	public LinkedHashMap<Integer, Long> coverageCount = new LinkedHashMap<>();
	
	Comparator<Annotation> beginComparator = Comparator.comparingInt(Annotation::getBegin);
	private Comparator<Annotation> hierachialComparator = new Comparator<Annotation>() {
		@Override
		public int compare(Annotation o1, Annotation o2) {
			int cmp = Long.compare(namedEntityHierachy.get(o1), namedEntityHierachy.get(o2));
			cmp = cmp != 0 ? cmp : Integer.compare(o1.getBegin(), o2.getBegin());
			return cmp;
		}
	};
	final ImmutableSet<String> annotatorSet;
	
	
	protected GenericIobEncoder(JCas jCas, ImmutableSet<String> annotatorSet) {
		this(jCas, new ArrayList<>(), annotatorSet);
	}
	
	GenericIobEncoder(JCas jCas, ArrayList<Class<? extends Annotation>> forceAnnotations, ImmutableSet<String> annotatorSet) {
		this.jCas = jCas;
		this.forceAnnotations = forceAnnotations;
		this.annotatorSet = annotatorSet;
		
		this.hierachialTokenNamedEntityMap = new HashMap<>();
		this.namedEntityHierachy = new CountMap<>();
		this.namedEntityByRank = new TreeMap<>();
		this.tokenIndexMap = new TreeMap<>();
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
			
			final LinkedHashSet<T> namedEntities = new LinkedHashSet<>(select(mergedCas, this.type));
			
			// Flatten the new view by removing identical duplicates
			if (removeDuplicateSameType) {
				removeDuplicates(namedEntities);
			}
			// Initialize the hierarchy
			namedEntities.forEach(key -> namedEntityHierachy.put(key, 0L));
			
			// Iterate over all NEs that are being covered by another NE
			// and set their hierarchy level to their parents level + 1
			for (T parentNamedEntity : namedEntities) {
				JCasUtil.subiterate(mergedCas, type, parentNamedEntity, true, false)
						.forEach(childNamedEntity -> {
							if (namedEntities.contains(childNamedEntity))
								namedEntityHierachy.put(childNamedEntity, namedEntityHierachy.get(parentNamedEntity) + 1);
						});
			}
			
			// Put all NEs into a Map<Integer, TreeSet> by their rank, with all sets ordered by the begin of the entities
			namedEntityHierachy.forEach((ne, rank) -> {
				TreeSet<T> orderedTreeSetOfRank = namedEntityByRank.getOrDefault(rank, new TreeSet<>(beginComparator));
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
					ArrayList<IConllFeatures> namedEntityStringTreeMap = hierachialTokenNamedEntityMap.get(token);
					namedEntityStringTreeMap.add(getEmptyConllFeatures());
				}
			}
			
			createMaxCoverageLookup();
		} catch (CASException e) {
			e.printStackTrace();
		} catch (UIMAException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Remove all duplicate, overlapping annotations subclassing {@link #type} using {@link
	 * JCasUtil#subiterate(JCas, Class, AnnotationFS, boolean, boolean)}. Will only remove shorter or equal length
	 * child annotations for any given parent annotation.
	 *
	 * @param namedEntities The set of entities to remove all duplicates from.
	 */
	protected void removeDuplicates(LinkedHashSet<T> namedEntities) {
		LinkedHashSet<T> iterNamedEntities = (LinkedHashSet<T>) namedEntities.clone();
		for (T parentNamedEntity : iterNamedEntities) {
			JCasUtil.subiterate(mergedCas, type, parentNamedEntity, true, true)
					.forEach(childNamedEntity -> {
						if (namedEntities.contains(childNamedEntity)
								&& parentNamedEntity.getType().getShortName().equals(childNamedEntity.getType().getShortName())
								&& (!removeDuplicateConstraintBegin || parentNamedEntity.getBegin() == childNamedEntity.getBegin())
								&& (!removeDuplicateConstraintEnd || parentNamedEntity.getEnd() == childNamedEntity.getEnd())
						)
							namedEntities.remove(childNamedEntity);
					});
		}
	}
	
	void mergeViews() throws CASException {
		CasCopier.copyCas(jCas.getCas(), mergedCas.getCas(), true, true);
		
		jCas.getViewIterator().forEachRemaining(viewCas -> {
			String viewName = StringUtils.substringAfterLast(viewCas.getViewName().trim(), "/");
			if (annotatorRelation == annotatorSet.contains(viewName)) {
				// Get all fingerprinted TOPs
				HashSet<TOP> fingerprinted = select(viewCas, Fingerprint.class).stream()
						.map(Fingerprint::getReference)
						.collect(Collectors.toCollection(HashSet::new));
				
				for (T oAnnotation : select(viewCas, type)) {
					if (!fingerprinted.contains(oAnnotation)) continue;
					
					Annotation nAnnotation = (Annotation) mergedCas.getCas().createAnnotation(oAnnotation.getType(), oAnnotation.getBegin(), oAnnotation.getEnd());
					nAnnotation.addToIndexes();
				}
				// FIXME: Features such as value or identifiers are not copied in this generic version!
			}
		});
		
		// Remove all sub-tokens, ie. the halves of a tokens that were split by a hyphen.
		ArrayList<Token> subTokens = new ArrayList<>();
		JCasUtil.select(mergedCas, Token.class).forEach(
				token -> JCasUtil.subiterate(mergedCas, Token.class, token, true, true).forEach(
						subTokens::add
				)
		);
		subTokens.forEach(mergedCas::removeFsFromIndexes);
	}
	
	
	/**
	 * Create a NE hierarchy by breadth-first search.
	 * <p>
	 * Given a list of tokens and the rank of each Named Entity, iterate over all NEs by rank, sorted by their begin.
	 * This sorts top level annotations first, as longer annotations precede others in the iteration order returned by
	 * {@link JCasUtil#select(JCas, Class)}.
	 * </p><p>
	 * For each rank, get all token covered by a NE and add the BIO code to the tokens hierarchy in the {@link
	 * DKProHierarchicalIobEncoder#hierachialTokenNamedEntityMap}. After each iteration, check all <i>higher</i> ranks
	 * for annotations, that cover annotations which are still unvisited in at this rank. At the end of each iteration
	 * over a rank, add an "O" to all not covered tokens.
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
	 * @see DKProHierarchicalIobEncoder#naiveStackingApproach(JCas, ArrayList) naiveStackingApproach(JCas, ArrayList)
	 */
	public void breadthFirstSearch(JCas jCas, ArrayList<Token> tokens) {
		Map<T, Collection<Token>> tokenNeIndex = indexCovered(jCas, type, Token.class);
		LinkedHashSet<T> visitedEntities = new LinkedHashSet<>();
		ArrayList<TreeSet<T>> rankSets = Lists.newArrayList(namedEntityByRank.values());
		for (int i = 0; i < rankSets.size(); i++) {
			TreeSet<T> rankSet = rankSets.get(i);
			rankSet.removeAll(visitedEntities);
			
			// A set to collect all tokens, that have been covered by an annotation
			HashSet<Token> visitedTokens = new HashSet<>();
			
			for (T namedEntity : rankSet) {
				// Get all tokens covered by this NE
				Collection<Token> coveredTokens = tokenNeIndex.get(namedEntity);
				// If its not already covered, add this Named Entity to the tokens NE hierarchy
				for (Token coveredToken : coveredTokens) {
					ArrayList<IConllFeatures> namedEntityStringTreeMap = hierachialTokenNamedEntityMap.get(coveredToken);
					namedEntityStringTreeMap.add(getConllFeatures(namedEntity, coveredToken));
				}
				visitedTokens.addAll(coveredTokens);
				visitedEntities.add(namedEntity);
			}
			
			// Run breadth-first search over all higher ranks for all remaining token
			for (int j = i + 1; j < rankSets.size(); j++) {
				TreeSet<T> rankSetBDSearch = rankSets.get(j);
				rankSet.removeAll(visitedEntities);
				for (T namedEntity : rankSetBDSearch) {
					// Get all tokens covered by this NE
					ArrayList<Token> coveredTokens = new ArrayList<>(tokenNeIndex.get(namedEntity));
					// Check if any covered token is already covered by another NE annotation
					if (!coveredTokens.isEmpty() && coveredTokens.stream().anyMatch(visitedTokens::contains))
						continue;
					// If its not already covered, add this Named Entity to the tokens NE hierarchy
					for (Token coveredToken : coveredTokens) {
						ArrayList<IConllFeatures> namedEntityStringTreeMap = hierachialTokenNamedEntityMap.get(coveredToken);
						namedEntityStringTreeMap.add(getConllFeatures(namedEntity, coveredToken));
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
				ArrayList<IConllFeatures> namedEntityStringTreeMap = hierachialTokenNamedEntityMap.get(notCoveredToken);
				namedEntityStringTreeMap.add(getEmptyConllFeatures());
			}
		}
		
		// Check if the last level of the hierarchy is empty and can be removed
		// TODO: Can other levels of the hierarchy be empty too and should be removed, too?
		int lastIndex = rankSets.size() - 1;
		if (lastIndex >= 0 && hierachialTokenNamedEntityMap.values().stream().allMatch(l -> l.get(lastIndex).equals(getEmptyConllFeatures())))
			hierachialTokenNamedEntityMap.values().forEach(l -> l.remove(lastIndex));
	}
	
	/**
	 * Compute the coverage for each hierarchy level and list the level indices sorted by their respective coverage.
	 */
	public void createMaxCoverageLookup() {
		Optional<ArrayList<IConllFeatures>> optionalArrayList = hierachialTokenNamedEntityMap.values().stream().findAny();
		maxCoverageOrder = new ArrayList<>();
		if (optionalArrayList.isPresent()) {
			int size = optionalArrayList.get().size();
			coverageCount = IntStream.range(0, size).boxed()
					.collect(Collectors.toMap(
							Function.identity(),
							i -> hierachialTokenNamedEntityMap.values().stream()
									.filter(l -> !l.get(i).isOut())
									.count(),
							(u, v) -> u,
							LinkedHashMap::new));
			maxCoverageOrder.addAll(
					coverageCount.entrySet().stream().sequential()
							.sorted(Comparator.comparingLong(Map.Entry::getValue))
							.mapToInt(Map.Entry::getKey).boxed()
							.collect(Collectors.toList()));
			Collections.reverse(maxCoverageOrder);
		} else {
			maxCoverageOrder.add(0);
		}
	}
	
	/**
	 * Create a naive NE hierarchy by stacking NE annotations over tokens on top of each other.
	 * <p>
	 * Given a list of tokens and the rank of each Named Entity, iterate over all NEs by rank, sorted by their begin.
	 * This sorts top level annotations first, as longer annotations precede others in the iteration order returned by
	 * {@link JCasUtil#select(JCas, Class)}.
	 * </p><p>
	 * For each rank, get all token covered by a NE and add the BIO code to the tokens hierarchy in the {@link
	 * DKProHierarchicalIobEncoder#hierachialTokenNamedEntityMap}. At the end of each iteration over a rank, add an "O"
	 * to all not covered tokens.
	 * </p><p>
	 * This approach will <b>not</b> "fill" holes created by three or more annotations overlapping, ie. given:
	 * <pre>
	 * Token:       t1  t2  t3
	 * Entities:    A   AB  BC</pre>
	 * The corresponding ranks and the resulting hierarchy will be:
	 * <pre>
	 * Token:       t1  t2  t3
	 * Rank/Lvl 1:  A   A   O
	 * Rank/Lvl 2:  O   B   B
	 * Rank/Lvl 3:  O   O   C</pre>
	 * </p>
	 *
	 * @param jCas   The JCas containing the annotations.
	 * @param tokens A list of token to be considered.
	 * @see DKProHierarchicalIobEncoder#breadthFirstSearch(JCas, ArrayList) breadthFirstSearch(JCas, ArrayList)
	 */
	public void naiveStackingApproach(JCas jCas, ArrayList<Token> tokens) {
		Map<T, Collection<Token>> tokenNeIndex = indexCovered(jCas, this.type, Token.class);
		for (TreeSet<T> rankSet : namedEntityByRank.values()) {
			// A set to collect all tokens, that have been covered by an annotation
			HashSet<Token> rankCoveredTokens = new HashSet<>();
			
			for (T namedEntity : rankSet) {
				// Get all tokens covered by this NE
				Collection<Token> coveredTokens = tokenNeIndex.get(namedEntity);
				// Add this Named Entity to the tokens NE hierarchy
				for (Token coveredToken : coveredTokens) {
					ArrayList<IConllFeatures> namedEntityStringTreeMap = hierachialTokenNamedEntityMap.get(coveredToken);
					namedEntityStringTreeMap.add(getConllFeatures(namedEntity, coveredToken));
				}
				rankCoveredTokens.addAll(coveredTokens);
			}
			
			// Iterate over all tokens, that have not been covered in this iteration
			// and fill their hierarchy with an "O". This levels all
			ArrayList<Token> notCoveredTokens = new ArrayList<>(tokens);
			notCoveredTokens.removeAll(rankCoveredTokens);
			for (Token notCoveredToken : notCoveredTokens) {
				ArrayList<IConllFeatures> namedEntityStringTreeMap = hierachialTokenNamedEntityMap.get(notCoveredToken);
				namedEntityStringTreeMap.add(getEmptyConllFeatures());
			}
		}
	}
	
	@Deprecated
	public void tokenInConflictApproach(JCas jCas, ArrayList<Token> tokens) {
		HashMap<Token, TreeSet<T>> tokenNeMap = new HashMap<>();
		tokens.forEach(token -> tokenNeMap.put(token, new TreeSet<>(hierachialComparator)));
		
		Map<T, Collection<Token>> tokenNeIndex = indexCovered(jCas, type, Token.class);
		tokenNeIndex.forEach((ne, tks) -> tks.forEach(tk -> tokenNeMap.get(tk).add(ne)));
		
		HashSet<T> usedEntities = new HashSet<>();
		
		Token curr_token = tokens.get(0);
		while (true) {
			TreeSet<T> treeSet = tokenNeMap.get(curr_token);
			treeSet.removeAll(usedEntities);
			if (treeSet.isEmpty()) {
				ArrayList<IConllFeatures> namedEntityStringTreeMap = hierachialTokenNamedEntityMap.get(curr_token);
				namedEntityStringTreeMap.add(getEmptyConllFeatures());
			} else {
				for (T namedEntity : treeSet) {
					if (usedEntities.contains(namedEntity)) continue;
					else usedEntities.add(namedEntity);
					for (Token coveredToken : tokenNeIndex.get(namedEntity)) { // FIXME: greift zurück, soll aber einen Konflikt finden!
						curr_token = coveredToken;
						ArrayList<IConllFeatures> namedEntityStringTreeMap = hierachialTokenNamedEntityMap.get(curr_token);
						namedEntityStringTreeMap.add(getConllFeatures(namedEntity, curr_token));
					}
					
					break;
				}
			}
			if (tokens.indexOf(curr_token) == tokens.size() - 1) break;
			curr_token = selectSingleRelative(jCas, Token.class, curr_token, 1);
		}
	}
	
	/**
	 * TODO: Comment
	 *
	 * @param namedEntity
	 * @param token
	 * @return
	 */
	public IConllFeatures getConllFeatures(T namedEntity, Token token) {
		IConllFeatures features = getEmptyConllFeatures();
		if (namedEntity instanceof org.texttechnologylab.annotation.AbstractNamedEntity) {
			features.name(namedEntity.getType().getShortName());
		} else if (namedEntity instanceof org.texttechnologylab.annotation.type.Other) {
			features.name(namedEntity.getType().getShortName());
		} else if (namedEntity instanceof org.texttechnologylab.annotation.NamedEntity) {
			features.name(namedEntity.getType().getShortName());
		} else if (namedEntity instanceof de.tudarmstadt.ukp.dkpro.core.api.ner.type.NamedEntity) {
			String value = ((de.tudarmstadt.ukp.dkpro.core.api.ner.type.NamedEntity) namedEntity).getValue();
			if (value == null) {
				features.name(namedEntity.getType().getShortName().substring(0, 3).toUpperCase()); // FIXME
			} else {
				features.name(value);
			}
		} else if (namedEntity instanceof TexttechnologyNamedEntity) {
			features.name(((TexttechnologyNamedEntity) namedEntity).getValue());
		} else {
			features.name("<UNK>");
		}
		if (features.isNameInvalid())
			return new SingleConllFeatures("O");
		if (namedEntity.getBegin() == token.getBegin() == useIOB2) {
			features.prependTag("B-");
		} else {
			features.prependTag("I-");
		}
		return features;
	}
	
	public ArrayList<String> getFeatures(Token token) {
		return getFeatures(token, Strategy.MaxCoverage);
	}
	
	public ArrayList<String> getFeatures(int index, int strategyIndex) {
		return getFeatures(tokenIndexMap.get(index), Strategy.byIndex(strategyIndex));
	}
	
	public ArrayList<String> getFeatures(Token token, int strategyIndex) {
		return getFeatures(token, Strategy.byIndex(strategyIndex));
	}
	
	public ArrayList<String> getFeatures(Token token, Strategy strategy) {
		return getFeaturesForNColumns(token, strategy, 1);
	}
	
	public ArrayList<String> getFeaturesForNColumns(Token token, int strategyIndex, int nColumns) {
		return getFeaturesForNColumns(token, Strategy.byIndex(strategyIndex), nColumns);
	}
	
	public ArrayList<String> getFeaturesForNColumns(Token token, Strategy strategy, int nColumns) {
		ArrayList<String> retList = new ArrayList<>();
		
		ArrayList<IConllFeatures> IConllFeatures = hierachialTokenNamedEntityMap.get(token);
		if (IConllFeatures == null) return retList;
		for (int i = 0; i < Math.min(nColumns, IConllFeatures.size()); i++) {
			try {
				switch (strategy) {
					case TopFirstBottomUp:
						strategy = Strategy.BottomUp;
					case TopDown:
						retList.addAll(IConllFeatures.get(i).build());
						break;
					case BottomUp:
						retList.addAll(Lists.reverse(IConllFeatures).get(i).build());
						break;
					case MaxCoverage:
						if (i < maxCoverageOrder.size()) {
							Integer index = maxCoverageOrder.get(i);
							retList.addAll(IConllFeatures.get(index).build());
						} else {
							retList.addAll(getEmptyConllFeatures().build());
						}
				}
			} catch (NullPointerException e) {
				e.printStackTrace();
				retList.addAll(getEmptyConllFeatures().build());
			}
		}
		
		return retList;
	}
	
	@NotNull
	IConllFeatures getEmptyConllFeatures() {
		return new SingleConllFeatures();
	}
	
	public JCas getMergedCas() {
		return mergedCas;
	}
	
	public int getNamedEntitiyCount() {
		return Objects.isNull(namedEntityHierachy) ? 0 : namedEntityHierachy.size();
	}
	
	public enum Strategy {
		TopFirstBottomUp(0), TopDown(1), BottomUp(2), MaxCoverage(3);
		
		final int index;
		
		Strategy(int i) {
			index = i;
		}
		
		public static Strategy byIndex(int i) {
			for (Strategy strategy : Strategy.values()) {
				if (strategy.index == i) {
					return strategy;
				}
			}
			throw new IndexOutOfBoundsException(String.format("The strategy index %d is out of bounds!", i));
		}
	}
	
	public void setFilterFingerprinted(boolean filterFingerprinted) {
		this.filterFingerprinted = filterFingerprinted;
	}
	
	public void setRemoveDuplicateSameType(boolean removeDuplicateSameType) {
		this.removeDuplicateSameType = removeDuplicateSameType;
	}
	
	public void setRemoveDuplicateConstraintBegin(boolean removeDuplicateConstraintBegin) {
		this.removeDuplicateConstraintBegin = removeDuplicateConstraintBegin;
	}
	
	public void setRemoveDuplicateConstraintEnd(boolean removeDuplicateConstraintEnd) {
		this.removeDuplicateConstraintEnd = removeDuplicateConstraintEnd;
	}
	
	public void setAnnotatorRelation(boolean annotatorRelation) {
		this.annotatorRelation = annotatorRelation;
	}
}

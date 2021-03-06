package org.texttechnologylab.uima.conll.extractor;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterators;
import com.google.common.collect.Streams;
import de.tudarmstadt.ukp.dkpro.core.api.lexmorph.type.pos.POS;
import de.tudarmstadt.ukp.dkpro.core.api.metadata.type.DocumentMetaData;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Lemma;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Sentence;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.output.CloseShieldOutputStream;
import org.apache.commons.lang3.StringUtils;
import org.apache.uima.UIMAException;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CASException;
import org.apache.uima.cas.CASRuntimeException;
import org.apache.uima.fit.component.JCasAnnotator_ImplBase;
import org.apache.uima.fit.descriptor.ConfigurationParameter;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.DoubleArray;
import org.apache.uima.jcas.cas.StringArray;
import org.apache.uima.jcas.tcas.Annotation;
import org.apache.uima.resource.ResourceInitializationException;
import org.dkpro.core.api.parameter.ComponentParameters;
import org.texttechnologylab.iaa.AgreementContainer;
import org.texttechnologylab.uima.conll.iobencoder.DKProHierarchicalIobEncoder;
import org.texttechnologylab.uima.conll.iobencoder.GenericIobEncoder;
import org.texttechnologylab.uima.conll.iobencoder.TTLabHierarchicalIobEncoder;

import javax.annotation.Nonnull;
import javax.validation.constraints.NotNull;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

import static org.apache.uima.fit.util.JCasUtil.select;
import static org.apache.uima.fit.util.JCasUtil.selectCovered;

public class ConllBIO2003Writer extends JCasAnnotator_ImplBase {
	
	// Start of AnalysisComponent parameters
	
	/**
	 * Character encoding of the output data.
	 */
	static final String UNUSED = "_";
	public static final String PARAM_TARGET_LOCATION = ComponentParameters.PARAM_TARGET_LOCATION;
	@ConfigurationParameter(name = PARAM_TARGET_LOCATION, mandatory = true, defaultValue = ComponentParameters.PARAM_TARGET_LOCATION)
	private String targetLocation;
	
	public static final String PARAM_TARGET_ENCODING = ComponentParameters.PARAM_TARGET_ENCODING;
	@ConfigurationParameter(name = PARAM_TARGET_ENCODING, mandatory = true, defaultValue = "UTF-8")
	private String targetEncoding;
	
	public static final String PARAM_OVERWRITE = "targetOverwrite";
	@ConfigurationParameter(name = PARAM_OVERWRITE, mandatory = true, defaultValue = "true")
	private Boolean targetOverwrite;
	
	public static final String PARAM_FILENAME_EXTENSION = ComponentParameters.PARAM_FILENAME_EXTENSION;
	@ConfigurationParameter(name = PARAM_FILENAME_EXTENSION, mandatory = true, defaultValue = ".conll")
	String filenameSuffix;
	
	public static final String PARAM_WRITE_POS = ComponentParameters.PARAM_WRITE_POS;
	@ConfigurationParameter(name = PARAM_WRITE_POS, mandatory = true, defaultValue = "true")
	boolean writePos;
	
	public static final String PARAM_WRITE_CHUNK = ComponentParameters.PARAM_WRITE_CHUNK;
	@ConfigurationParameter(name = PARAM_WRITE_CHUNK, mandatory = true, defaultValue = "true")
	boolean writeChunk;
	
	public static final String PARAM_WRITE_NAMED_ENTITY = ComponentParameters.PARAM_WRITE_NAMED_ENTITY;
	@ConfigurationParameter(name = PARAM_WRITE_NAMED_ENTITY, mandatory = true, defaultValue = "true")
	boolean writeNamedEntity;
	
	/**
	 * The number of desired NE columns
	 */
	public static final String PARAM_NAMED_ENTITY_COLUMNS = "pNamedEntityColumns";
	@ConfigurationParameter(name = PARAM_NAMED_ENTITY_COLUMNS, defaultValue = "1")
	Integer pNamedEntityColumns;
	
	public static final String PARAM_CONLL_SEPARATOR = "pConllSeparator";
	@ConfigurationParameter(name = PARAM_CONLL_SEPARATOR, defaultValue = " ")
	String pConllSeparator;
	
	public static final String PARAM_STRATEGY_INDEX = "pEncoderStrategyIndex";
	@ConfigurationParameter(name = PARAM_STRATEGY_INDEX, defaultValue = "0")
	Integer pEncoderStrategyIndex;
	
	public static final String PARAM_FILTER_FINGERPRINTED = "pFilterFingerprinted";
	@ConfigurationParameter(name = PARAM_FILTER_FINGERPRINTED, defaultValue = "true")
	private Boolean pFilterFingerprinted;
	
	/**
	 * If true, the raw document text will also be exported.
	 */
	public static final String PARAM_EXPORT_RAW = "pExportRaw";
	@ConfigurationParameter(name = PARAM_EXPORT_RAW, mandatory = false, defaultValue = "false")
	private Boolean pExportRaw;
	
	/**
	 * If true, only the raw document text will be exported.
	 */
	public static final String PARAM_EXPORT_RAW_ONLY = "pExportRawOnly";
	@ConfigurationParameter(name = PARAM_EXPORT_RAW_ONLY, mandatory = false, defaultValue = "false")
	private Boolean pExportRawOnly;
	
	/**
	 * The target location for the raw document text.
	 */
	public static final String PARAM_RAW_TARGET_LOCATION = "pRawTargetLocation";
	@ConfigurationParameter(name = PARAM_RAW_TARGET_LOCATION, mandatory = false)
	private String pRawTargetLocation;
	
	/**
	 * Target location. If this parameter is not set, data is written to stdout.
	 */
	public static final String PARAM_RAW_FILENAME_SUFFIX = "pRawFilenameSuffix";
	@ConfigurationParameter(name = PARAM_RAW_FILENAME_SUFFIX, mandatory = false, defaultValue = ".txt")
	private String pRawFilenameSuffix;
	
	public static final String PARAM_USE_TTLAB_TYPESYSTEM = "pUseTTLabTypesystem";
	@ConfigurationParameter(name = PARAM_USE_TTLAB_TYPESYSTEM, mandatory = false, defaultValue = "false")
	private Boolean pUseTTLabTypesystem;
	
	public static final String PARAM_USE_TTLAB_CONLL_FEATURES = "pUseTTLabConllFeatures";
	@ConfigurationParameter(name = PARAM_USE_TTLAB_CONLL_FEATURES, mandatory = false, defaultValue = "false")
	private Boolean pUseTTLabConllFeatures;
	
	public static final String PARAM_MERGE_VIEWS = "pMergeViews";
	@ConfigurationParameter(name = PARAM_MERGE_VIEWS, mandatory = false, defaultValue = "true")
	private Boolean pMergeViews;
	
	public static final String PARAM_REMOVE_DUPLICATES_SAME_TYPE = "pRemoveDuplicatesSameType";
	@ConfigurationParameter(name = PARAM_REMOVE_DUPLICATES_SAME_TYPE, mandatory = false, defaultValue = "true")
	private Boolean pRemoveDuplicatesSameType;
	
	public static final String PARAM_ANNOTATOR_LIST = "pAnnotatorList";
	@ConfigurationParameter(name = PARAM_ANNOTATOR_LIST, mandatory = false, defaultValue = "",
			description = "Array of view names that should be considered during view merge..")
	private String[] pAnnotatorList;
	
	public static final String PARAM_MIN_VIEWS = "pMinViews";
	@ConfigurationParameter(name = PARAM_MIN_VIEWS, mandatory = false, defaultValue = "2",
			description = "Minimum number of filtered views to be processed.")
	private Integer pMinViews;
	
	public static final String PARAM_ANNOTATOR_RELATION = "pAnnotatorRelation";
	@ConfigurationParameter(name = PARAM_ANNOTATOR_RELATION, mandatory = false, defaultValue = "true",
			description = "Decides weather to white- to or blacklist the given annotators. Default: ConllBIO2003Writer.WHITELIST"
	)
	private Boolean pAnnotatorRelation;
	public static boolean WHITELIST = true;
	public static boolean BLACKLIST = false;
	
	public static final String PARAM_FILTER_BY_AGREEMENT = "pFilterByAgreement";
	@ConfigurationParameter(
			name = PARAM_FILTER_BY_AGREEMENT,
			mandatory = false,
			defaultValue = "-1.0",
			description = "Set to minimal required double inter-annotator agreement value to filter by category. " +
					"REQUIRES IAA VIEW WITH DOCUMENT ANNOTATIONS!"
	)
	private Float pFilterByAgreement;
	
	public static final String PARAM_FILTER_EMPTY_SENTENCES = "pFilterEmptySentences";
	@ConfigurationParameter(
			name = PARAM_FILTER_EMPTY_SENTENCES,
			mandatory = false,
			defaultValue = "true",
			description = "If set true, filter sentences that have not at least one annotation. Default: true."
	)
	Boolean pFilterEmptySentences;
	
	public static final String PARAM_ONLY_PRINT_PRESENT = "pOnlyPrintPresent";
	@ConfigurationParameter(name = PARAM_ONLY_PRINT_PRESENT, mandatory = false, defaultValue = "false",
			description = "Only print columns for present annotations."
	)
	private Boolean pOnlyPrintPresent;
	
	public static final String PARAM_RETAIN_CLASSES = "pRetainClasses";
	@ConfigurationParameter(name = PARAM_RETAIN_CLASSES, mandatory = false)
	protected String[] pRetainClasses;
	protected ArrayList<Class<? extends Annotation>> classesToRetain;
	
	public static final String PARAM_TAG_ALL_AS = "pTagAllAs";
	@ConfigurationParameter(name = PARAM_TAG_ALL_AS, mandatory = false)
	protected String pTagAllAs;
	
	// End of AnalysisComponent parameters
	
	
	@Override
	public void initialize(UimaContext context) throws ResourceInitializationException {
		super.initialize(context);
		if (pRetainClasses != null && pRetainClasses.length > 0) {
			classesToRetain = new ArrayList<>();
			for (String retainClass : pRetainClasses) {
				try {
					classesToRetain.add((Class<? extends Annotation>) Class.forName(retainClass));
				} catch (ClassNotFoundException e) {
					getLogger().error(String.format("Encountered invalid class name '%s' in agreement categories!", retainClass), e);
				}
			}
		} else {
			classesToRetain = new ArrayList<>();
			classesToRetain.add(Annotation.class);
		}
	}
	
	@Override
	public void process(JCas aJCas) throws AnalysisEngineProcessException {
		if (!pExportRawOnly) {
			try {
				boolean doFilterByAgreement = pFilterByAgreement > 0.0;
				boolean hasIaaView = hasIaaView(aJCas);
				ImmutableSet<String> validViewNames = getValidViewNames(aJCas);
				
				if (doFilterByAgreement && hasIaaView) {
					JCas iaaView = aJCas.getView("IAA");
					AgreementContainer agreementContainer = JCasUtil.selectSingle(iaaView, AgreementContainer.class);
					StringArray categoryNames = agreementContainer.getCategoryNames();
					DoubleArray categoryAgreementValues = agreementContainer.getCategoryAgreementValues();
					ArrayList<Class<? extends Annotation>> filteredCategories = getFilteredCategories(categoryNames, categoryAgreementValues);
					
					// Check if there are categories with high enough agreement
					if (!filteredCategories.isEmpty()) {
						printWithIaaFiltering(aJCas, filteredCategories, validViewNames);
					} else {
						printWarning(aJCas, String.format(" as no category has at least %.2f agreement.", pFilterByAgreement));
					}
				} else { // No IAA filtering
					if (validViewNames.size() >= pMinViews) { // .. but at least the required number of views
						printWithoutIaaFiltering(aJCas, validViewNames);
					} else {
						printWarning(aJCas, " as it does not confirm to view constraint.");
					}
				}
			} catch (CASException | CASRuntimeException e) {
				try {
					getLogger().warn(String.format("%s Skipping JCas '%s'.", e.getMessage(), DocumentMetaData.get(aJCas).getDocumentId()));
				} catch (Exception x) {
					getLogger().warn(String.format("%s Skipping JCas.", e.getMessage()));
				}
				return;
			} catch (UIMAException e) {
				throw new AnalysisEngineProcessException(e);
			}
		}
		if (pExportRaw || pExportRawOnly) {
			try (PrintWriter rawWriter = getRawPrintWriter(aJCas, pRawFilenameSuffix)) {
				rawWriter.print(aJCas.getDocumentText());
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	
	@Nonnull
	ArrayList<Class<? extends Annotation>> getFilteredCategories(StringArray categoryNames, DoubleArray categoryAgreementValues) {
		ArrayList<Class<? extends Annotation>> filteredCategories;
		filteredCategories = new ArrayList<>();
		for (int i = 0; i < categoryNames.size(); i++) {
			String category = categoryNames.get(i);
			double value = categoryAgreementValues.get(i);
			if (value >= pFilterByAgreement) {
				try {
					filteredCategories.add((Class<? extends Annotation>) Class.forName(category));
				} catch (ClassCastException | ClassNotFoundException e) {
					getLogger().error(String.format("Encountered invalid class name '%s' in agreement categories!", category), e);
				}
			}
		}
		return filteredCategories;
	}
	
	boolean printWithoutIaaFiltering(JCas aJCas, ImmutableSet<String> validViewNames) throws UIMAException {
		return printWithIaaFiltering(aJCas, classesToRetain, validViewNames);
	}
	
	boolean printWithIaaFiltering(JCas aJCas, ArrayList<Class<? extends Annotation>> filteredCategories, ImmutableSet<String> validViewNames) throws UIMAException {
		if (pUseTTLabTypesystem) {
			TTLabHierarchicalIobEncoder hierarchicalBioEncoder = getTTLabHierarchicalIobEncoder(aJCas, filteredCategories, validViewNames);
			hierarchicalBioEncoder.setAnnotatorRelation(pAnnotatorRelation);
			hierarchicalBioEncoder.setFilterFingerprinted(pFilterFingerprinted);
			hierarchicalBioEncoder.setUseTTLabConllFeatures(pUseTTLabConllFeatures);
			hierarchicalBioEncoder.setRemoveDuplicateSameType(pRemoveDuplicatesSameType);
			hierarchicalBioEncoder.setMergeViews(pMergeViews);
			// FIXME: This does not belog to TTLabHierarchicalIobEncoder, but instead only to TTLabOneColumnPerClassEncoder. Move it there and refactor this hacky selection of Encoders..
			hierarchicalBioEncoder.setOnlyPrintPresentAnnotations(pOnlyPrintPresent);
			hierarchicalBioEncoder.build();
			if (hierarchicalBioEncoder.getNamedEntitiyCount() > 0) {
				printConllFile(hierarchicalBioEncoder);
			} else {
				printWarning(aJCas, " as it does not contain any named entities.");
				return true;
			}
		} else {
			DKProHierarchicalIobEncoder hierarchicalBioEncoder = getDKProHierarchicalIobEncoder(aJCas, filteredCategories, validViewNames);
			hierarchicalBioEncoder.setAnnotatorRelation(pAnnotatorRelation);
			hierarchicalBioEncoder.setFilterFingerprinted(pFilterFingerprinted);
			hierarchicalBioEncoder.setRemoveDuplicateSameType(pRemoveDuplicatesSameType);
			hierarchicalBioEncoder.build();
			if (hierarchicalBioEncoder.getNamedEntitiyCount() > 0) {
				printConllFile(hierarchicalBioEncoder);
			} else {
				printWarning(aJCas, " as it does not contain any named entities.");
				return true;
			}
		}
		return false;
	}
	
	@Nonnull
	TTLabHierarchicalIobEncoder getTTLabHierarchicalIobEncoder(JCas aJCas, ArrayList<Class<? extends Annotation>> filteredCategories, ImmutableSet<String> validViewNames) throws UIMAException {
		if (filteredCategories == null) {
			return new TTLabHierarchicalIobEncoder(aJCas, validViewNames);
		} else {
			return new TTLabHierarchicalIobEncoder(aJCas, filteredCategories, validViewNames);
		}
	}
	
	@Nonnull
	DKProHierarchicalIobEncoder getDKProHierarchicalIobEncoder(JCas aJCas, ArrayList<Class<? extends Annotation>> filteredCategories, ImmutableSet<String> validViewNames) throws UIMAException {
		if (filteredCategories == null) {
			return new DKProHierarchicalIobEncoder(aJCas, validViewNames);
		} else {
			return new DKProHierarchicalIobEncoder(aJCas, filteredCategories, validViewNames);
		}
	}
	
	private void printWarning(JCas aJCas, String message) {
		try {
			getLogger().warn(String.format("Skipping JCas '%s'", DocumentMetaData.get(aJCas).getDocumentId()) + message);
		} catch (Exception x) {
			getLogger().warn("Skipping JCas" + message);
		}
	}
	
	<T extends Annotation> void printConllFile(GenericIobEncoder<T> hierarchicalBioEncoder) {
		JCas aJCas = hierarchicalBioEncoder.getMergedCas();
		try (PrintWriter conllWriter = getPrintWriter(aJCas, filenameSuffix)) {
			
			int emptySentences = 0;
			int globalEntityCount = 0;
			for (Sentence sentence : select(aJCas, Sentence.class)) {
				int entityCount = 0;
				HashMap<Token, Row> ctokens = new LinkedHashMap<>();
				
				// Tokens
				List<Token> coveredTokens = selectCovered(Token.class, sentence);
				for (Token token : coveredTokens) {
					Lemma lemma = token.getLemma();
					POS pos = token.getPos();
					Row row = new Row();
					row.token = token;
					
					row.lemma = UNUSED;
					if (writeChunk && lemma != null) {
						String lemmaValue = lemma.getValue();
						if (!Strings.isNullOrEmpty(lemmaValue) && !lemmaValue.equals("null")) {
							row.lemma = lemmaValue;
						} else row.lemma = UNUSED;
					}
					
					row.pos = UNUSED;
					if (writePos && pos != null) {
						String posValue = pos.getPosValue();
						if (!Strings.isNullOrEmpty(posValue) && !posValue.equals("null")) {
							row.pos = posValue;
						}
					}
					
					if (pNamedEntityColumns < 2) {
						row.entities = hierarchicalBioEncoder.getFeatures(token, pEncoderStrategyIndex);
						if (!row.entities.isEmpty() && !ImmutableSet.of("O", "B-O").contains(row.entities.get(0))) {
							entityCount++;
						}
					} else {
						row.entities = hierarchicalBioEncoder.getFeaturesForNColumns(token, pEncoderStrategyIndex, pNamedEntityColumns);
						if (!row.entities.isEmpty() && !ImmutableSet.of("O", "B-O").contains(row.entities.get(0))) {
							entityCount++;
						}
					}
					
					ctokens.put(row.token, row);
				}
				
				// Check for empty sentences if parameter was set
				if (!pFilterEmptySentences || entityCount > 0) {
					globalEntityCount += entityCount;
					// Write sentence in CONLL 2006 format
					for (Row row : ctokens.values()) {
						String pos = row.pos;
						String lemma = row.lemma;
						
						String namedEntityFeatures = UNUSED;
						if (writeNamedEntity && (row.entities != null)) {
							StringBuilder neBuilder = new StringBuilder();
							ArrayList<String> ne = row.entities;
							for (int i = 0; i < ne.size(); ) {
								String entry = ne.get(i);
								neBuilder.append(entry);
								if (++i < ne.size()) {
									neBuilder.append(pConllSeparator);
								}
							}
							namedEntityFeatures = neBuilder.toString();
						}
						
						if (pTagAllAs != null && !pTagAllAs.isEmpty()) {
							namedEntityFeatures = namedEntityFeatures.replaceAll("-[\\w_]+", "-" + pTagAllAs);
						}
						
						conllWriter.printf("%s%s%s%s%s%s%s%n", row.token.getCoveredText(), pConllSeparator, pos, pConllSeparator, lemma, pConllSeparator, namedEntityFeatures);
					}
					conllWriter.println();
				} else {
					emptySentences++;
				}
			}
			if (emptySentences > 0) {
				getLogger().info(String.format("Skipped %d empty sentences.", emptySentences));
			}
			getLogger().info(String.format("Wrote file with %d tags.", globalEntityCount));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private boolean hasIaaView(JCas aJCas) throws CASException {
		return Iterators.tryFind(aJCas.getViewIterator(), p -> p.getViewName().equals("IAA")).isPresent();
	}
	
	private ImmutableSet<String> getValidViewNames(JCas aJCas) throws CASException {
		LinkedHashSet<String> validViewNames = Streams.stream(aJCas.getViewIterator())
				.map(JCas::getViewName)
				.filter(fullName -> {
					// If whitelisting (true), the name must be in the set; if blacklisting (false), it must not be in the set
					String viewName = StringUtils.substringAfterLast(fullName.trim(), "/");
					return StringUtils.isNotEmpty(viewName) && pAnnotatorRelation == ImmutableSet.copyOf(pAnnotatorList).contains(viewName);
				})
				.collect(Collectors.toCollection(LinkedHashSet::new));
		return ImmutableSet.copyOf(validViewNames);
	}
	
	@NotNull
	PrintWriter getPrintWriter(JCas aJCas, String aExtension) throws IOException {
		String relativePath = getFileName(aJCas);
		Files.createDirectories(Paths.get(targetLocation));
		File file = new File(targetLocation, relativePath + aExtension);
		if (!targetOverwrite && file.exists()) {
			throw new IOException(String.format("File '%s' already exists!\n", file.getAbsolutePath()));
		}
		return new PrintWriter(new OutputStreamWriter(FileUtils.openOutputStream(file), targetEncoding));
	}
	
	private String getFileName(JCas aJCas) {
		DocumentMetaData meta = DocumentMetaData.get(aJCas);
		String path = meta.getDocumentId() == null || meta.getDocumentId().isEmpty() ? StringUtils.substringAfterLast(meta.getDocumentUri(), "/") : meta.getDocumentId();
		return path.replaceAll("\\.xmi", "");
	}
	
	@NotNull
	private PrintWriter getRawPrintWriter(JCas aJCas, String aExtension) throws IOException {
		if (pRawTargetLocation == null) {
			return new PrintWriter(new CloseShieldOutputStream(System.out));
		} else {
			Files.createDirectories(Paths.get(pRawTargetLocation));
			return new PrintWriter(getRawOutputStream(getFileName(aJCas), aExtension));
		}
	}
	
	private OutputStream getRawOutputStream(String aRelativePath, String aExtension) throws IOException {
		File outputFile = new File(pRawTargetLocation, aRelativePath + aExtension);
		
		File file = new File(outputFile.getAbsolutePath());
		if (!targetOverwrite && file.exists()) {
			throw new IOException(String.format("File '%s' already exists!\n", file.getAbsolutePath()));
		}
		return FileUtils.openOutputStream(file);
	}
	
	static final class Row {
		Token token;
		String lemma;
		String pos;
		ArrayList<String> entities;
	}
	
	
}

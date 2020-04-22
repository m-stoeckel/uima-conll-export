import BIOfid.BioEncoder.DKProHierarchicalBioEncoder;
import BIOfid.Extraction.ConllBIO2003Writer;
import com.google.common.collect.Lists;
import de.tudarmstadt.ukp.dkpro.core.api.metadata.type.DocumentMetaData;
import de.tudarmstadt.ukp.dkpro.core.api.ner.type.Location;
import de.tudarmstadt.ukp.dkpro.core.api.ner.type.Organization;
import de.tudarmstadt.ukp.dkpro.core.api.ner.type.Person;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Sentence;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token;
import de.tudarmstadt.ukp.dkpro.core.io.xmi.XmiReader;
import org.apache.uima.UIMAException;
import org.apache.uima.analysis_engine.AnalysisEngine;
import org.apache.uima.collection.CollectionReader;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.fit.factory.CollectionReaderFactory;
import org.apache.uima.fit.factory.JCasFactory;
import org.apache.uima.fit.pipeline.SimplePipeline;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.biofid.agreement.engine.TTLabUnitizingIAACollectionProcessingEngine;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;

public class ConllBIO2003WriterTest {
	
	@Test
	public void testEncoder() {
		try {
			JCas jCas = getjCas();
			
			DKProHierarchicalBioEncoder encoder = new DKProHierarchicalBioEncoder(jCas, false);
			
			ArrayList<Token> tokens = Lists.newArrayList(JCasUtil.select(jCas, Token.class));
			for (int i = 0; i < tokens.size(); i++) {
				System.out.printf("%s %s\n", tokens.get(i).getCoveredText(), encoder.getFeatures(i, 0));
			}
			System.out.println();
			for (int i = 0; i < tokens.size(); i++) {
				System.out.printf("%s %s\n", tokens.get(i).getCoveredText(), encoder.getFeatures(i, 1));
			}
			System.out.println();
			for (int i = 0; i < tokens.size(); i++) {
				System.out.printf("%s %s\n", tokens.get(i).getCoveredText(), encoder.getFeatures(i, 2));
			}
			System.out.println();
			for (int i = 0; i < tokens.size(); i++) {
				System.out.printf("%s %s\n", tokens.get(i).getCoveredText(), encoder.getFeatures(i, 3));
			}
			System.out.println();
		} catch (UIMAException e) {
			e.printStackTrace();
		}
	}
	
	@Test
	public void conllWriter() {
		try {
			JCas jCas = getjCas();
			
			// Metadata
			DocumentMetaData documentMetaData = new DocumentMetaData(jCas);
			documentMetaData.setDocumentId("basic");
			documentMetaData.setDocumentUri("basicGUF");
			
			final AnalysisEngine conllEngine = AnalysisEngineFactory.createEngine(
					ConllBIO2003Writer.class,
					ConllBIO2003Writer.PARAM_TARGET_LOCATION, "src/test/out/",
					ConllBIO2003Writer.PARAM_OVERWRITE, true,
					ConllBIO2003Writer.PARAM_FILTER_FINGERPRINTED, false);
			
			conllEngine.process(jCas);
		} catch (UIMAException e) {
			e.printStackTrace();
		}
	}
	
	@Test
	public void singleColumn() {
		try {
			JCas jCas = getjCas();
			
			// Metadata
			DocumentMetaData documentMetaData = new DocumentMetaData(jCas);
			documentMetaData.setDocumentId("singleColumn");
			documentMetaData.setDocumentUri("singleColumnGUF");
			
			final AnalysisEngine conllEngine = AnalysisEngineFactory.createEngine(
					ConllBIO2003Writer.class,
					ConllBIO2003Writer.PARAM_TARGET_LOCATION, "src/test/out/",
					ConllBIO2003Writer.PARAM_OVERWRITE, true,
					ConllBIO2003Writer.PARAM_FILTER_FINGERPRINTED, false);
			
			conllEngine.process(jCas);
		} catch (UIMAException e) {
			e.printStackTrace();
		}
	}
	
	@Test
	public void exampleFile() {
		try {
			String[] annotatorWhitelist = {"305236", "305235"};
			final AnalysisEngine agreementEngine = AnalysisEngineFactory.createEngine(TTLabUnitizingIAACollectionProcessingEngine.class,
					TTLabUnitizingIAACollectionProcessingEngine.PARAM_ANNOTATOR_LIST, annotatorWhitelist,
					TTLabUnitizingIAACollectionProcessingEngine.PARAM_ANNOTATOR_RELATION, TTLabUnitizingIAACollectionProcessingEngine.WHITELIST,
					TTLabUnitizingIAACollectionProcessingEngine.PARAM_MULTI_CAS_HANDLING, TTLabUnitizingIAACollectionProcessingEngine.SEPARATE,
					TTLabUnitizingIAACollectionProcessingEngine.PARAM_PRINT_STATS, false,
					TTLabUnitizingIAACollectionProcessingEngine.PARAM_MIN_VIEWS, 2,
					TTLabUnitizingIAACollectionProcessingEngine.PARAM_MIN_ANNOTATIONS, 0,
					TTLabUnitizingIAACollectionProcessingEngine.PARAM_ANNOTATE_DOCUMENT, true
			);
			
			final AnalysisEngine conllEngine = AnalysisEngineFactory.createEngine(
					ConllBIO2003Writer.class,
					ConllBIO2003Writer.PARAM_TARGET_LOCATION, "/home/stud_homes/s3676959/Data/BIOfid/Annotated/conll/",
					ConllBIO2003Writer.PARAM_OVERWRITE, true,
					ConllBIO2003Writer.PARAM_USE_TTLAB_TYPESYSTEM, true,
					ConllBIO2003Writer.PARAM_STRATEGY_INDEX, 1,
					ConllBIO2003Writer.PARAM_FILTER_FINGERPRINTED, true,
					ConllBIO2003Writer.PARAM_ANNOTATOR_LIST, annotatorWhitelist,
					ConllBIO2003Writer.PARAM_ANNOTATOR_RELATION, ConllBIO2003Writer.WHITELIST,
					ConllBIO2003Writer.PARAM_MIN_VIEWS, 1,
					ConllBIO2003Writer.PARAM_FILTER_BY_AGREEMENT, 0.6F,
					ConllBIO2003Writer.PARAM_FILTER_EMPTY_SENTENCES, true);
			
			CollectionReader reader = CollectionReaderFactory.createReader(XmiReader.class,
					XmiReader.PARAM_PATTERNS, "[+]**.xmi",
					XmiReader.PARAM_SOURCE_LOCATION, "/home/stud_homes/s3676959/Data/BIOfid/Annotated/xmi/",
					XmiReader.PARAM_LENIENT, true
			);
			SimplePipeline.runPipeline(reader, agreementEngine, conllEngine);
		} catch (UIMAException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	@Test
	public void doubleColumn() {
		try {
			JCas jCas = getjCas();
			
			// Metadata
			DocumentMetaData documentMetaData = new DocumentMetaData(jCas);
			documentMetaData.setDocumentId("doubleColumn");
			documentMetaData.setDocumentUri("doubleColumnGUF");
			
			final AnalysisEngine conllEngine = AnalysisEngineFactory.createEngine(
					ConllBIO2003Writer.class,
					ConllBIO2003Writer.PARAM_TARGET_LOCATION, "src/test/out/",
					ConllBIO2003Writer.PARAM_OVERWRITE, true,
					ConllBIO2003Writer.PARAM_NAMED_ENTITY_COLUMNS, 2,
					ConllBIO2003Writer.PARAM_FILTER_FINGERPRINTED, false);
			
			conllEngine.process(jCas);
		} catch (UIMAException e) {
			e.printStackTrace();
		}
	}
	
	@Test
	public void tripleColumn() {
		try {
			JCas jCas = getjCas();
			
			// Metadata
			DocumentMetaData documentMetaData = new DocumentMetaData(jCas);
			documentMetaData.setDocumentId("tripleColumn");
			documentMetaData.setDocumentUri("tripleColumnGUF");
			
			final AnalysisEngine conllEngine = AnalysisEngineFactory.createEngine(
					ConllBIO2003Writer.class,
					ConllBIO2003Writer.PARAM_TARGET_LOCATION, "src/test/out/",
					ConllBIO2003Writer.PARAM_OVERWRITE, true,
					ConllBIO2003Writer.PARAM_NAMED_ENTITY_COLUMNS, 3,
					ConllBIO2003Writer.PARAM_STRATEGY_INDEX, 1,
					ConllBIO2003Writer.PARAM_FILTER_FINGERPRINTED, false);
			
			conllEngine.process(jCas);
		} catch (UIMAException e) {
			e.printStackTrace();
		}
	}
	
	
	private JCas getjCas() throws UIMAException {
		JCas jCas = JCasFactory.createJCas();
		jCas.setDocumentText("Goethe Universität Frankfurt am Main");
		
		jCas.addFsToIndexes(new Sentence(jCas, 0, 36));
		jCas.addFsToIndexes(new Token(jCas, 0, 6));
		jCas.addFsToIndexes(new Token(jCas, 7, 18));
		jCas.addFsToIndexes(new Token(jCas, 19, 28));
		jCas.addFsToIndexes(new Token(jCas, 29, 31));
		jCas.addFsToIndexes(new Token(jCas, 32, 36));
		
		// Goethe Universität Frankfurt
		Organization org = new Organization(jCas, 0, 28);
		org.setValue("ORG");
		jCas.addFsToIndexes(org);
		
		// Goethe Universität
		Organization org2 = new Organization(jCas, 0, 18);
		org2.setValue("ORG");
		jCas.addFsToIndexes(org2);

//		// Universität Frankfurt
		Organization org3 = new Organization(jCas, 7, 28);
		org3.setValue("ORG");
		jCas.addFsToIndexes(org3);
		
		// Goethe
		Person person = new Person(jCas, 0, 6);
		person.setValue("PER");
		jCas.addFsToIndexes(person);
		
		// Frankfurt
		Location location = new Location(jCas, 19, 28);
		location.setValue("LOC");
		jCas.addFsToIndexes(location);
		
		// Frankfurt am Main
		Location location2 = new Location(jCas, 19, 36);
		location2.setValue("LOC");
		jCas.addFsToIndexes(location2);

//		// Main
		Location location3 = new Location(jCas, 32, 36);
		location3.setValue("LOC");
		jCas.addFsToIndexes(location3);
		
		return jCas;
	}
}
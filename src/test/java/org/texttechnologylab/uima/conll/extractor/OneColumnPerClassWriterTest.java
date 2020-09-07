package org.texttechnologylab.uima.conll.extractor;

import de.tudarmstadt.ukp.dkpro.core.api.metadata.type.DocumentMetaData;
import de.tudarmstadt.ukp.dkpro.core.api.ner.type.Location;
import de.tudarmstadt.ukp.dkpro.core.api.ner.type.Organization;
import de.tudarmstadt.ukp.dkpro.core.api.ner.type.Person;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Sentence;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token;
import org.apache.uima.UIMAException;
import org.apache.uima.analysis_engine.AnalysisEngine;
import org.apache.uima.collection.CollectionReader;
import org.apache.uima.fit.factory.AggregateBuilder;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.fit.factory.CollectionReaderFactory;
import org.apache.uima.fit.factory.JCasFactory;
import org.apache.uima.fit.pipeline.SimplePipeline;
import org.apache.uima.jcas.JCas;
import org.dkpro.core.io.conll.Conll2003Reader;
import org.dkpro.core.io.xmi.XmiReader;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.texttechnologylab.agreement.engine.TTLabUnitizingIAACollectionProcessingEngine;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.fail;
import static org.texttechnologylab.agreement.engine.TTLabUnitizingIAACollectionProcessingEngine.*;

public class OneColumnPerClassWriterTest {
	
	@Test
	@Disabled
	public void conllWriter() {
		try {
			JCas jCas = getjCas();
			
			// Metadata
			DocumentMetaData documentMetaData = DocumentMetaData.create(jCas);
			documentMetaData.setDocumentId("basic");
			documentMetaData.setDocumentUri("basicGUF");
			
			final AnalysisEngine conllEngine = AnalysisEngineFactory.createEngine(
					OneClassPerColumnWriter.class,
					OneClassPerColumnWriter.PARAM_TARGET_LOCATION, "src/test/resources/out/",
					OneClassPerColumnWriter.PARAM_OVERWRITE, true,
					OneClassPerColumnWriter.PARAM_FILTER_FINGERPRINTED, false,
					OneClassPerColumnWriter.PARAM_MIN_VIEWS, 0,
					OneClassPerColumnWriter.PARAM_USE_TTLAB_TYPESYSTEM, false,
					OneClassPerColumnWriter.PARAM_ANNOTATOR_RELATION, false
			);
			
			conllEngine.process(jCas);
		} catch (UIMAException e) {
			e.printStackTrace();
			fail();
		}
	}
	
	@Test
	@Disabled
	public void singleColumn() {
		try {
			JCas jCas = getjCas();
			
			// Metadata
			DocumentMetaData documentMetaData = DocumentMetaData.create(jCas);
			documentMetaData.setDocumentId("singleColumn");
			documentMetaData.setDocumentUri("singleColumnGUF");
			
			final AnalysisEngine conllEngine = AnalysisEngineFactory.createEngine(
					OneClassPerColumnWriter.class,
					OneClassPerColumnWriter.PARAM_TARGET_LOCATION, "src/test/out/",
					OneClassPerColumnWriter.PARAM_OVERWRITE, true,
					OneClassPerColumnWriter.PARAM_FILTER_FINGERPRINTED, false,
					OneClassPerColumnWriter.PARAM_MIN_VIEWS, 0,
					OneClassPerColumnWriter.PARAM_USE_TTLAB_TYPESYSTEM, false,
					OneClassPerColumnWriter.PARAM_ANNOTATOR_RELATION, false
			);
			
			conllEngine.process(jCas);
		} catch (UIMAException e) {
			e.printStackTrace();
			fail();
		}
	}
	
	@Test
	@Disabled
	public void doubleColumn() {
		try {
			JCas jCas = getjCas();
			
			// Metadata
			DocumentMetaData documentMetaData = DocumentMetaData.create(jCas);
			documentMetaData.setDocumentId("doubleColumn");
			documentMetaData.setDocumentUri("doubleColumnGUF");
			
			final AnalysisEngine conllEngine = AnalysisEngineFactory.createEngine(
					OneClassPerColumnWriter.class,
					OneClassPerColumnWriter.PARAM_TARGET_LOCATION, "src/test/out/",
					OneClassPerColumnWriter.PARAM_OVERWRITE, true,
					OneClassPerColumnWriter.PARAM_NAMED_ENTITY_COLUMNS, 2,
					OneClassPerColumnWriter.PARAM_FILTER_FINGERPRINTED, false,
					OneClassPerColumnWriter.PARAM_MIN_VIEWS, 0,
					OneClassPerColumnWriter.PARAM_USE_TTLAB_TYPESYSTEM, false,
					OneClassPerColumnWriter.PARAM_ANNOTATOR_RELATION, false
			);
			
			conllEngine.process(jCas);
		} catch (UIMAException e) {
			e.printStackTrace();
			fail();
		}
	}
	
	@Test
	@Disabled
	public void tripleColumn() {
		try {
			JCas jCas = getjCas();
			
			// Metadata
			DocumentMetaData documentMetaData = DocumentMetaData.create(jCas);
			documentMetaData.setDocumentId("tripleColumn");
			documentMetaData.setDocumentUri("tripleColumnGUF");
			
			final AnalysisEngine conllEngine = AnalysisEngineFactory.createEngine(
					OneClassPerColumnWriter.class,
					OneClassPerColumnWriter.PARAM_TARGET_LOCATION, "src/test/out/",
					OneClassPerColumnWriter.PARAM_OVERWRITE, true,
					OneClassPerColumnWriter.PARAM_NAMED_ENTITY_COLUMNS, 3,
					OneClassPerColumnWriter.PARAM_FILTER_FINGERPRINTED, false,
					OneClassPerColumnWriter.PARAM_MIN_VIEWS, 0,
					OneClassPerColumnWriter.PARAM_USE_TTLAB_TYPESYSTEM, false,
					OneClassPerColumnWriter.PARAM_ANNOTATOR_RELATION, false
			);
			
			conllEngine.process(jCas);
		} catch (UIMAException e) {
			e.printStackTrace();
			fail();
		}
	}
	
	@Test
	public void exampleFile() {
		try {
			String[] annotatorWhitelist = {"306229", "306299", "305236", "305235", "302902", "306614"};
			String[] annotatorBlacklist = {"0", "302904", "303228", "306320", "305718", "306513"};
			final AnalysisEngine agreementEngine = AnalysisEngineFactory.createEngine(
					TTLabUnitizingIAACollectionProcessingEngine.class,
					PARAM_ANNOTATOR_RELATION, WHITELIST,
					PARAM_ANNOTATOR_LIST, annotatorWhitelist,
//					PARAM_ANNOTATOR_RELATION, BLACKLIST,
//					PARAM_ANNOTATOR_LIST, annotatorBlacklist,
					PARAM_MULTI_CAS_HANDLING, SEPARATE,
					PARAM_PRINT_STATS, false,
					PARAM_MIN_VIEWS, 1,
					PARAM_MIN_ANNOTATIONS, 0,
					PARAM_ANNOTATE_DOCUMENT, true
			);
			
			final AnalysisEngine conllEngine = AnalysisEngineFactory.createEngine(
					OneClassPerColumnWriter.class,
					OneClassPerColumnWriter.PARAM_TARGET_LOCATION, "/home/manu/Work/data/conll/annotated/",
					OneClassPerColumnWriter.PARAM_OVERWRITE, true,
					OneClassPerColumnWriter.PARAM_USE_TTLAB_TYPESYSTEM, true,
					OneClassPerColumnWriter.PARAM_USE_TTLAB_CONLL_FEATURES, false,
					OneClassPerColumnWriter.PARAM_FILTER_FINGERPRINTED, false,
					OneClassPerColumnWriter.PARAM_ANNOTATOR_RELATION, OneClassPerColumnWriter.WHITELIST,
					OneClassPerColumnWriter.PARAM_ANNOTATOR_LIST, annotatorWhitelist,
//					OneClassPerColumnWriter.PARAM_ANNOTATOR_RELATION, OneClassPerColumnWriter.BLACKLIST,
//					OneClassPerColumnWriter.PARAM_ANNOTATOR_LIST, annotatorBlacklist,
					OneClassPerColumnWriter.PARAM_MIN_VIEWS, 1,
//					OneClassPerColumnWriter.PARAM_FILTER_BY_AGREEMENT, 0.6F,
					OneClassPerColumnWriter.PARAM_FILTER_EMPTY_SENTENCES, true);
			
			CollectionReader reader = CollectionReaderFactory.createReader(XmiReader.class,
					XmiReader.PARAM_SOURCE_LOCATION, "/home/manu/Work/Utilities/src/test/out/xmi/",
					XmiReader.PARAM_PATTERNS, "[+]**.xmi",
					XmiReader.PARAM_LENIENT, true
			);
			SimplePipeline.runPipeline(reader, agreementEngine, conllEngine);
		} catch (UIMAException | IOException e) {
			e.printStackTrace();
			fail();
		}
	}
	
	@Test
	public void testBIOfid() {
		try {
			CollectionReader reader = CollectionReaderFactory.createReader(XmiReader.class,
					XmiReader.PARAM_SOURCE_LOCATION, "src/test/resources/",
					XmiReader.PARAM_PATTERNS, "[+]3671249-gazetteer.xmi",
					XmiReader.PARAM_LENIENT, true
			);
			
			AggregateBuilder aggregateBuilder = new AggregateBuilder();
			aggregateBuilder.add(AnalysisEngineFactory.createEngineDescription(
					OneClassPerColumnWriter.class,
					OneClassPerColumnWriter.PARAM_TARGET_LOCATION, "src/test/resources/out/",
					OneClassPerColumnWriter.PARAM_OVERWRITE, true,
					OneClassPerColumnWriter.PARAM_USE_TTLAB_TYPESYSTEM, true,
					OneClassPerColumnWriter.PARAM_USE_TTLAB_CONLL_FEATURES, false,
					OneClassPerColumnWriter.PARAM_FILTER_FINGERPRINTED, false,
					OneClassPerColumnWriter.PARAM_REMOVE_DUPLICATES_SAME_TYPE, false,
					OneClassPerColumnWriter.PARAM_MERGE_VIEWS, false,
					OneClassPerColumnWriter.PARAM_MIN_VIEWS, 0,
					OneClassPerColumnWriter.PARAM_FILTER_EMPTY_SENTENCES, true,
					OneClassPerColumnWriter.PARAM_ONLY_PRINT_PRESENT, false
			));
			
			SimplePipeline.runPipeline(reader, aggregateBuilder.createAggregate());
		} catch (IOException | UIMAException e) {
			e.printStackTrace();
			fail();
		}
	}
	
	@Test
	@Disabled
	public void testConll() {
		String home = System.getenv("HOME");
		try {
			CollectionReader reader = CollectionReaderFactory.createReader(Conll2003Reader.class,
					Conll2003Reader.PARAM_SOURCE_LOCATION, home + "/.flair/datasets/wikiner_german/",
					Conll2003Reader.PARAM_PATTERNS, "[+]aij-wikiner-de-wp3.conll2000",
					Conll2003Reader.PARAM_NAMED_ENTITY_MAPPING_LOCATION, "/home/manu/Work/biofid-gazetteer/src/main/resources/org/biofid/gazetteer/lib/ner-default.map"
			);
			JCas jCas = JCasFactory.createJCas();
			reader.getNext(jCas.getCas());
			
			final AnalysisEngine conllEngine = AnalysisEngineFactory.createEngine(
					OneClassPerColumnWriter.class,
					OneClassPerColumnWriter.PARAM_TARGET_LOCATION, "src/test/out/",
					OneClassPerColumnWriter.PARAM_OVERWRITE, true,
					OneClassPerColumnWriter.PARAM_USE_TTLAB_TYPESYSTEM, true,
					OneClassPerColumnWriter.PARAM_USE_TTLAB_CONLL_FEATURES, false,
					OneClassPerColumnWriter.PARAM_FILTER_FINGERPRINTED, false,
					OneClassPerColumnWriter.PARAM_REMOVE_DUPLICATES_SAME_TYPE, false,
					OneClassPerColumnWriter.PARAM_MERGE_VIEWS, false,
					OneClassPerColumnWriter.PARAM_MIN_VIEWS, 0,
					OneClassPerColumnWriter.PARAM_FILTER_EMPTY_SENTENCES, true,
					OneClassPerColumnWriter.PARAM_ONLY_PRINT_PRESENT, true
			);
			
			SimplePipeline.runPipeline(jCas, conllEngine);
		} catch (IOException | UIMAException e) {
			e.printStackTrace();
			fail();
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
package org.texttechnologylab.uima.conll.run;

import org.apache.uima.UIMAException;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.collection.CollectionReader;
import org.apache.uima.fit.factory.AggregateBuilder;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.fit.factory.CollectionReaderFactory;
import org.apache.uima.fit.pipeline.SimplePipeline;
import org.dkpro.core.io.xmi.XmiReader;
import org.texttechnologylab.uima.conll.extractor.ConllBIO2003Writer;

import static org.dkpro.core.io.xmi.XmiReader.*;
import static org.texttechnologylab.uima.conll.extractor.ConllBIO2003Writer.*;

/**
 * Created on 28.01.2019.
 */
public class ExtractConllFromFile {
	public static void main(String[] args) {
		try {
			final AnalysisEngineDescription conllEngineDescription = AnalysisEngineFactory.createEngineDescription(
					ConllBIO2003Writer.class,
					PARAM_TARGET_LOCATION, "/home/stud_homes/s3676959/Documents/BioFID/textimager-uima/textimager-uima-biofid-ocr-parser/src/test/out/TAF/conll",
					PARAM_OVERWRITE, true,
					PARAM_STRATEGY_INDEX, 3,
					PARAM_FILTER_FINGERPRINTED, true,
					PARAM_EXPORT_RAW, false,
					PARAM_TARGET_ENCODING, "UTF-8",
					PARAM_USE_TTLAB_TYPESYSTEM, true
//					ConllBIO2003Writer.PARAM_EXPORT_RAW_ONLY, false,
//					ConllBIO2003Writer.PARAM_RAW_TARGET_LOCATION, "/home/s3676959/Documents/BIOfid/data/EOS/eos_plain_txt/"
			);
			
			try {
				CollectionReader collection = CollectionReaderFactory.createReader(
						XmiReader.class,
						PARAM_SOURCE_LOCATION, "/home/stud_homes/s3676959/Documents/BioFID/textimager-uima/textimager-uima-biofid-ocr-parser/src/test/out/TAF/xmi/",
						PARAM_PATTERNS, "*.xmi",
						PARAM_LENIENT, true
				);
				
				AggregateBuilder ab = new AggregateBuilder();
				ab.add(conllEngineDescription);
				SimplePipeline.runPipeline(collection, ab.createAggregate());
			} catch (Exception e) {
				e.printStackTrace();
			}
			System.out.println("\nDone");
		} catch (UIMAException e) {
			e.printStackTrace();
		}
	}
}

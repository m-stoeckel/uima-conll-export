package org.texttechnologylab.uima.conll.extractor;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import de.tudarmstadt.ukp.dkpro.core.api.lexmorph.type.pos.POS;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Lemma;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Sentence;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token;
import org.apache.uima.fit.descriptor.ConfigurationParameter;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;
import org.texttechnologylab.uima.conll.iobencoder.GenericIobEncoder;
import org.texttechnologylab.uima.conll.iobencoder.TTLabHierarchicalIobEncoder;
import org.texttechnologylab.uima.conll.iobencoder.TTLabOneColumnPerClassEncoder;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

import static org.apache.uima.fit.util.JCasUtil.select;
import static org.apache.uima.fit.util.JCasUtil.selectCovered;

public class OneClassPerColumnWriter extends ConllBIO2003Writer {
	
	<T extends Annotation> void printConllFile(GenericIobEncoder<T> hierarchicalBioEncoder) {
		JCas aJCas = hierarchicalBioEncoder.getMergedCas();
		TTLabOneColumnPerClassEncoder lHierarchicalBioEncoder = (TTLabOneColumnPerClassEncoder) hierarchicalBioEncoder;
		try (PrintWriter conllWriter = getPrintWriter(aJCas, filenameSuffix)) {
			conllWriter.printf("# text pos lemma %s%n", String.join(pConllSeparator, lHierarchicalBioEncoder.getNamedEntityTypes()));
			int emptySentences = 0;
			int entityCount = 0;
			for (Sentence sentence : select(aJCas, Sentence.class)) {
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
					
					row.entities = lHierarchicalBioEncoder.getFeatures(token, pEncoderStrategyIndex);
					if (!row.entities.isEmpty()) {
						entityCount += row.entities.stream().filter(s -> !s.equals("O")).count();
					}
					
					ctokens.put(row.token, row);
				}
				
				// Check for empty sentences if parameter was set
				if (!pFilterEmptySentences || entityCount > 0) {
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
			getLogger().info(String.format("Wrote file with %d tags.", entityCount));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	@Nonnull
	@Override
	TTLabHierarchicalIobEncoder getTTLabHierarchicalIobEncoder(JCas aJCas, ArrayList<Class<? extends Annotation>> filteredCategories, ImmutableSet<String> validViewNames) {
		if (filteredCategories == null) {
			return new TTLabOneColumnPerClassEncoder(aJCas, validViewNames);
		} else {
			return new TTLabOneColumnPerClassEncoder(aJCas, filteredCategories, validViewNames);
		}
	}
}

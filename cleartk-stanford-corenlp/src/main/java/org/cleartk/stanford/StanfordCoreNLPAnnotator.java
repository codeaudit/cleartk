/** 
 * Copyright (c) 2011, Regents of the University of Colorado 
 * All rights reserved.
 * 
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * For a complete copy of the license please see the file LICENSE distributed 
 * with the cleartk-stanford-corenlp project or visit 
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html.
 */
package org.cleartk.stanford;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.FSArray;
import org.apache.uima.resource.ResourceInitializationException;
import org.cleartk.ne.type.NamedEntity;
import org.cleartk.ne.type.NamedEntityMention;
import org.cleartk.syntax.constituent.type.TopTreebankNode;
import org.cleartk.syntax.constituent.type.TreebankNode;
import org.cleartk.syntax.dependency.type.DependencyNode;
import org.cleartk.syntax.dependency.type.DependencyRelation;
import org.cleartk.syntax.dependency.type.TopDependencyNode;
import org.cleartk.token.type.Sentence;
import org.cleartk.token.type.Token;
import org.cleartk.util.AnnotationRetrieval;
import org.cleartk.util.UIMAUtil;
import org.uimafit.component.JCasAnnotator_ImplBase;

import com.google.common.collect.ArrayListMultimap;

import edu.stanford.nlp.ling.CoreAnnotations.BeginIndexAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.CharacterOffsetBeginAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.CharacterOffsetEndAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.CollapsedCCProcessedDependenciesAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.CorefGraphAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.EndIndexAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.LemmaAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.NamedEntityTagAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.PartOfSpeechAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.SentencesAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TokensAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TreeAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.semgraph.SemanticGraph;
import edu.stanford.nlp.trees.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.util.CoreMap;

/**
 * 
 * <br>
 * Copyright (c) 2011, Regents of the University of Colorado <br>
 * All rights reserved.
 * 
 * @author Steven Bethard
 */
public class StanfordCoreNLPAnnotator extends JCasAnnotator_ImplBase {

  private StanfordCoreNLP processor;

  @Override
  public void initialize(UimaContext context) throws ResourceInitializationException {
    super.initialize(context);

    Properties properties = new Properties();
    properties.put("annotators", "tokenize, ssplit, pos, lemma, ner, parse, dcoref");

    this.processor = new StanfordCoreNLP(properties);
  }

  @Override
  public void process(JCas jCas) throws AnalysisEngineProcessException {
    Annotation document = this.processor.process(jCas.getDocumentText());

    String lastNETag = "O";
    int lastNEBegin = -1;
    int lastNEEnd = -1;
    for (CoreMap tokenAnn : document.get(TokensAnnotation.class)) {

      // create the token annotation
      int begin = tokenAnn.get(CharacterOffsetBeginAnnotation.class);
      int end = tokenAnn.get(CharacterOffsetEndAnnotation.class);
      String pos = tokenAnn.get(PartOfSpeechAnnotation.class);
      String lemma = tokenAnn.get(LemmaAnnotation.class);
      Token token = new Token(jCas, begin, end);
      token.setPos(pos);
      token.setLemma(lemma);
      token.addToIndexes();

      // hackery to convert token-level named entity tag into phrase-level tag
      String neTag = tokenAnn.get(NamedEntityTagAnnotation.class);
      if (neTag.equals("O") && !lastNETag.equals("O")) {
        NamedEntityMention ne = new NamedEntityMention(jCas, lastNEBegin, lastNEEnd);
        ne.setMentionType(lastNETag);
        ne.addToIndexes();
      } else {
        if (lastNETag.equals("O")) {
          lastNEBegin = begin;
        } else if (lastNETag.equals(neTag)) {
          // do nothing - begin was already set
        } else {
          NamedEntityMention ne = new NamedEntityMention(jCas, lastNEBegin, lastNEEnd);
          ne.setMentionType(lastNETag);
          ne.addToIndexes();
          lastNEBegin = begin;
        }
        lastNEEnd = end;
      }
      lastNETag = neTag;
    }
    if (!lastNETag.equals("O")) {
      NamedEntityMention ne = new NamedEntityMention(jCas, lastNEBegin, lastNEEnd);
      ne.setMentionType(lastNETag);
      ne.addToIndexes();
    }

    // add sentences and trees
    for (CoreMap sentenceAnn : document.get(SentencesAnnotation.class)) {

      // add the sentence annotation
      int sentBegin = sentenceAnn.get(CharacterOffsetBeginAnnotation.class);
      int sentEnd = sentenceAnn.get(CharacterOffsetEndAnnotation.class);
      Sentence sentence = new Sentence(jCas, sentBegin, sentEnd);
      sentence.addToIndexes();

      // add the syntactic tree annotation
      List<CoreLabel> tokenAnns = sentenceAnn.get(TokensAnnotation.class);
      Tree tree = sentenceAnn.get(TreeAnnotation.class);
      if (tree.children().length != 1) {
        throw new RuntimeException("Expected single root node, found " + tree);
      }
      tree = tree.firstChild();
      tree.indexSpans(0);
      TopTreebankNode root = new TopTreebankNode(jCas);
      root.setTreebankParse(tree.toString());
      // TODO: root.setTerminals(v)
      this.addTreebankNodeToIndexes(root, jCas, tree, tokenAnns);

      // get the dependencies
      SemanticGraph dependencies = sentenceAnn
          .get(CollapsedCCProcessedDependenciesAnnotation.class);

      // convert Stanford nodes to UIMA annotations
      List<Token> tokens = JCasUtil.selectCovered(jCas, Token.class, sentence);
      Map<IndexedWord, DependencyNode> stanfordToUima = new HashMap<IndexedWord, DependencyNode>();
      for (IndexedWord stanfordNode : dependencies.vertexSet()) {
        int indexBegin = stanfordNode.get(BeginIndexAnnotation.class);
        int indexEnd = stanfordNode.get(EndIndexAnnotation.class);
        int tokenBegin = tokens.get(indexBegin).getBegin();
        int tokenEnd = tokens.get(indexEnd - 1).getEnd();
        DependencyNode node;
        if (dependencies.getRoots().contains(stanfordNode)) {
          node = new TopDependencyNode(jCas, tokenBegin, tokenEnd);
        } else {
          node = new DependencyNode(jCas, tokenBegin, tokenEnd);
        }
        stanfordToUima.put(stanfordNode, node);
      }

      // create relation annotations for each Stanford dependency
      ArrayListMultimap<DependencyNode, DependencyRelation> headRelations = ArrayListMultimap
          .create();
      ArrayListMultimap<DependencyNode, DependencyRelation> childRelations = ArrayListMultimap
          .create();
      for (SemanticGraphEdge stanfordEdge : dependencies.edgeList()) {
        DependencyRelation relation = new DependencyRelation(jCas);
        DependencyNode head = stanfordToUima.get(stanfordEdge.getGovernor());
        DependencyNode child = stanfordToUima.get(stanfordEdge.getDependent());
        String relationType = stanfordEdge.getRelation().toString();
        if (head == null || child == null || relationType == null) {
          throw new RuntimeException(String.format(
              "null elements not allowed in relation:\nrelation=%s\nchild=%s\nhead=%s\n",
              relation,
              child,
              head));
        }
        relation.setHead(head);
        relation.setChild(child);
        relation.setRelation(relationType);
        relation.addToIndexes();
        headRelations.put(child, relation);
        childRelations.put(head, relation);
      }

      // set the relations for each node annotation
      for (DependencyNode node : stanfordToUima.values()) {
        node.setHeadRelations(UIMAUtil.toFSArray(jCas, headRelations.get(node)));
        node.setChildRelations(UIMAUtil.toFSArray(jCas, childRelations.get(node)));
        node.addToIndexes();
      }
    }

    // add mentions for all entities identified by the coreference system
    CorefGraph corefGraph = new CorefGraph(document.get(CorefGraphAnnotation.class));
    Map<CoreMap, NamedEntityMention> stanfordToUimaNE = new HashMap<CoreMap, NamedEntityMention>();
    for (CoreMap tokenMap : corefGraph.getMentions(document)) {
      NamedEntityMention mention = null;

      // figure out the character span of the token
      int begin = tokenMap.get(CharacterOffsetBeginAnnotation.class);
      int end = tokenMap.get(CharacterOffsetEndAnnotation.class);

      // if a named entity already contains the token, use that
      Token token = new Token(jCas, begin, end);
      mention = AnnotationRetrieval.getContainingAnnotation(jCas, token, NamedEntityMention.class);

      // otherwise, create a new named entity mention
      if (mention == null) {
        for (TreebankNode node : JCasUtil.selectCovered(jCas, TreebankNode.class, token)) {
          // if the token is a PRP, use that
          if (node.getNodeType().startsWith("PRP")) {
            begin = node.getBegin();
            end = node.getEnd();
            break;
          }
          // if the token's parent is an NP, use that
          TreebankNode parent = node.getParent();
          if (node.getLeaf() && parent != null && parent.getNodeType().equals("NP")) {
            begin = parent.getBegin();
            end = parent.getEnd();
            break;
          }
        }
        // create the named entity mention (defaulting to the same span as the token)
        mention = new NamedEntityMention(jCas, begin, end);
        mention.addToIndexes();
      }

      // update the token -> mention mapping
      stanfordToUimaNE.put(tokenMap, mention);
    }

    // link mentions into their entities
    List<NamedEntity> entities = new ArrayList<NamedEntity>();
    for (Set<CoreMap> tokenMaps : corefGraph.getEntities(document)) {

      // sort mentions by document order
      List<CoreMap> tokenMapsList = new ArrayList<CoreMap>(tokenMaps);
      Collections.sort(tokenMapsList, new Comparator<CoreMap>() {
        @Override
        public int compare(CoreMap o1, CoreMap o2) {
          int begin1 = o1.get(CharacterOffsetBeginAnnotation.class);
          int begin2 = o2.get(CharacterOffsetBeginAnnotation.class);
          return begin1 - begin2;
        }
      });

      // create mentions and add them to entity
      NamedEntity entity = new NamedEntity(jCas);
      entity.setMentions(new FSArray(jCas, tokenMapsList.size()));
      int index = 0;
      for (CoreMap tokenMap : tokenMapsList) {
        NamedEntityMention mention = stanfordToUimaNE.get(tokenMap);
        mention.setMentionedEntity(entity);
        entity.setMentions(index, mention);
        index += 1;
      }
      entities.add(entity);
    }

    // add singleton entities for any named entities not picked up by coreference system
    for (NamedEntityMention mention : JCasUtil.iterate(jCas, NamedEntityMention.class)) {
      if (mention.getMentionedEntity() == null) {
        NamedEntity entity = new NamedEntity(jCas);
        entity.setMentions(new FSArray(jCas, 1));
        entity.setMentions(0, mention);
        mention.setMentionedEntity(entity);
        entity.getMentions();
        entities.add(entity);
      }
    }

    // sort entities by document order
    Collections.sort(entities, new Comparator<NamedEntity>() {
      @Override
      public int compare(NamedEntity o1, NamedEntity o2) {
        return getFirstBegin(o1) - getFirstBegin(o2);
      }

      private int getFirstBegin(NamedEntity entity) {
        int min = Integer.MAX_VALUE;
        for (NamedEntityMention mention : JCasUtil.iterate(
            entity.getMentions(),
            NamedEntityMention.class)) {
          if (mention.getBegin() < min) {
            min = mention.getBegin();
          }
        }
        return min;
      }
    });

    // add entities to document
    for (NamedEntity entity : entities) {
      entity.addToIndexes();
    }

  }

  private FSArray addTreebankNodeChildrenToIndexes(
      TreebankNode parent,
      JCas jCas,
      List<CoreLabel> tokenAnns,
      Tree tree) {
    Tree[] childTrees = tree.children();

    // collect all children (except leaves, which are just the words - POS tags are pre-terminals in
    // a Stanford tree)
    List<TreebankNode> childNodes = new ArrayList<TreebankNode>();
    for (Tree child : childTrees) {
      if (!child.isLeaf()) {

        // set node attributes and add children (mutual recursion)
        TreebankNode node = new TreebankNode(jCas);
        node.setParent(parent);
        this.addTreebankNodeToIndexes(node, jCas, child, tokenAnns);
        childNodes.add(node);
      }
    }

    // convert the child list into an FSArray
    FSArray childNodeArray = new FSArray(jCas, childNodes.size());
    for (int i = 0; i < childNodes.size(); ++i) {
      childNodeArray.set(i, childNodes.get(i));
    }
    return childNodeArray;
  }

  private void addTreebankNodeToIndexes(
      TreebankNode node,
      JCas jCas,
      Tree tree,
      List<CoreLabel> tokenAnns) {
    // figure out begin and end character offsets
    CoreMap label = (CoreMap) tree.label();
    CoreMap beginToken = tokenAnns.get(label.get(BeginIndexAnnotation.class));
    CoreMap endToken = tokenAnns.get(label.get(EndIndexAnnotation.class) - 1);
    int nodeBegin = beginToken.get(CharacterOffsetBeginAnnotation.class);
    int nodeEnd = endToken.get(CharacterOffsetEndAnnotation.class);

    // set span, node type, children (mutual recursion), and add it to the JCas
    node.setBegin(nodeBegin);
    node.setEnd(nodeEnd);
    node.setNodeType(tree.value());
    node.setChildren(this.addTreebankNodeChildrenToIndexes(node, jCas, tokenAnns, tree));
    node.setLeaf(node.getChildren().size() == 0);
    node.addToIndexes();
  }
}

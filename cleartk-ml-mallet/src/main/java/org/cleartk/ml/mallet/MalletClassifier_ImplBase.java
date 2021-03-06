/** 
 * Copyright (c) 2007-2008, Regents of the University of Colorado 
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer. 
 * Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution. 
 * Neither the name of the University of Colorado at Boulder nor the names of its contributors may be used to endorse or promote products derived from this software without specific prior written permission. 
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE. 
 */
package org.cleartk.ml.mallet;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.cleartk.ml.CleartkProcessingException;
import org.cleartk.ml.Feature;
import org.cleartk.ml.encoder.CleartkEncoderException;
import org.cleartk.ml.encoder.features.FeaturesEncoder;
import org.cleartk.ml.encoder.features.NameNumber;
import org.cleartk.ml.encoder.outcome.OutcomeEncoder;
import org.cleartk.ml.jar.Classifier_ImplBase;

import cc.mallet.classify.Classification;
import cc.mallet.classify.Classifier;
import cc.mallet.types.Alphabet;
import cc.mallet.types.FeatureVector;
import cc.mallet.types.Instance;
import cc.mallet.types.Labeling;

import com.google.common.collect.Maps;

/**
 * <br>
 * Copyright (c) 2007-2008, Regents of the University of Colorado <br>
 * All rights reserved.
 * 
 * 
 * @author Philip Ogren
 * 
 * 
 */
public abstract class MalletClassifier_ImplBase<OUTCOME_TYPE> extends
    Classifier_ImplBase<List<NameNumber>, OUTCOME_TYPE, String> {

  protected Classifier classifier;

  Alphabet alphabet;

  public MalletClassifier_ImplBase(
      FeaturesEncoder<List<NameNumber>> featuresEncoder,
      OutcomeEncoder<OUTCOME_TYPE, String> outcomeEncoder,
      Classifier classifier) {
    super(featuresEncoder, outcomeEncoder);
    this.classifier = classifier;
    this.alphabet = classifier.getAlphabet();
  }

  public OUTCOME_TYPE classify(List<Feature> features) throws CleartkProcessingException {
    Classification classification = classifier.classify(toInstance(features));
    String returnValue = classification.getLabeling().getBestLabel().toString();
    return outcomeEncoder.decode(returnValue);
  }

  @Override
  public Map<OUTCOME_TYPE, Double> score(List<Feature> features) throws CleartkProcessingException {
    Classification classification = classifier.classify(toInstance(features));
    Labeling labeling = classification.getLabeling();

    Map<OUTCOME_TYPE, Double> returnValues = Maps.newHashMap();
    for (int i = 0; i < labeling.numLocations(); i++) {
      String label = labeling.getLabelAtRank(i).toString();
      OUTCOME_TYPE outcome = outcomeEncoder.decode(label);
      double score = labeling.getValueAtRank(i);
      returnValues.put(outcome, score);
    }
    return returnValues;
  }

  public Instance[] toInstances(List<List<Feature>> features) throws CleartkEncoderException {

    Instance[] instances = new Instance[features.size()];
    for (int i = 0; i < features.size(); i++) {
      instances[i] = toInstance(features.get(i));
    }
    return instances;
  }

  public Instance toInstance(List<Feature> features) throws CleartkEncoderException {
    List<NameNumber> nameNumbers = featuresEncoder.encodeAll(features);

    Iterator<NameNumber> nameNumberIterator = nameNumbers.iterator();
    while (nameNumberIterator.hasNext()) {
      NameNumber nameNumber = nameNumberIterator.next();
      if (!alphabet.contains(nameNumber.name))
        nameNumberIterator.remove();
    }

    String[] keys = new String[nameNumbers.size()];
    double[] values = new double[nameNumbers.size()];

    for (int i = 0; i < nameNumbers.size(); i++) {
      NameNumber nameNumber = nameNumbers.get(i);
      keys[i] = nameNumber.name;
      values[i] = nameNumber.number.doubleValue();
    }

    int[] keyIndices = FeatureVector.getObjectIndices(keys, alphabet, true);
    FeatureVector fv = new FeatureVector(alphabet, keyIndices, values);

    Instance instance = new Instance(fv, null, null, null);
    return instance;
  }
}

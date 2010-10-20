package edu.stanford.nlp.mt.tune.optimizers;

import java.util.ArrayList;
import java.util.List;

import edu.stanford.nlp.classify.BadLicenseMIRAWeightUpdater;
import edu.stanford.nlp.classify.MIRAWeightUpdater;
import edu.stanford.nlp.classify.WeightUpdater;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.ScoredFeaturizedTranslation;
import edu.stanford.nlp.mt.tune.HillClimbingMultiTranslationMetricMax;
import edu.stanford.nlp.mt.tune.MERT;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;

/**
 * 
 * @author Daniel Cer
 */
public class MIRAOptimizer extends AbstractNBestOptimizer {
  final double weightsConvergenceTol = 1e-6;
  final double C;
  public static final double DEFAULT_C = 100;
  
  public MIRAOptimizer(MERT mert) {
    super(mert);
    this.C = DEFAULT_C;
  }
  
  public MIRAOptimizer(MERT mert, double C) {
    super(mert);
    this.C = C;                                          
  }
  
  public MIRAOptimizer(MERT mert, String... args) {
    super(mert);
    double C;
    if (args.length == 0) {
      C = DEFAULT_C;
    } else {
      C = Double.parseDouble(args[0]);
    }
    if (C == 0) {
      C = Double.POSITIVE_INFINITY;
    }
    this.C = C;
  }

  @SuppressWarnings("unchecked")
  @Override
  public Counter<String> optimize(Counter<String> initialWts) {
    System.err.printf("MIRA optimization with C: %e\n", C);
    List<ScoredFeaturizedTranslation<IString, String>> target = (new HillClimbingMultiTranslationMetricMax<IString, String>(emetric)).maximize(nbest);
    
    double[][] lossMatrix = OptimizerUtils.calcDeltaMetric(nbest, target, emetric);
    
    // scale lossMatrix entries by data set size
    for (int i = 0; i < lossMatrix.length; i++) {
      for (int j = 0; j < lossMatrix[i].length; j++) {
        lossMatrix[i][j] *= lossMatrix.length;
      }
    }
    
    ClassicCounter<String> newWeights = new ClassicCounter<String>(initialWts);
    Counter<String> weightsLastIter;
    
    // WeightUpdater<String, String> weightUpdater = new MIRAWeightUpdater<String, String>(C);
    WeightUpdater<String, String> weightUpdater = new BadLicenseMIRAWeightUpdater<String, String>();
    
    for (int epoch = 0; ; epoch++) {
      System.err.printf("MIRA epochs: %d\n", epoch);      
      weightsLastIter = new ClassicCounter<String>(newWeights);
      for (int i = 0; i < nbest.nbestLists().size(); i++) {
        List<ScoredFeaturizedTranslation<IString, String>> nbestlist = nbest.nbestLists().get(i);
        
        List<Counter<String>> targetVectors = new ArrayList<Counter<String>>(nbestlist.size());        
        List<Counter<String>> guessedVectors = new ArrayList<Counter<String>>(nbestlist.size());
        
        
        for (ScoredFeaturizedTranslation<IString, String> trans : nbestlist) {
           guessedVectors.add(OptimizerUtils.featureValueCollectionToCounter(trans.features));  
        }
        
        Counter<String> targetVector = OptimizerUtils.featureValueCollectionToCounter(target.get(i).features);
        for (int j = 0; j < guessedVectors.size(); j++) {
          targetVectors.add(targetVector);
        }
                
        ClassicCounter<String> weightDiff = weightUpdater.getUpdate(newWeights, targetVectors.toArray(new ClassicCounter[0]), guessedVectors.toArray(new ClassicCounter[0]), 
            lossMatrix[i], new String[0], nbest.nbestLists().size());        
        Counters.addInPlace(newWeights, weightDiff);
      }
      
      double weightsDiff = Counters.L1Norm(Counters.diff(newWeights, weightsLastIter));
      System.err.printf("Eval Score: %e Weights diff: %e\n", MERT.evalAtPoint(nbest, newWeights, emetric), weightsDiff);
      if (weightsDiff < weightsConvergenceTol) {
        System.err.printf("Coverged wt delta: %e < tol: %e\n", weightsDiff, weightsConvergenceTol);
        break;
      }
    } 
    
    
    return newWeights;
  }

}

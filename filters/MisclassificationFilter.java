/*
 *    This program is free software; you can redistribute it and/or modify
 *    it under the terms of the GNU General Public License as published by
 *    the Free Software Foundation; either version 2 of the License, or
 *    (at your option) any later version.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU General Public License for more details.
 *
 *    You should have received a copy of the GNU General Public License
 *    along with this program; if not, write to the Free Software
 *    Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 */


/*
 *    MisclassificationFilter.java
 *    Copyright (C) 2002 Richard Kirkby
 *
 */

package weka.filters;

import weka.classifiers.Classifier;
import weka.core.*;
import java.util.Enumeration;
import java.util.Vector;

/** 
 * A filter that removes instances which are incorrectly classified. Useful for removing outliers. <p>
 *
 * Valid filter-specific options are: <p>
 *
 * -W classifier string <br>
 * Full class name of classifier to use, followed by scheme options. (required)<p>
 * 
 * -C class index <br>
 * Attribute on which misclassifications are based. If < 0 will use any current set class or default
 * to the last attribute.
 *
 * -F number of folds <br>
 * The number of folds to use for cross-validation cleansing. (<2 = no cross-validation - default)<p> 
 *
 * -T threshold <br>
 * Threshold for the max error when predicting numeric class. (Value should be >= 0, default = 0.1)<p>
 *
 * -I max iterations <br>
 * The maximum number of cleansing iterations to perform. (<1 = until fully cleansed - default)<p>
 *
 * -V <br>
 * Invert the match so that correctly classified instances are discarded.<p>
 *
 * @author Richard Kirkby (rkirkby@cs.waikato.ac.nz)
 * @version $Revision: 1.1 $
 */
public class MisclassificationFilter extends Filter implements OptionHandler {

  /** The classifier used to do the cleansing */
  protected Classifier m_cleansingClassifier = new weka.classifiers.rules.ZeroR();

  /** The attribute to treat as the class for purposes of cleansing. */
  protected int m_classIndex = -1;

  /** The number of cross validation folds to perform (<2 = no cross validation)  */
  protected int m_numOfCrossValidationFolds = 0;
  
  /** The maximum number of cleansing iterations to perform (<1 = until fully cleansed)  */
  protected int m_numOfCleansingIterations = 0;

  /** The threshold for deciding when a numeric value is correctly classified */
  protected double m_numericClassifyThreshold = 0.1;

  /** Whether to invert the match so the correctly classified instances are discarded */
  protected boolean m_invertMatching = false;

  /**
   * Sets the format of the input instances.
   *
   * @param instanceInfo an Instances object containing the input instance
   * structure (any instances contained in the object are ignored - only the
   * structure is required).
   * @return true if the outputFormat may be collected immediately
   * @exception Exception if the inputFormat can't be set successfully 
   */ 
  public boolean setInputFormat(Instances instanceInfo) throws Exception {
    
    super.setInputFormat(instanceInfo);
    setOutputFormat(instanceInfo);
    return true;
  }

  /**
   *
   */
  private Instances cleanseTrain(Instances data) throws Exception {
    
    Instance inst;
    Instances buildSet = new Instances(data);  
    Instances temp = new Instances(data, data.numInstances());
    Instances inverseSet = new Instances(data, data.numInstances()); 
    int count = 0;
    double ans;
    int iterations = 0;
    int classIndex = m_classIndex;
    if (classIndex < 0) classIndex = data.numAttributes()-1;

    // loop until perfect
    while(count != buildSet.numInstances()) {
      
      // check if hit maximum number of iterations
      iterations++;
      if (m_numOfCleansingIterations > 0 && iterations > m_numOfCleansingIterations) break;

      // build classifier
      count = buildSet.numInstances();
      buildSet.setClassIndex(classIndex);
      m_cleansingClassifier.buildClassifier(buildSet);

      temp = new Instances(buildSet, buildSet.numInstances());
      
      //if (m_invertMatching) inverseSet = new Instances(buildSet, buildSet.numInstances());

      // test on training data
      for (int i = 0; i < buildSet.numInstances(); i++) {
	inst = buildSet.instance(i);
	ans = m_cleansingClassifier.classifyInstance(inst);
	if (buildSet.classAttribute().isNumeric()) {
	  if (ans >= inst.classValue() - m_numericClassifyThreshold &&
	      ans <= inst.classValue() + m_numericClassifyThreshold) {
	    temp.add(inst);
	  } else if (m_invertMatching) {
	    inverseSet.add(inst);
	  }
	}
	else { //class is nominal
	  if (ans == inst.classValue()) {
	    temp.add(inst);
	  } else if (m_invertMatching) {
	    inverseSet.add(inst);
	  }
	}
      }
      buildSet = temp;
    }

    if (m_invertMatching) {
      inverseSet.setClassIndex(data.classIndex());
      return inverseSet;
    }
    else {
      buildSet.setClassIndex(data.classIndex());
      return buildSet;
    }
  }

  private Instances cleanseCross(Instances data) throws Exception {
    
    Instance inst;
    Instances crossSet = new Instances(data);
    Instances temp = new Instances(data, data.numInstances());    
    Instances inverseSet = new Instances(data, data.numInstances()); 
    int count = 0;
    double ans;
    int iterations = 0;
    int classIndex = m_classIndex;
    if (classIndex < 0) classIndex = data.numAttributes()-1;

    // loop until perfect
    while (count != crossSet.numInstances() && 
	   crossSet.numInstances() >= m_numOfCrossValidationFolds) {

      count = crossSet.numInstances();
      
      // check if hit maximum number of iterations
      iterations++;
      if (m_numOfCleansingIterations > 0 && iterations > m_numOfCleansingIterations) break;

      crossSet.setClassIndex(classIndex);

      if (crossSet.classAttribute().isNominal()) {
	crossSet.stratify(m_numOfCrossValidationFolds);
      }
      // do the folds
      temp = new Instances(crossSet, crossSet.numInstances());
      //if (m_invertMatching) inverseSet = new Instances(crossSet, crossSet.numInstances());
      for (int fold = 0; fold < m_numOfCrossValidationFolds; fold++) {
	Instances train = crossSet.trainCV(m_numOfCrossValidationFolds, fold);
	m_cleansingClassifier.buildClassifier(train);
	Instances test = crossSet.testCV(m_numOfCrossValidationFolds, fold);
	//now test
	for (int i = 0; i < test.numInstances(); i++) {
	  inst = test.instance(i);
	  ans = m_cleansingClassifier.classifyInstance(inst);
	  if (crossSet.classAttribute().isNumeric()) {
	    if (ans >= inst.classValue() - m_numericClassifyThreshold &&
		ans <= inst.classValue() + m_numericClassifyThreshold) {
	      temp.add(inst);
	    } else if (m_invertMatching) {
	      inverseSet.add(inst);
	    }
	  }
	  else { //class is nominal
	    if (ans == inst.classValue()) {
	      temp.add(inst);
	    } else if (m_invertMatching) {
	      inverseSet.add(inst);
	    }
	  }
	}
      }
      crossSet = temp;
    }

    if (m_invertMatching) {
      inverseSet.setClassIndex(data.classIndex());
      return inverseSet;
    }
    else {
      crossSet.setClassIndex(data.classIndex());
      return crossSet;
    }

  }
  
  /**
   * Signify that this batch of input to the filter is finished.
   *
   * @return true if there are instances pending output
   * @exception IllegalStateException if no input structure has been defined 
   */  
  public boolean batchFinished() throws Exception {

    if (getInputFormat() == null) {
      throw new IllegalStateException("No input instance format defined");
    }

    Instances filtered;

    if (m_numOfCrossValidationFolds < 2) {
      filtered = cleanseTrain(getInputFormat());
    } else {
      filtered = cleanseCross(getInputFormat());
    }

    for (int i=0; i<filtered.numInstances(); i++) {
      push(filtered.instance(i));
    }

    return (numPendingOutput() != 0);
  }

  /**
   * Returns an enumeration describing the available options.
   *
   * @return an enumeration of all the available options.
   */
  public Enumeration listOptions() {
    
    Vector newVector = new Vector(6);
    
    newVector.addElement(new Option(
	      "\tFull class name of classifier to use, followed\n"
	      + "\tby scheme options. (required)\n"
	      + "\teg: \"weka.classifiers.bayes.NaiveBayes -D\"",
	      "W", 1, "-W <classifier specification>"));
    newVector.addElement(new Option(
	      "\tAttribute on which misclassifications are based.\n"
	      + "\tIf < 0 will use any current set class or default to the last attribute.",
	      "C", 1, "-C <class index>"));
    newVector.addElement(new Option(
	      "\tThe number of folds to use for cross-validation cleansing.\n"
	      +"\t(<2 = no cross-validation - default).",
	      "F", 1, "-F <number of folds>"));
    newVector.addElement(new Option(
	      "\tThreshold for the max error when predicting numeric class.\n"
	      +"\t(Value should be >= 0, default = 0.1).",
	      "T", 1, "-T <threshold>"));
    newVector.addElement(new Option(
	      "\tThe maximum number of cleansing iterations to perform.\n"
	      +"\t(<1 = until fully cleansed - default)",
	      "I", 1,"-I"));
    newVector.addElement(new Option(
	      "\tInvert the match so that correctly classified instances are discarded.\n",
	      "V", 0,"-V"));
    
    return newVector.elements();
  }


  /**
   * Parses the options for this object. Valid options are: <p>
   *
   * -W classifier string <br>
   * Full class name of classifier to use, followed by scheme options. (required)<p>
   * 
   * -C class index <br>
   * Attribute on which misclassifications are based. If < 0 will use any current set class or default
   * to the last attribute.
   *
   * -F number of folds <br>
   * The number of folds to use for cross-validation cleansing. (<2 = no cross-validation - default)<p> 
   *
   * -T threshold <br>
   * Threshold for the max error when predicting numeric class. (Value should be >= 0, default = 0.1)<p>
   *
   * -I max iterations <br>
   * The maximum number of cleansing iterations to perform. (<1 = until fully cleansed - default)<p>
   *
   * -V <br>
   * Invert the match so that correctly classified instances are discarded.<p>
   *
   * @param options the list of options as an array of strings
   * @exception Exception if an option is not supported
   */
  public void setOptions(String[] options) throws Exception {

    String classifierString = Utils.getOption('W', options);
    if (classifierString.length() == 0) {
      throw new Exception("A classifier must be specified"
			  + " with the -W option.");
    }
    String[] classifierSpec = Utils.splitOptions(classifierString);
    if (classifierSpec.length == 0) {
      throw new Exception("Invalid classifier specification string");
    }
    String classifierName = classifierSpec[0];
    classifierSpec[0] = "";
    setClassifier(Classifier.forName(classifierName, classifierSpec));

    String cString = Utils.getOption('C', options);
    if (cString.length() != 0) {
      setClassIndex((new Double(cString)).intValue());
    } else {
      setClassIndex(-1);
    }

    String fString = Utils.getOption('F', options);
    if (fString.length() != 0) {
      setNumFolds((new Double(fString)).intValue());
    } else {
      setNumFolds(0);
    }

    String tString = Utils.getOption('T', options);
    if (tString.length() != 0) {
      setThreshold((new Double(tString)).doubleValue());
    } else {
      setThreshold(0.1);
    }

    String iString = Utils.getOption('I', options);
    if (tString.length() != 0) {
      setMaxIterations((new Double(tString)).intValue());
    } else {
      setMaxIterations(0);
    }
    
    if (Utils.getFlag('V', options)) {
      setInvert(true);
    } else {
      setInvert(false);
    }
        
    Utils.checkForRemainingOptions(options);

  }

  /**
   * Gets the current settings of the filter.
   *
   * @return an array of strings suitable for passing to setOptions
   */
  public String [] getOptions() {

    String [] options = new String [15];
    int current = 0;

    options[current++] = "-W"; options[current++] = "" + getClassifierSpec();
    options[current++] = "-C"; options[current++] = "" + getClassIndex();
    options[current++] = "-F"; options[current++] = "" + getNumFolds();
    options[current++] = "-T"; options[current++] = "" + getThreshold();
    options[current++] = "-I"; options[current++] = "" + getMaxIterations();
    if (getInvert()) {
      options[current++] = "-V";
    }
    
    while (current < options.length) {
      options[current++] = "";
    }
    return options;
  }

  /**
   * Returns a string describing this filter
   *
   * @return a description of the filter suitable for
   * displaying in the explorer/experimenter gui
   */
  public String globalInfo() {

    return "A filter that removes instances which are incorrectly classified. Useful for removing outliers.";
  }

  /**
   * Returns the tip text for this property
   *
   * @return tip text for this property suitable for
   * displaying in the explorer/experimenter gui
   */
  public String classifierTipText() {

    return "The classifier upon which to base the misclassifications.";
  }

  /**
   * Sets the classifier to classify instances with.
   *
   * @param classifier The classifier to be used (with its options set).
   */
  public void setClassifier(Classifier classifier) {

    m_cleansingClassifier = classifier;
  }
  
  /**
   * Gets the classifier used by the filter.
   *
   * @return The classifier to be used.
   */
  public Classifier getClassifier() {

    return m_cleansingClassifier;
  }

  /**
   * Gets the classifier specification string, which contains the class name of
   * the classifier and any options to the classifier.
   *
   * @return the classifier string.
   */
  protected String getClassifierSpec() {
    
    Classifier c = getClassifier();
    if (c instanceof OptionHandler) {
      return c.getClass().getName() + " "
	+ Utils.joinOptions(((OptionHandler)c).getOptions());
    }
    return c.getClass().getName();
  }

  /**
   * Returns the tip text for this property
   *
   * @return tip text for this property suitable for
   * displaying in the explorer/experimenter gui
   */
  public String classIndexTipText() {

    return "Index of the class upon which to base the misclassifications. "
      + "If < 0 will use any current set class or default to the last attribute";
  }

  /**
   * Sets the attribute on which misclassifications are based. If < 0 will use any current set class or default
   * to the last attribute.
   *
   * @param classIndex the class index.
   */
  public void setClassIndex(int classIndex) {
    
    m_classIndex = classIndex;
  }

  /**
   * Gets the attribute on which misclassifications are based.
   *
   * @return the class index.
   */
  public int getClassIndex() {

    return m_classIndex;
  }

  /**
   * Returns the tip text for this property
   *
   * @return tip text for this property suitable for
   * displaying in the explorer/experimenter gui
   */
  public String numFoldsTipText() {

    return "The number of cross-validation folds to use. If < 2 then no cross-validation will be performed.";
  }

  /**
   * Sets the number of cross-validation folds to use
   * - < 2 means no cross-validation.
   *
   * @param numOfFolds the number of folds.
   */
  public void setNumFolds(int numOfFolds) {
    
    m_numOfCrossValidationFolds = numOfFolds;
  }

  /**
   * Gets the number of cross-validation folds used by the filter.
   *
   * @return the number of folds.
   */
  public int getNumFolds() {

    return m_numOfCrossValidationFolds;
  }

  /**
   * Returns the tip text for this property
   *
   * @return tip text for this property suitable for
   * displaying in the explorer/experimenter gui
   */
  public String thresholdTipText() {

    return "Threshold for the max allowable error when predicting a numeric class. Should be >= 0.";
  }

  /**
   * Sets the threshold for the max error when predicting a numeric class.
   * The value should be >= 0.
   *
   * @param threshold the numeric theshold.
   */
  public void setThreshold(double threshold) {
    
    m_numericClassifyThreshold = threshold;
  }

  /**
   * Gets the threshold for the max error when predicting a numeric class.
   *
   * @return the numeric threshold.
   */
  public double getThreshold() {

    return m_numericClassifyThreshold;
  }

  /**
   * Returns the tip text for this property
   *
   * @return tip text for this property suitable for
   * displaying in the explorer/experimenter gui
   */
  public String maxIterationsTipText() {

    return "The maximum number of iterations to perform. < 1 means filter will go until fully cleansed.";
  }

  /**
   * Sets the maximum number of cleansing iterations to perform
   * - < 1 means go until fully cleansed
   *
   * @param iterations the maximum number of iterations.
   */
  public void setMaxIterations(int iterations) {
    
    m_numOfCleansingIterations = iterations;
  }

  /**
   * Gets the maximum number of cleansing iterations performed
   *
   * @return the maximum number of iterations. 
   */
  public int getMaxIterations() {

    return m_numOfCleansingIterations;
  }

  /**
   * Returns the tip text for this property
   *
   * @return tip text for this property suitable for
   * displaying in the explorer/experimenter gui
   */
  public String invertTipText() {

    return "Whether or not to invert the selection. If true, correctly classified instances will be discarded.";
  }

  /**
   * Set whether selection is inverted.
   *
   * @param invert whether or not to invert selection.
   */
  public void setInvert(boolean invert) {
    
    m_invertMatching = invert;
  }

  /**
   * Get whether selection is inverted.
   *
   * @return whether or not selection is inverted.
   */
  public boolean getInvert() {
    
    return m_invertMatching;
  }

  /**
   * Main method for testing this class.
   *
   * @param argv should contain arguments to the filter: use -h for help
   */
  public static void main(String [] argv) {

    try {
      if (Utils.getFlag('b', argv)) {
 	Filter.batchFilterFile(new MisclassificationFilter(), argv); 
      } else {
	Filter.filterFile(new MisclassificationFilter(), argv);
      }
    } catch (Exception ex) {
      System.out.println(ex.getMessage());
    }
  }
}

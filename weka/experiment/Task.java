/*
 *    Task.java
 *    Copyright (C) 2000 Mark Hall
 *
 */


package weka.experiment;

import java.io.Serializable;

/**
 * Interface to something that can be remotely executed as a task.
 *
 * @author Mark Hall (mhall@cs.waikato.ac.nz)
 * @version $Revision: 1.4 $
 */
public interface Task extends Serializable {
  
  /**
   * Execute this task.
   * @return a TaskStatusInfo object holding info and result from executing
   * this task
   */
  public TaskStatusInfo execute();

}

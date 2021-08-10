package twita.whipsaw.api

/**
  * Contains classes that define the Engine for running Whipsaw Workloads.  The engine has been
  * defined in the abstract here to contain a [[Director]] that sits on top of the management hierarchy
  * and is responsible for delgating work out to [[Manager]]s to be done.  For their part, [[Manager]]s will
  * then figure out what needs to be done from a Workload scheduling and processing perspective and will then
  * delegate that work down to [[Worker]] instances.  These operations are all defined only as abstractions,
  * and it is up to the applications to pick a particular implementation of this abstraction to actually run
  * the Whipsaw system.
  */
package object engine

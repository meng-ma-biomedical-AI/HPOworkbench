package org.monarchinitiative.hpoworkbench.controller;


import org.monarchinitiative.phenol.ontology.data.Term;

/** This class is used to display {@link Term} objects in a tree view. The TreeView uses to
 * toString method of the objects to create the label, and the GenericTerm's toString object unfortunately
 * provides a very long string, whereas we would like  to show just the label.
 * @author <a href="mailto:peter.robinson@jax.org">Peter Robinson</a>
 */
class GenericTermWrapper {
    final Term term;
    GenericTermWrapper(Term trm) {
        this.term=trm;
    }
    @Override
    public String toString() {
        return this.term.getName();
    }
}

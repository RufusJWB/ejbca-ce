/*
 * IllegalQueryException.java
 *
 * Created on den 1 april 2002, 12:37
 */

package se.anatom.ejbca.util.query;

/**
 * An exception thrown if Query strucure is illegal.
 *
 * @author  Philip Vendil
 */
public class IllegalQueryException extends java.lang.Exception {
    
    /**
     * Creates a new instance of <code>IllegalQueryException/code> without detail message.
     */
    public IllegalQueryException() {
      super();  
    }
    
    
    /**
     * Constructs an instance of <code>IllegalQueryException</code> with the specified detail message.
     * @param msg the detail message.
     */
    public IllegalQueryException(String msg) {
        super(msg);
    }
}

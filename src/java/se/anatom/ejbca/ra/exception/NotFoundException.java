package se.anatom.ejbca.ra.exception;

import se.anatom.ejbca.exception.EjbcaException;


/**
 * Thrown when an objekt cannot be found in the database and the error is not critical so we want
 * to inform the client in a nice way.
 *
 * @version $Id: NotFoundException.java,v 1.2 2003-06-26 11:43:25 anatom Exp $
 */
public class NotFoundException extends EjbcaException {
    /**
     * Constructor used to create exception with an errormessage. Calls the same constructor in
     * baseclass <code>Exception</code>.
     *
     * @param message Human redable error message, can not be NULL.
     */
    public NotFoundException(String message) {
        super(message);
    }
}

package se.anatom.ejbca;

import se.anatom.ejbca.log.Admin;

import java.rmi.RemoteException;


/**
 * JobRunner session wraps around any class and is a general session bean that can be used to
 * launch a specified job.
 *
 * @version $Id: IJobRunnerSession.java,v 1.5 2003-06-26 11:43:16 anatom Exp $
 */
public interface IJobRunnerSession {
    /**
     * Runs the job
     *
     * @param admin administrator running the job
     *
     * @throws RemoteException error
     */
    public void run(Admin admin) throws RemoteException;
}

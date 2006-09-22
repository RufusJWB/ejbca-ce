/*************************************************************************
 *                                                                       *
 *  EJBCA: The OpenSource Certificate Authority                          *
 *                                                                       *
 *  This software is free software; you can redistribute it and/or       *
 *  modify it under the terms of the GNU Lesser General Public           *
 *  License as published by the Free Software Foundation; either         *
 *  version 2.1 of the License, or any later version.                    *
 *                                                                       *
 *  See terms of license at gnu.org.                                     *
 *                                                                       *
 *************************************************************************/
package org.ejbca.util;

import java.awt.print.PageFormat;
import java.awt.print.Paper;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.io.IOException;
import java.io.StringReader;

import javax.ejb.EJBException;
import javax.print.DocFlavor;
import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;

import org.apache.log4j.Logger;
import org.ejbca.core.model.hardtoken.profiles.SVGImageManipulator;
import org.ejbca.core.model.ra.UserDataVO;

/**
 * Class managing EJBCA print functionality, such as listing printers
 * and managing printjobs
 * 
 * @author Philip Vendil 2006 sep 20
 *
 * @version $Id: PrinterManager.java,v 1.2 2006-09-22 13:48:16 herrvendil Exp $
 */
public class PrinterManager {
	
	private static Logger log = Logger.getLogger(PrinterManager.class);

	private static transient PrintService currentService = null;	
	private static transient String currentPrinterName = null;
	private static transient String currentSVGTemplateName = null;
	private static transient SVGImageManipulator sVGImagemanipulator = null;
	
	/**
	 * Method that returns the names of all the available printers.
	 * @return
	 */
	
	public static String[] listPrinters(){
		PrintRequestAttributeSet pras = new HashPrintRequestAttributeSet();
		DocFlavor flavor = DocFlavor.BYTE_ARRAY.AUTOSENSE;
		PrintService printService[] =  PrintServiceLookup.lookupPrintServices(flavor, pras);
		String[] printerNames = new String[printService.length];
		for(int i=0;i<printService.length;i++){
			printerNames[i] = printService[i].getName();
		}						
		
		return printerNames;
	}


	
	/**
	 * Method performin the actual processing and
	 * printing of an SVG Template
	 * 
	 * Imporant the method is caching template data
	 * so to subsekvent calls with the samt svtTemplate name
	 * will result in the same SVG data being processed.
	 * Avoid the same svgTemplate name for two different
	 * SVG files
	 * 
	 * @param printerName the name of the printer to use
	 * @param svtTemplateName the filname of the SVG file.
	 * @param sVGData the actual SVG data.
	 * @param copies number of copies to print
	 * @param visualVaildity the visual validity use 0 if not used
	 * @param userDataVO the userdata
	 * @param pINs the PINS (or password)
	 * @param pUKs the PUKS, optional
	 * @param hardTokenSerialPrefix the prefix of the hardtoken, optional
	 * @param hardTokenSN, optional
	 * @param copyOfHardTokenSN, optional
	 * @throws PrinterException throws if any printer problems occur.
	 */
	public static void print(String printerName, String svtTemplateName, 
			String sVGData, int copies,
			int visualVaildity,  UserDataVO userDataVO, 
			String[] pINs, String[] pUKs, String hardTokenSerialPrefix,
			String hardTokenSN, String copyOfHardTokenSN) throws PrinterException{
		if(currentService == null 
		   || currentPrinterName == null 
		   || !printerName.equals(currentPrinterName)){
			PrintRequestAttributeSet pras = new HashPrintRequestAttributeSet();
			DocFlavor flavor = DocFlavor.BYTE_ARRAY.AUTOSENSE;
			PrintService printService[] =  PrintServiceLookup.lookupPrintServices(flavor, pras);
			int i = 0;
			String trimemdPrinterName = printerName.trim();
			while ( i<printService.length && !trimemdPrinterName.equalsIgnoreCase(printService[i].getName()) ){
				i++;
			}
			currentService = i<printService.length ? printService[i] : null;	
			currentPrinterName = printerName;
		}	
		try{
			if(currentSVGTemplateName == null || !currentSVGTemplateName.equals(svtTemplateName)){
				sVGImagemanipulator = new SVGImageManipulator(new StringReader(sVGData),visualVaildity,hardTokenSerialPrefix);
			}

			if(currentService != null){
				PrinterJob job = PrinterJob.getPrinterJob();

				job.setPrintService(currentService);
				PageFormat pf = job.defaultPage();	   	  
				Paper paper = new Paper();
				paper.setSize(pf.getWidth(), pf.getHeight());
				paper.setImageableArea(0.0,0.0,pf.getWidth(), pf.getHeight());				

				pf.setPaper(paper);	   	  
				job.setPrintable(sVGImagemanipulator.print(userDataVO,pINs,pUKs,hardTokenSN, copyOfHardTokenSN),pf);	   	  
                job.setCopies(copies);
				job.print();	   	  	   	  	   	  	   	 
			}else{
				throw new EJBException("Error, couldn't find the right printer");		  	   	
			}	
		}catch(IOException e){
			log.debug(e);
			throw new PrinterException("Error occured when processing the SVG data :" + e.getMessage());		
		}
	}
}

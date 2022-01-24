/*************************************************************************
 *                                                                       *
 *  EJBCA Community: The OpenSource Certificate Authority                *
 *                                                                       *
 *  This software is free software; you can redistribute it and/or       *
 *  modify it under the terms of the GNU Lesser General Public           *
 *  License as published by the Free Software Foundation; either         *
 *  version 2.1 of the License, or any later version.                    *
 *                                                                       *
 *  See terms of license at gnu.org.                                     *
 *                                                                       *
 *************************************************************************/
package org.ejbca.core.model.ra;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.cesecore.certificates.endentity.EndEntityInformation;
import org.cesecore.certificates.endentity.EndEntityType;
import org.cesecore.certificates.endentity.EndEntityTypes;
import org.cesecore.certificates.endentity.ExtendedInformation;
import org.cesecore.certificates.util.DNFieldExtractor;
import org.cesecore.certificates.util.DnComponents;
import org.cesecore.util.CertTools;
import org.ejbca.core.model.ra.raadmin.EndEntityProfile;
import org.ejbca.core.model.ra.raadmin.EndEntityProfileValidationException;
import org.ejbca.util.dn.DistinguishedName;

import javax.naming.InvalidNameException;
import javax.naming.ldap.Rdn;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** This class gives facilities to populate user data with default values from profile.
 *
 * @version $Id: EndEntityInformationFiller.java 34943 2020-04-29 12:12:05Z anatom $
 */
public class EndEntityInformationFiller {

    /** For log purpose. */
    private static final Logger log = Logger.getLogger(EndEntityInformationFiller.class.getName());
    private static final String SUBJECT_DN = "subject DN";
    private static final String SUBJECT_ALTERNATIVE_NAME = "subject alternative name";
    
    private static final Map<String, String> BC_STYLE_PARAMETERS;
    
    private static final String REPLACED_ESCAPABLE_CHARS = "Replaced_EndEntityInformationFiller_Dn_Chars";
    
    static {
        BC_STYLE_PARAMETERS = new HashMap<>();
        BC_STYLE_PARAMETERS.put("EMAILADDRESS", "E");
        BC_STYLE_PARAMETERS.put("EMAIL", "E");
        BC_STYLE_PARAMETERS.put("SERIALNUMBER", "SN");
        BC_STYLE_PARAMETERS.put("COMMONNAME", "CN");
    }

    /** This method fill user data with the default values from the specified profile.
     *
     * @param userData user data.
     * @param profile user associated profile.
     * @return update user.
     * @throws EndEntityProfileValidationException 
     */
    public static EndEntityInformation fillUserDataWithDefaultValues(final EndEntityInformation userData, final EndEntityProfile profile) throws EndEntityProfileValidationException {


    	if (StringUtils.isEmpty(userData.getUsername())) {
        	userData.setUsername(profile.getUsernameDefault());
        }
    	if (userData.getSendNotification() == false) {
    		if(StringUtils.isNotEmpty(profile.getValue(EndEntityProfile.SENDNOTIFICATION, 0))) {
    			final Boolean isSendNotification = Boolean.valueOf(profile.getValue(EndEntityProfile.SENDNOTIFICATION, 0));
    			userData.setSendNotification(isSendNotification.booleanValue());
    		}
        }
    	if (StringUtils.isEmpty(userData.getEmail())) {
			final String email = profile.getValue(EndEntityProfile.EMAIL, 0);
			if (StringUtils.isNotEmpty(email) && email.indexOf("@") > 0) {
				userData.setEmail(email);
			}
		}
        //Batch generation (clear text pwd storage) is only active when password 
        //is not empty so is not necessary to do something here
        if (StringUtils.isEmpty(userData.getPassword())) {
            // check if the password is autogenerated
        	if(!profile.useAutoGeneratedPasswd()) {
        		userData.setPassword(profile.getPredefinedPassword());
        	}
        }

        // Processing Subject DN values
        userData.setDN(mergeDnString(userData.getDN(), profile, SUBJECT_DN, userData.getEmail()));
        // Processing Subject Altname values
        userData.setSubjectAltName(mergeDnString(userData.getSubjectAltName(), profile, 
                                                SUBJECT_ALTERNATIVE_NAME, userData.getEmail()));
        if (userData.getType().getHexValue() == EndEntityTypes.INVALID.hexValue()) {
        	if (StringUtils.isNotEmpty(profile.getValue(EndEntityProfile.FIELDTYPE, 0))) {
        	    final int type = Integer.parseInt(profile.getValue(EndEntityProfile.FIELDTYPE, 0));
        		userData.setType(new EndEntityType(type));
        	}
        }
        
        if (profile.isCabfOrganizationIdentifierUsed()) {
            ExtendedInformation extInfo = userData.getExtendedInformation();
            if (extInfo == null) {
                extInfo = new ExtendedInformation();
                extInfo.setCabfOrganizationIdentifier(profile.getCabfOrganizationIdentifier());
                userData.setExtendedInformation(extInfo);
            } else if (StringUtils.isEmpty(extInfo.getCabfOrganizationIdentifier())) {
                extInfo.setCabfOrganizationIdentifier(profile.getCabfOrganizationIdentifier());
            }
        }
        
        return userData;
    }

    /**
     * This method merge subject DN with data from End entity profile. Kept as legacy.
     *
     * @param subjectDN   user Distinguished Name.
     * @param profile     user associated profile.
     * @param entityEmail entity email.
     * @return updated DN.
     */
    private static String mergeSubjectDnWithDefaultValues(String subjectDN, EndEntityProfile profile,
                                                          String entityEmail) {
        DistinguishedName profiledn;
        DistinguishedName userdn;
        if (StringUtils.isNotEmpty(subjectDN)) {
            try {
                userdn = new DistinguishedName(subjectDN);
            } catch (InvalidNameException ine) {
                log.debug(subjectDN, ine);
                throw new RuntimeException(ine);
            }
        } else {
            userdn = new DistinguishedName(Collections.emptyList());
        }
        final int numberofsubjectdnfields = profile.getSubjectDNFieldOrderLength();
        final List<Rdn> rdnList = new ArrayList<Rdn>(numberofsubjectdnfields);
        int[] fielddata = null;
        String value;
        //Build profile's DN
        for (int i = 0; i < numberofsubjectdnfields; i++) {
            fielddata = profile.getSubjectDNFieldsInOrder(i);
            value = profile.getValue(fielddata[EndEntityProfile.FIELDTYPE], fielddata[EndEntityProfile.NUMBER]);
            if (!StringUtils.isEmpty(value) && !StringUtils.isWhitespace(value)) {
                value = value.trim();
                addFieldValueToRdnList(rdnList, fielddata, value, DNFieldExtractor.TYPE_SUBJECTDN);
            }
        }
        // As the constructor taking a list of RDNs numbers them from behind, which is typically not what we want when mergin DNs from a profile
        // that has specified CN=User,OU=Org1,OU=Org2, we'll reverse them. This is of little importance when you only have one item of each component,
        // but when you have multiple, such as sevral OUs it becomes important. See DistingushedNameTest for more detailed tests of this behavios
        Collections.reverse(rdnList);
        profiledn = new DistinguishedName(rdnList);
        if (log.isDebugEnabled()) {
            log.debug("Profile DN to merge with subject DN: " + profiledn.toString());
        }

        Map<String, String> dnMap = new HashMap<String, String>();
        if (profile.getUse(DnComponents.DNEMAILADDRESS, 0)) {
            dnMap.put(DnComponents.DNEMAILADDRESS, entityEmail);
        }

        return CertTools.stringToBCDNString(profiledn.mergeDN(userdn, true, dnMap).toString());
    }

    /**
     * This method merge subject Alt name with data from End entity profile. Kept as legacy.
     *
     * @param subjectAltName user subject alt name.
     * @param profile        user associated profile.
     * @param entityEmail    entity email field
     * @return updated subject alt name
     */
    private static String mergeSubjectAltNameWithDefaultValues(String subjectAltName, EndEntityProfile profile, String entityEmail) {
        DistinguishedName profileAltName;
        DistinguishedName userAltName;
        try {
            if(subjectAltName==null) {
                subjectAltName = "";
            }
            userAltName = new DistinguishedName(subjectAltName);
        } catch (InvalidNameException ine) {
            log.debug(subjectAltName,ine);
            throw new RuntimeException(ine);
        }
        int numberofsubjectAltNamefields = profile.getSubjectAltNameFieldOrderLength();
        List<Rdn> rdnList = new ArrayList<Rdn>(numberofsubjectAltNamefields);
        int[] fielddata = null;
        String value;
        //Build profile's Alt Name
        for (int i = 0; i < numberofsubjectAltNamefields; i++) {
            fielddata = profile.getSubjectAltNameFieldsInOrder(i);
            value = profile.getValue(fielddata[EndEntityProfile.FIELDTYPE], fielddata[EndEntityProfile.NUMBER]);
            if (value != null) {
                value = value.trim();
                if (!value.equals("")) {
                    addFieldValueToRdnList(rdnList, fielddata, value, DNFieldExtractor.TYPE_SUBJECTALTNAME);
                }
            }
        }
        profileAltName = new DistinguishedName(rdnList);

        Map<String, String> dnMap = new HashMap<String, String>();
        if (profile.getUse(DnComponents.RFC822NAME, 0)) {
            dnMap.put(DnComponents.RFC822NAME, entityEmail);
        }

        return  profileAltName.mergeDN(userAltName, true, dnMap).toString();
    }

    /**
     *  Adds a value to rdnList.
     * @param rdnList rdnList to be updated
     * @param fielddata a field data, what will be added to rdnList
     * @param value field value to be added to rdnList
     * @param dnFieldExtractorType subject DNFieldExtractor.TYPE_SUBJECTALTNAME or subject DNFieldExtractor.TYPE_SUBJECTDN
     */
    private static void addFieldValueToRdnList(List<Rdn> rdnList, final int[] fielddata, final String value, final int dnFieldExtractorType) {
        String parameter = DNFieldExtractor.getFieldComponent(
                DnComponents.profileIdToDnId(fielddata[EndEntityProfile.FIELDTYPE]),
                dnFieldExtractorType);
        try {
            parameter = StringUtils.replace(parameter, "=", "");
            rdnList.add(new Rdn(parameter, value));
        } catch (InvalidNameException ine) {
            log.debug("InvalidNameException while creating new Rdn with parameter " + parameter + " and value " + value, ine);
            throw new RuntimeException(ine);
        }
    }


    /**
     * Gets the first Common Name value from subjectDn and sets this value to all dns's with "use from CN" checked
     *
     * @param endEntityProfile EEP selected for end entity
     * @param subjectDn        provided subjectDn
     * @return String with comma separated DNSNames
     */
    public static String copyDnsNameValueFromCn(final EndEntityProfile endEntityProfile, String subjectDn) {
        if (endEntityProfile == null) {
            return StringUtils.EMPTY;
        }
        StringBuilder dnses = new StringBuilder();
        String commonName = CertTools.getCommonNameFromSubjectDn(subjectDn);
        if (StringUtils.isNotEmpty(commonName)) {
            int[] field = null;
            final int numberOfFields = endEntityProfile.getSubjectAltNameFieldOrderLength();
            for (int i = 0; i < numberOfFields; i++) {
                field = endEntityProfile.getSubjectAltNameFieldsInOrder(i);
                final boolean isDnsField = EndEntityProfile.isFieldOfType(field[EndEntityProfile.FIELDTYPE], DnComponents.DNSNAME);
                final boolean isCopy = endEntityProfile.getCopy(field[EndEntityProfile.FIELDTYPE], field[EndEntityProfile.NUMBER]);
                if (isDnsField && isCopy) {
                    if (dnses.length() > 0) {
                        dnses.append(", ");
                    }
                    int dnId = DnComponents.profileIdToDnId(field[EndEntityProfile.FIELDTYPE]);
                    String nameValueDnPart = DNFieldExtractor.getFieldComponent(dnId, DNFieldExtractor.TYPE_SUBJECTALTNAME) + commonName;
                    dnses.append(nameValueDnPart);
                }
            }
        }
        return dnses.toString();
    }
    
    private static String mergeDnString(String userDnString, final EndEntityProfile profile, 
                                final String entityType, final String entityEmail) throws EndEntityProfileValidationException {
        
        if (userDnString==null) {
            userDnString = "";
        } else {
            userDnString = userDnString.trim();
        }
        
        
        // append end entity email to user dn or san to achieve the same functionality
        if(!StringUtils.isEmpty(entityEmail)) {
            if(!StringUtils.isEmpty(userDnString)) {
                userDnString += ",";
            }
            if(entityType.equals(SUBJECT_DN) && profile.getUse(DnComponents.DNEMAILADDRESS, 0)) {
                userDnString += "E=" + entityEmail;
            }
            if(entityType.equals(SUBJECT_ALTERNATIVE_NAME) && profile.getUse(DnComponents.RFC822NAME, 0)) {
                userDnString += "RFC822NAME=" + entityEmail;
            }
        }
        
        final int averageRdnsPerDnField = 16;
        final int numberofDnfields;
        if(entityType.equals(SUBJECT_DN)) {
            numberofDnfields = profile.getSubjectDNFieldOrderLength();
        } else {
            numberofDnfields = profile.getSubjectAltNameFieldOrderLength();
        }
        
        if(numberofDnfields==0) {
            if(!userDnString.isEmpty()) {
                // no fields are allowed in profile
                throw new EndEntityProfileValidationException("Only empty " + entityType + " is supported.");
            } else {
                return "";
            }
        }
        
        LinkedHashMap<String, LinkedHashSet<Rdn>> userRdns = 
                new LinkedHashMap<>();
        
        
        Map<String, Integer> emptyFieldsForDntype = new HashMap<>();
        int[] fielddata = null;
        String value;
        LinkedHashSet<Rdn> orderedRdns;
        LinkedHashSet<Rdn> orderedModifiableRdns;
        LinkedHashSet<Rdn> currentDnTypeUserRdns = new LinkedHashSet<>();        
        
        //Build user DN, all DN types are validated
        String[] userDnParts = splitEscaped(userDnString, ",");
        String[] dnParts;
        String parameter;
        for (String curDn: userDnParts) {
            currentDnTypeUserRdns.clear();
            dnParts = splitEscaped(curDn, "=");            
            if(dnParts.length==1) {
                // skip processing empty DN values as they will be ignored at the end
                continue;
            }
            
            parameter = dnParts[0].toUpperCase(Locale.ROOT).trim();
            parameter = getBcNameStyle(parameter, entityType);
            if(!isValidDnType(parameter, entityType)) {
                throw new IllegalArgumentException("Invalid DN type: " + parameter);
            }
            
            if(dnParts.length==2) {
                value = dnParts[1].trim();
                if(StringUtils.isEmpty(value)) {
                    throw new IllegalArgumentException("Invalid DN component value in: " + curDn);
                }
                
                Rdn rdn = convertToRdn(parameter, value);
                if(!userRdns.containsKey(parameter)){
                    orderedRdns = new LinkedHashSet<Rdn>(averageRdnsPerDnField);
                    userRdns.put(parameter, orderedRdns);
                } else {
                    orderedRdns = userRdns.get(parameter);
                }
                orderedRdns.add(rdn);
            } else {
                
                value = curDn.substring(dnParts[0].length()+1).trim();
                String []currentDnParts = splitEscaped(curDn, "+");
                for(String currentDnPart: currentDnParts) {
                    dnParts = splitEscaped(currentDnPart, "=");
                    parameter = dnParts[0].toUpperCase(Locale.ROOT).trim();
                    parameter = getBcNameStyle(parameter, entityType);
                    if(!isValidDnType(parameter, entityType)) {
                        throw new IllegalArgumentException("Invalid DN component: " + parameter);
                    }
                    
                    if(dnParts.length!=2 || StringUtils.isEmpty(dnParts[1])) {
                        throw new IllegalArgumentException(
                                "Invalid DN component detected during processing multi-valued rdn: " + curDn);
                    }
                    
                    Rdn rdn = convertToRdn(parameter, value);
                    value = ""; 
                    // we only add the value against first dnType
                    // for rest null is added, but dnType is tracked to validate 
                    // against number of fields allowed in profile
                    if(!userRdns.containsKey(parameter)){
                        orderedRdns = new LinkedHashSet<Rdn>(averageRdnsPerDnField);
                        userRdns.put(parameter, orderedRdns);
                    } else {
                        orderedRdns = userRdns.get(parameter);
                    }
                    
                    orderedRdns.add(rdn);
                }
            }
            
        }
        
        //Build profile's DN
        LinkedHashMap<String, LinkedHashSet<Rdn>> groupedModifiableProfileRdns = 
                new LinkedHashMap<>(numberofDnfields/averageRdnsPerDnField);
        LinkedHashMap<String, LinkedHashSet<Rdn>> groupedAllProfileRdns = 
                new LinkedHashMap<>(numberofDnfields/averageRdnsPerDnField);
        
        boolean isModifiable;
        for (int i = 0; i < numberofDnfields; i++) {
            fielddata = entityType.equals(SUBJECT_DN) ? 
                    profile.getSubjectDNFieldsInOrder(i) : profile.getSubjectAltNameFieldsInOrder(i);
            value = profile.getValue(fielddata[EndEntityProfile.FIELDTYPE], fielddata[EndEntityProfile.NUMBER]);
            
            if(StringUtils.isNotEmpty(value)) {
                value = value.trim();
            }
            
            // also validates parameter type
            parameter = DNFieldExtractor.getFieldComponent(
                    DnComponents.profileIdToDnId(fielddata[EndEntityProfile.FIELDTYPE]),
                    entityType.equals(SUBJECT_DN) ? 
                    DNFieldExtractor.TYPE_SUBJECTDN : DNFieldExtractor.TYPE_SUBJECTALTNAME);
            parameter = StringUtils.replace(parameter, "=", "");
            
            if(StringUtils.isNotEmpty(value) && value.contains(";")) {
                // we need to address DN values with multiple valid choices
                // they are represented as ;-separated list and unmodifiable
                boolean optionSelected = false;
                if(userRdns.containsKey(parameter)) {
                    orderedRdns = userRdns.get(parameter);
                    for(String dn: value.split(";")) {
                        if(orderedRdns.contains(convertToRdn(parameter, dn))) {
                            optionSelected = true;
                            value = dn;
                        }
                    }
                }
                
                if(!optionSelected) {
                    value = value.substring(0, value.indexOf(";"));
                }
            }
            
            if(!groupedAllProfileRdns.containsKey(parameter)){
                orderedRdns = new LinkedHashSet<>(averageRdnsPerDnField);
                groupedAllProfileRdns.put(parameter, orderedRdns);

                orderedModifiableRdns = new LinkedHashSet<Rdn>(averageRdnsPerDnField);
                groupedModifiableProfileRdns.put(parameter, orderedModifiableRdns);
                emptyFieldsForDntype.put(parameter, 0);
                
            } else {
                orderedRdns = groupedAllProfileRdns.get(parameter);
                orderedModifiableRdns = groupedModifiableProfileRdns.get(parameter);
            }
            
            Rdn rdn = convertToRdn(parameter, value);
            orderedRdns.add(rdn);
            isModifiable = profile.isModifyable(fielddata[EndEntityProfile.FIELDTYPE], fielddata[EndEntityProfile.NUMBER]);

            if(isModifiable) {
                if(StringUtils.isEmpty(value)) {
                    // only modifiable fields can be empty
                    emptyFieldsForDntype.put(parameter, emptyFieldsForDntype.get(parameter)+1);
                } else {
                    orderedModifiableRdns.add(rdn);
                }
            }
            
        }
        
        Set<String> userDnTypes = new HashSet<>(userRdns.keySet());
        userDnTypes.removeAll(groupedAllProfileRdns.keySet());
        if(!userDnTypes.isEmpty()){
            // user contains un-allowed DN types
            String error = "Not allowed " + entityType + " type(s): " + userDnTypes;
            log.debug(error);
            throw new IllegalArgumentException(error);
        }
        
        int currentDnTypeModifiedDns;
        for(String dnType: groupedAllProfileRdns.keySet()) {
            if(userRdns.containsKey(dnType)) {
                currentDnTypeUserRdns = userRdns.get(dnType);
            } else {
                continue; // only profile rdns to add, nothing to remove
            }
            
            if(groupedAllProfileRdns.containsKey(dnType)) {
                orderedRdns = groupedAllProfileRdns.get(dnType);
                orderedModifiableRdns = groupedModifiableProfileRdns.get(dnType);
            } else {
                continue; // only user rdns to add, nothing to remove
            }
            
            currentDnTypeModifiedDns = 0;
            for(Rdn userdn: currentDnTypeUserRdns) {
                if(!orderedRdns.remove(userdn)||userdn==null){ 
                    // modifiable fields were not altered, if already part of profile dn
                    currentDnTypeModifiedDns++;
                }
                // if in non-modifiable set, only sent to top of dn list
                // removed from modifiable list
                orderedModifiableRdns.remove(userdn); 
            }
            
            // remove entries from modifiable fields to make room for added user dns
            Iterator<Rdn> it = orderedModifiableRdns.iterator();
            currentDnTypeModifiedDns -= emptyFieldsForDntype.get(dnType);
            for(int i=0; i<currentDnTypeModifiedDns; i++) {
                if(it.hasNext()) {
                    // no need to remove from modifiable one 
                    orderedRdns.remove(it.next());
                } else {
                    // added fields exceeds no of modifiable fields
                    throw new EndEntityProfileValidationException("User DN has too many components for " + dnType);
                }
            }

        }
        
        StringBuilder result = new StringBuilder(numberofDnfields*10);
        for(String dnType: groupedAllProfileRdns.keySet()) {
            // finally, add all dnTypes according to sequence in profile
            // for individual dn type, first user dns and the profile dns
            if(userRdns.containsKey(dnType)) {
                for(Rdn dn: userRdns.get(dnType)) {
                    appendDn(result, dn);
                }
            }
            
            for(Rdn dn: groupedAllProfileRdns.get(dnType)) {
                appendDn(result, dn);
            }
        }
        
        if(result.length()==0) {
            log.debug("merged dn is empty.");
            return "";
        }
        
        String mergedDn = result.toString();
        mergedDn = mergedDn.substring(0, mergedDn.length() - 1);
        log.debug("merged dn: " + mergedDn);
        
        return mergedDn;
    }
    
    private static String getBcNameStyle(String parameter, String entityType) {
        if(entityType.equals(SUBJECT_ALTERNATIVE_NAME)) {
            return parameter;
        }
        return BC_STYLE_PARAMETERS.getOrDefault(parameter.toUpperCase(), parameter.toUpperCase());
    }

    private static void appendDn(StringBuilder buffer, Rdn rdn) {
        if(rdn==null||StringUtils.isEmpty((String) rdn.getValue())) {
            return;
        }
        buffer.append(rdn.toString());
        buffer.append(",");
    }
    
    private static boolean isValidDnType(String dnType, String entityType) {
        if(entityType.equals(SUBJECT_DN)) {
            return DnComponents.getDnIdFromDnName(dnType) != null;
        } else {
            return DnComponents.getDnIdFromAltName(dnType) != null;
        }
    }
    
    private static Rdn convertToRdn(String parameter, String value) {
        if(StringUtils.isEmpty(value)) {
            return null;
        }
        try {
            return new Rdn(parameter, value);
        } catch (InvalidNameException e) {
            log.debug("Invalid Rdn parameter or value: " + e.getMessage());
            throw new IllegalStateException(e);
        }
    }
    
    private static String[] splitEscaped(String input, String separator) {
        if(separator.equals("+")) {
            separator = "\\+";
        }
        String[] parts = input.replace("\\" + separator, REPLACED_ESCAPABLE_CHARS).split(separator);
        for(int i=0; i<parts.length; i++) {
            parts[i] = parts[i].replace(REPLACED_ESCAPABLE_CHARS, "\\" + separator);
        }
        return parts;
    }
    
    
}

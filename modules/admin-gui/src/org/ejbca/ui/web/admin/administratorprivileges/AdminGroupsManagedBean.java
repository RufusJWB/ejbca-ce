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

package org.ejbca.ui.web.admin.administratorprivileges;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.faces.context.FacesContext;
import javax.faces.model.SelectItem;

import org.apache.log4j.Logger;
import org.cesecore.authorization.AuthorizationDeniedException;
import org.cesecore.authorization.rules.AccessRuleData;
import org.cesecore.authorization.rules.AccessRuleState;
import org.cesecore.authorization.user.AccessMatchType;
import org.cesecore.authorization.user.AccessMatchValue;
import org.cesecore.authorization.user.AccessUserAspectData;
import org.cesecore.roles.RoleData;
import org.cesecore.roles.RoleExistsException;
import org.cesecore.roles.RoleNotFoundException;
import org.ejbca.config.GlobalConfiguration;
import org.ejbca.core.model.authorization.AccessRulesConstants;
import org.ejbca.core.model.authorization.AdminEntity;
import org.ejbca.core.model.authorization.BasicAccessRuleSet;
import org.ejbca.core.model.authorization.BasicAccessRuleSetDecoder;
import org.ejbca.core.model.authorization.BasicAccessRuleSetEncoder;
import org.ejbca.core.model.util.EjbLocalHelper;
import org.ejbca.ui.web.admin.BaseManagedBean;
import org.ejbca.ui.web.admin.configuration.AccessRulesView;
import org.ejbca.ui.web.admin.configuration.AuthorizationDataHandler;
import org.ejbca.ui.web.admin.configuration.EjbcaJSFHelper;

/**
 * Managed bean for editing administrative privileges.
 * 
 * @author Philip Vendil
 * @version $Id$
 */
public class AdminGroupsManagedBean extends BaseManagedBean {
	private static final long serialVersionUID = 1L;
	private static final Logger log = Logger.getLogger(AdminGroupsManagedBean.class);
	
	private final EjbLocalHelper ejb = new EjbLocalHelper();

	public AdminGroupsManagedBean() { }

	//
	// Edit groups (mostly used by administratorprivileges.jsp)
	// 

	private String newAdminGroupName = "new";

	// Simple from backing
	public String getNewAdminGroupName() { return this.newAdminGroupName; }
	public void setNewAdminGroupName(String newAdminGroupName) { this.newAdminGroupName = newAdminGroupName; }

	/** @return a List of authorized AdminGroups */
	public List<RoleData> getAdminGroups() {
		List <RoleData> adminGroups = new ArrayList<RoleData>();
		adminGroups.addAll(getAuthorizationDataHandler().getRoles());
		Collections.sort(adminGroups);
		return adminGroups;
	}

	/** Renames a role */
	public void renameGroup() {
		String newGroupName = getNewAdminGroupName();
		try {
			getAuthorizationDataHandler().renameRole(getCurrentAdminGroup(), newGroupName);
			setCurrentAdminGroup(newGroupName);
		} catch (RoleExistsException e) {
			addErrorMessage("ADMINGROUPEXISTS");
		} catch (AuthorizationDeniedException e) {
			addErrorMessage("AUTHORIZATIONDENIED");
		}
	}

	/** Removes a role
	 * @throws RoleNotFoundException 
	 *  
	 */
	public void deleteGroup() throws RoleNotFoundException  {
		try {
			getAuthorizationDataHandler().removeRole(getCurrentAdminGroup());
		} catch (AuthorizationDeniedException e) {
			addErrorMessage("AUTHORIZATIONDENIED");
		}
	}

	/** Adds a new admin group */
	public void addGroup() {
		try {
			getAuthorizationDataHandler().addRole(getNewAdminGroupName());
		} catch (RoleExistsException e) {
			addErrorMessage("ADMINGROUPEXISTS");
		} catch (AuthorizationDeniedException e) {
			addErrorMessage("AUTHORIZATIONDENIED");
		}
	}

	/** @return the BasicAccessRuleSetEncoder for the current admin group in a list */
	public BasicAccessRuleSetEncoder getBasicRuleSetForEach() {
		return getBasicRuleSetInternal(getCurrentAdminGroupObjectForEach());
	}

	//
	// Edit administrators in a group (mostly used by editadminentities.jsp) 
	//

	private String currentAdminGroupName = null;
	private String matchCaId = null;
	private AccessMatchValue matchWith = AccessMatchValue.WITH_SERIALNUMBER;  
	private AccessMatchType matchType = null;
	private String matchValue = null;

	/** @return a List of (SelectItem<String, String>) authorized CA */
	public List<SelectItem> getAvailableCaIds() {
		List<SelectItem> list  = new ArrayList<SelectItem>();
		Collection<Integer> availableCAs = getBasicRuleSet().getAvailableCAs();	// All the CAs (and no 'All' flag)
		Map<String, Integer> cas = getEjbcaWebBean().getInformationMemory().getAllCANames();
		for (String caName : cas.keySet()) {
			Integer caId = cas.get(caName);
			if (availableCAs.contains(caId)) {
				list.add(new SelectItem(caId.toString(), caName));
			}
		}
		return list;
	}

	/** @return a viewable list of 'match with'-texts */
	public List<SelectItem> getMatchWithTexts() {
		List<SelectItem> list = new ArrayList<SelectItem>();
		for (String current : AdminEntity.MATCHWITHTEXTS) {
			if (!"".equals(current)) {
				list.add(new SelectItem(current, getEjbcaWebBean().getText(current)));
			}
		}
		return list;
	}

	/** @return a viewable list of 'match type'-texts */
	public List<SelectItem> getMatchTypeTexts() {
		List<SelectItem> list = new ArrayList<SelectItem>(); 
		for (String current : AdminEntity.MATCHTYPETEXTS) {
			list.add(new SelectItem(current, getEjbcaWebBean().getText(current)));
		}
		return list;
	}

	/** @return the  public constants of AdminEntity as a Map  */
	public Map<String, Object> getAdminEntityConstants() {
		return getPublicConstantsAsMap(AdminEntity.class);
	}

	// Simple form backing
	public String getMatchCaId() { return matchCaId; }
	public void setMatchCaId(String matchCaId) { this.matchCaId= matchCaId; }
	public AccessMatchValue getMatchWith() { return matchWith; }
	public void setMatchWith(AccessMatchValue matchWith) { this.matchWith= matchWith; }
	public AccessMatchType getMatchType() { return matchType; }
	public void setMatchType(AccessMatchType matchType) { this.matchType= matchType; }
	public String getMatchValue() { return matchValue; }
	public void setMatchValue(String matchValue) { this.matchValue = matchValue; }

	/** Adds an admin to the current group. 
	 * @throws RoleNotFoundException */
	public void addAdmin() throws RoleNotFoundException {
	    AccessMatchValue matchWith = getMatchWith();
		AccessMatchType matchType = getMatchType();
		String matchValue = getMatchValue();
		if (matchValue==null || "".equals(matchValue)) {
			addErrorMessage("MATCHVALUEREQUIRED");
			return;
		}
		int caid = Integer.parseInt(getMatchCaId());
		AccessUserAspectData adminEntity = new AccessUserAspectData(getCurrentAdminGroupObject().getRoleName(), caid, matchWith, matchType, matchValue);
		// TODO: Check if adminentity exist and add a nice errormessage instead of being silent
		Collection<AccessUserAspectData> adminEntities = new ArrayList<AccessUserAspectData>();
		adminEntities.add(adminEntity);
		try {
			getAuthorizationDataHandler().addAdminEntities(getCurrentAdminGroupObject(), adminEntities);
		} catch (AuthorizationDeniedException e) {
			addErrorMessage("AUTHORIZATIONDENIED");
		}
	}

	/** Removes an admin from the current group. 
	 * @throws RoleNotFoundException */
	public void deleteAdmin() throws RoleNotFoundException {
		AccessUserAspectData adminEntity =  getAdminForEach();
		Collection<AccessUserAspectData> adminEntities = new ArrayList<AccessUserAspectData>();
		adminEntities.add(adminEntity);
		try {
			getAuthorizationDataHandler().removeAdminEntities(getCurrentAdminGroupObject(), adminEntities);
		} catch (AuthorizationDeniedException e) {
			addErrorMessage("AUTHORIZATIONDENIED");
		}
	}

	/** @return the current admin group for the current row in the datatable */
	private RoleData getCurrentAdminGroupObjectForEach() {
		String adminGroupName = ((RoleData) FacesContext.getCurrentInstance().getExternalContext().getRequestMap().get("adminGroup")).getRoleName();
		RoleData adminGroup = null;
		try {
			adminGroup = getAuthorizationDataHandler().getRole(adminGroupName);
		} catch (AuthorizationDeniedException e) {
			addErrorMessage("AUTHORIZATIONDENIED");
		}
		return adminGroup;
	}

	/** @return the administrators for the current admin group */
	public Collection<AccessUserAspectData> getAdmins() {
	    List<AccessUserAspectData> list = new ArrayList<AccessUserAspectData>();
		try {
			list.addAll(getAuthorizationDataHandler().getRole((getCurrentAdminGroup())).getAccessUsers().values());
		} catch (AuthorizationDeniedException e) {
			addErrorMessage("AUTHORIZATIONDENIED");
		}
		Collections.sort(list);
		return list;
	}

	/** @return the name of the CA that has issed the certificate for the admin in the current row of the datatable */
	public String getIssuingCA() {
	    AccessUserAspectData adminEntity = getAdminForEach();
		String caName = (String) ejb.getCaAdminSession().getCAIdToNameMap(EjbcaJSFHelper.getBean().getAdmin()).get(adminEntity.getCaId());
		if (caName == null) {
			caName = "Unknown CA with hash " + adminEntity.getCaId();
		}
		return caName;
	}

	/** @return the 'match with'-text for the admin in the current row of the datatable */
	public String getAdminsMatchWith() {
		return getEjbcaWebBean().getText( AdminEntity.MATCHWITHTEXTS[getAdminForEach().getMatchWith()] );
	}

	/** @return the 'match type'-text for the admin in the current row of the datatable */
	public String getAdminsMatchType() {
	    AccessUserAspectData adminEntity =  getAdminForEach();
		if (adminEntity.getMatchType() < AdminEntity.SPECIALADMIN_PUBLICWEBUSER) {
			return getEjbcaWebBean().getText( AdminEntity.MATCHTYPETEXTS[adminEntity.getMatchType()-1000] );
		}
		return "" + adminEntity.getMatchType();
	}

	/** @return the AdminEntity object for the current row in the datatable */
	private AccessUserAspectData getAdminForEach() {
		return (AccessUserAspectData) FacesContext.getCurrentInstance().getExternalContext().getRequestMap().get("admin");
	}

	//
	// Edit basic access rules (mostly used by editbasicaccessrules.jsp)
	//

	private BasicAccessRuleSetEncoder basicAccessRuleSetEncoderCache = null;

	private RoleData currentRole = null;
	private List<Integer> currentCAs = null;
	private List<Integer> currentEndEntityProfiles = null;
	private List<Integer> currentOtherRules = null;
	private List<Integer> currentEndEntityRules = null;

	// Stores the value from request, but always reads the value directly from the saved data
	public String getCurrentRole() {return getBasicRuleSet().getCurrentRole(); }
	public void setCurrentRole(RoleData currentRole) { this.currentRole = currentRole; 	}
	public List<String> getCurrentCAs() { return integerSetToStringList(getBasicRuleSet().getCurrentCAs()); }
	public void setCurrentCAs(List<String> currentCAs) { 	this.currentCAs = stringListToIntegerList(currentCAs); }
	public List<String> getCurrentEndEntityProfiles() { return integerSetToStringList(getBasicRuleSet().getCurrentEndEntityProfiles()); }
	public void setCurrentEndEntityProfiles(List<String> currentEndEntityProfiles) { this.currentEndEntityProfiles = stringListToIntegerList(currentEndEntityProfiles); }
	public List<String> getCurrentOtherRules() { return integerSetToStringList(getBasicRuleSet().getCurrentOtherRules()); }
	public void setCurrentOtherRules(List<String> currentOtherRules) {	this.currentOtherRules = stringListToIntegerList(currentOtherRules); }
	public List<String> getCurrentEndEntityRules() { return integerSetToStringList(getBasicRuleSet().getCurrentEndEntityRules()); }
	public void setCurrentEndEntityRules(List<String> currentEndEntityRules) { this.currentEndEntityRules = stringListToIntegerList(currentEndEntityRules); }

	/** @return a new access rules view
	public AccessRulesView getAdminGroupAccessRules() {
		return new AccessRulesView(getCurrentAdminGroupObject().getAccessRules());
	}*/

	/** @return a new access rules view
	public AccessRulesView getUnusedAdminGroupAccessRules() {
		return new AccessRulesView(getCurrentAdminGroupObject().nonUsedAccessRules(getAuthorizationDataHandler().getAvailableAccessRules()));
	}*/

	/** @return a cached BasicAccessRuleSet  */
	public BasicAccessRuleSetEncoder getBasicRuleSet() {
		if (basicAccessRuleSetEncoderCache == null) {
			basicAccessRuleSetEncoderCache = getBasicRuleSetInternal(getCurrentAdminGroupObject());
		}
		return basicAccessRuleSetEncoderCache;
	}

	/** @return the public constants of BasicAccessRuleSet as a Map  */
	public Map<String, Object> getBasicAccessRuleSetConstants() {
		return getPublicConstantsAsMap(BasicAccessRuleSet.class);
	}

	/** @return the available admin roles as a Collection<SelectItem>  */
	public Collection<SelectItem> getAvailableRoles() {
		Collection<SelectItem> list = new ArrayList<SelectItem>();
		for (String currentRole : getBasicRuleSet().getAvailableRoles()) {
			list.add(new SelectItem(currentRole, getEjbcaWebBean().getText(currentRole)));
		}
		return list;
	}

    /** @return the available cas as a Collection<SelectItem> */
    public Collection<SelectItem> getAvailableCasAndAll() {
        Collection<SelectItem> cas = getAvailableCaIds();

        if (getAuthorizationDataHandler().isAuthorizedNoLog(getAdmin(), AccessRulesConstants.CABASE)) {
            cas.add(new SelectItem(String.valueOf(BasicAccessRuleSet.CA_ALL), getEjbcaWebBean().getText("ALL")));
        }

        return cas;
    }

	/** @return the available end entity rules as a Collection<SelectItem>  */
	public Collection<SelectItem> getAvailableEndEntityRules()  {
		Collection<SelectItem> list = new ArrayList<SelectItem>();
		for (Integer currentRule : (Collection<Integer>) getBasicRuleSet().getAvailableEndEntityRules()) {
			list.add(new SelectItem(currentRule, getEjbcaWebBean().getText(BasicAccessRuleSet.getEndEntityRuleText(currentRule))));
		}
		return list;
	}

	/** @return the available end entity profile rules as a Collection<SelectItem>  */
	public Collection<SelectItem> getAvailableEndEntityProfiles()  {
		Collection<SelectItem> list = new ArrayList<SelectItem>();
		for (Integer currentProfile : (Collection<Integer>) getBasicRuleSet().getAvailableEndEntityProfiles()) {
			if (currentProfile == BasicAccessRuleSet.ENDENTITYPROFILE_ALL) {
				list.add(new SelectItem(currentProfile, getEjbcaWebBean().getText("ALL")));
			} else {
				list.add(new SelectItem(currentProfile, ejb.getEndEntityProfileSession().getEndEntityProfileName(getAdmin(), currentProfile)));
			}
		}
		return list;
	}

	/** @return the available other access rules as a Collection<SelectItem> */
	public Collection<SelectItem> getAvailableOtherRules()  {
		Collection<SelectItem> list = new ArrayList<SelectItem>();
		for (Integer currentRule : (Collection<Integer>) getBasicRuleSet().getAvailableOtherRules()) {
			list.add(new SelectItem(currentRule, getEjbcaWebBean().getText(BasicAccessRuleSet.OTHERTEXTS[currentRule])));
		}
		return list;
	}

	/** Save the current state of the access rules and invalidate caches 
	 * @throws RoleNotFoundException */
	public void saveAccessRules() throws RoleNotFoundException {
		BasicAccessRuleSetDecoder barsd = new BasicAccessRuleSetDecoder(currentRole, currentCAs, currentEndEntityRules, currentEndEntityProfiles, currentOtherRules);
		try {
			getAuthorizationDataHandler().replaceAccessRules(getCurrentAdminGroup(), barsd.getCurrentAdvancedRuleSet());
		} catch (AuthorizationDeniedException e) {
			addErrorMessage("AUTHORIZATIONDENIED");
		}
		basicAccessRuleSetEncoderCache = null;	// We want this to be re-read
		getEjbcaWebBean().getInformationMemory().administrativePriviledgesEdited();
	}
	
	private BasicAccessRuleSetEncoder getBasicRuleSetInternal(RoleData adminGroup) {
		GlobalConfiguration globalConfiguration = getEjbcaWebBean().getGlobalConfiguration();
		return new BasicAccessRuleSetEncoder(adminGroup.getAccessRules().values(), getAuthorizationDataHandler().getAvailableAccessRules(),
				globalConfiguration.getIssueHardwareTokens(), globalConfiguration.getEnableKeyRecovery());
	}

	//
	// Advanced access rules (mostly used by editadvancedaccessrules.jsp)
	//

	private AccessRulesView accessRulesViewCache = null;

	/** @return a cached list of all the available access rules holding the current state */
	private AccessRulesView getAccessRules() {
		if (accessRulesViewCache == null) {
			RoleData adminGroup = getCurrentAdminGroupObject();
			Collection<AccessRuleData> usedAccessRules = adminGroup.getAccessRules().values();
			Collection<AccessRuleData> unusedAccessRules = adminGroup.getDisjunctSetOfRules(getAuthorizationDataHandler().getAvailableAccessRules());
			usedAccessRules.addAll(unusedAccessRules);
			accessRulesViewCache = new AccessRulesView(usedAccessRules);
		}
		return accessRulesViewCache;
	}

	/** @return a list of lists with access rules and the catagory name */
	public Collection<AccessRuleCollection> getAccessRulesCollections() {
		Collection<AccessRuleCollection> result = new ArrayList<AccessRuleCollection>();
		result.add(new AccessRuleCollection("ROLEBASEDACCESSRULES", getAccessRules().getRoleBasedAccessRules()));
		result.add(new AccessRuleCollection("REGULARACCESSRULES", getAccessRules().getRegularAccessRules()));
		result.add(new AccessRuleCollection("ENDENTITYPROFILEACCESSR", getAccessRules().getEndEntityProfileAccessRules()));
		result.add(new AccessRuleCollection("CAACCESSRULES", getAccessRules().getCAAccessRules()));
		result.add(new AccessRuleCollection("USERDATASOURCEACCESSRULES", getAccessRules().getUserDataSourceAccessRules()));
		return result;
	}

	/** @return a viewable list of the possible values for a access rule */
	public Collection<SelectItem> getAccessRuleRules() {
		Collection<SelectItem> result = new ArrayList<SelectItem>();
		result.add(new SelectItem(AccessRuleState.RULE_NOTUSED, getEjbcaWebBean().getText(AccessRuleState.RULE_NOTUSED.getName(), true)));
		result.add(new SelectItem(AccessRuleState.RULE_ACCEPT, getEjbcaWebBean().getText(AccessRuleState.RULE_ACCEPT.getName(), true)));
		result.add(new SelectItem(AccessRuleState.RULE_DECLINE, getEjbcaWebBean().getText(AccessRuleState.RULE_DECLINE.getName(), true)));
		return result;
	}

	/** @return a parsed version of the accessrule for the current row in the datatable. CAs, End Entity Profiles and UserDataSources are given their cleartext name. */
	public String getParsedAccessRule() {
		AccessRuleData accessRule =  (AccessRuleData) FacesContext.getCurrentInstance().getExternalContext().getRequestMap().get("accessRule");
		String resource = accessRule.getAccessRuleName();
		// Check if it is a profile rule, then replace profile id with profile name.
		if (resource.startsWith(AccessRulesConstants.ENDENTITYPROFILEPREFIX)) {
			if (resource.lastIndexOf('/') < AccessRulesConstants.ENDENTITYPROFILEPREFIX.length()) {
				return AccessRulesConstants.ENDENTITYPROFILEPREFIX + ejb.getEndEntityProfileSession().getEndEntityProfileName(
						getAdmin(), Integer.parseInt(resource.substring(AccessRulesConstants.ENDENTITYPROFILEPREFIX.length())));
			} else {
				String tmpString = resource.substring(AccessRulesConstants.ENDENTITYPROFILEPREFIX.length());
				return AccessRulesConstants.ENDENTITYPROFILEPREFIX + ejb.getEndEntityProfileSession().getEndEntityProfileName(
						getAdmin(), Integer.parseInt(tmpString.substring(0, tmpString.indexOf('/')))) + tmpString.substring(tmpString.indexOf('/'));
			}
		}
		// Check if it is a CA rule, then replace CA id with CA name.
		if (resource.startsWith(AccessRulesConstants.CAPREFIX)) {
			Map<Integer, String> caIdToNameMap = ejb.getCaAdminSession().getCAIdToNameMap(getAdmin());
			if(resource.lastIndexOf('/') < AccessRulesConstants.CAPREFIX.length()) {
				return AccessRulesConstants.CAPREFIX + caIdToNameMap.get(Integer.valueOf(resource.substring(AccessRulesConstants.CAPREFIX.length())));
			} else {
				return AccessRulesConstants.CAPREFIX + caIdToNameMap.get(Integer.valueOf(resource.substring(AccessRulesConstants.CAPREFIX.length(),
						resource.lastIndexOf('/')))) + resource.substring(resource.lastIndexOf('/'));
			}
		}
		// Check if it is a User Data Source rule, then replace User Data Source id with User Data Source name.
		if (resource.startsWith(AccessRulesConstants.USERDATASOURCEPREFIX)) { 
			if (resource.lastIndexOf('/') < AccessRulesConstants.USERDATASOURCEPREFIX.length()) {
				return AccessRulesConstants.USERDATASOURCEPREFIX + ejb.getUserDataSourceSession().getUserDataSourceName(
						getAdmin(), Integer.parseInt(resource.substring(AccessRulesConstants.USERDATASOURCEPREFIX.length())));
			} else {
				return AccessRulesConstants.USERDATASOURCEPREFIX + ejb.getUserDataSourceSession().getUserDataSourceName(
						getAdmin(), Integer.parseInt(resource.substring(AccessRulesConstants.USERDATASOURCEPREFIX.length(), resource.lastIndexOf('/')))) +
						resource.substring(resource.lastIndexOf('/'));
			}
		}
		return resource;
	}

	/** Save the current state of the access rules and invalidate caches 
	 * @throws RoleNotFoundException */
	public void saveAdvancedAccessRules() throws RoleNotFoundException {
		log.info("Trying to replace access rules..");
		Collection<AccessRuleData> allRules = new ArrayList<AccessRuleData>();
		Collection<AccessRuleData> toReplace = new ArrayList<AccessRuleData>();
		List<AccessRuleData> toRemove = new ArrayList<AccessRuleData>();
		allRules.addAll(getAccessRules().getRoleBasedAccessRules());
		allRules.addAll(getAccessRules().getRegularAccessRules());
		allRules.addAll(getAccessRules().getEndEntityProfileAccessRules());
		allRules.addAll(getAccessRules().getCAAccessRules());
		allRules.addAll(getAccessRules().getUserDataSourceAccessRules());
		// TODO: Remove all access rules marked as UNUSED and replace the others
		for(AccessRuleData ar : allRules) {
			if (ar.getInternalState() == AccessRuleState.RULE_NOTUSED) {
				toRemove.add(ar);
			} else {
				toReplace.add(ar);
			}
		}
		try {
			getAuthorizationDataHandler().removeAccessRules(currentAdminGroupName, toRemove);
			getAuthorizationDataHandler().replaceAccessRules(currentAdminGroupName, toReplace);
		} catch (AuthorizationDeniedException e) {
			addErrorMessage("AUTHORIZATIONDENIED");
		}
		accessRulesViewCache = null;	// We want this to be re-read
		basicAccessRuleSetEncoderCache = null;	// We want this to be re-read
		getEjbcaWebBean().getInformationMemory().administrativePriviledgesEdited();
	}
	
	/** Invalidates local cache */
	public void restoreAdvancedAccessRules() {
		accessRulesViewCache = null;	// We want this to be re-read
	}

	//
	// Methods used by several pages
	//
	
	/** @return the name of current admin group sent with POST, GET or injected through the backing value */
	public String getCurrentAdminGroup() {
		// Did we get the AdminGroup passed as f:param to this page or as a GET parameter?
		String current = currentAdminGroupName;
		// Try reading it from the form POST
		final String FIELDNAME = "currentAdminGroup";
		final String[] FORMNAMES = {"currentGroupList", "adminListForm", "basicRules", "accessRulesForm"};
		for (String key : FORMNAMES) {
			if (current != null) {
				break;
			}
			current = getRequestParameter(key + ":" + FIELDNAME);
		}
		return current;
	}

	/** @return the current admin group sent with POST, GET or injected through the backing value */
	public RoleData getCurrentAdminGroupObject() {
		RoleData adminGroup = null;
		try {
			adminGroup = getAuthorizationDataHandler().getRole(getCurrentAdminGroup());
		} catch (AuthorizationDeniedException e) {
			addErrorMessage("AUTHORIZATIONDENIED");
		}
		return adminGroup;
	}

	/** Setter for current admin group used were applicable. */
	public void setCurrentAdminGroup(String currentAdminGroupName) {
		this.currentAdminGroupName = currentAdminGroupName;
	}

	/** @return true if logged on administrator is allowed to edit current group */
	public boolean isAuthorizedToGroup() {
		for (RoleData adminGroup :  getAuthorizationDataHandler().getRoles()) {
			if (adminGroup.getRoleName().equals(getCurrentAdminGroup())) {
				return true;
			}
		}
		return false;
	}

	//
	// Helper functions
	//

	private AuthorizationDataHandler getAuthorizationDataHandler() {
		return getEjbcaWebBean().getAuthorizationDataHandler();
	}

	private String getRequestParameter(String key) {
		return (String) FacesContext.getCurrentInstance().getExternalContext().getRequestParameterMap().get(key);
	}

	private static List<String> integerSetToStringList(Set<Integer> hashSet) {
		List<String> stringList = new ArrayList<String>();
		for (Integer item : hashSet) {
			stringList.add(item.toString());
		}
		return stringList;
	}

	private static List<Integer> stringListToIntegerList(List<String> stringList) {
		List<Integer> integerList = new ArrayList<Integer>();
		for (String string : stringList) {
			integerList.add(Integer.parseInt(string));
		}
		return integerList;
	}

	/* Useful for debugging and development..
	private void dumpAllParameters() {
		Map m = FacesContext.getCurrentInstance().getExternalContext().getRequestMap();
		Iterator i1 = m.keySet().iterator();
		String x = "";
		while (i1.hasNext()) {
			String key = (String) i1.next();
			x +=  key + "=" + m.get(key) + " ";
		}
		log.info("RequestMap: " + x);
		Map m2 = FacesContext.getCurrentInstance().getExternalContext().getRequestParameterMap();
		Iterator i2 = m2.keySet().iterator();
		x = "";
		while (i2.hasNext()) {
			String key = (String) i2.next();
			x +=  key + "=" + m2.get(key) + " ";
		}
		log.info("RequestParameterMap: " + x);
	}
	*/
}

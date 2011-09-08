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
package org.ejbca.ui.web.admin.audit;

import java.io.Serializable;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.faces.context.FacesContext;
import javax.faces.model.SelectItem;

import org.apache.log4j.Logger;
import org.cesecore.audit.AuditLogEntry;
import org.cesecore.audit.audit.SecurityEventsAuditorSessionLocal;
import org.cesecore.audit.enums.EventStatus;
import org.cesecore.audit.enums.EventType;
import org.cesecore.audit.enums.EventTypes;
import org.cesecore.audit.enums.ModuleType;
import org.cesecore.audit.enums.ModuleTypes;
import org.cesecore.audit.enums.ServiceType;
import org.cesecore.audit.enums.ServiceTypes;
import org.cesecore.authentication.tokens.AlwaysAllowLocalAuthenticationToken;
import org.cesecore.authentication.tokens.AuthenticationToken;
import org.cesecore.authentication.tokens.UsernamePrincipal;
import org.cesecore.authorization.AuthorizationDeniedException;
import org.cesecore.util.QueryCriteria;
import org.cesecore.util.ValidityDate;
import org.ejbca.core.ejb.audit.enums.EjbcaEventTypes;
import org.ejbca.core.ejb.audit.enums.EjbcaModuleTypes;
import org.ejbca.core.ejb.audit.enums.EjbcaServiceTypes;
import org.ejbca.core.ejb.ca.caadmin.CAAdminSessionLocal;
import org.ejbca.core.model.util.EjbLocalHelper;
import org.ejbca.ui.web.admin.configuration.EjbcaJSFHelper;
import org.ejbca.ui.web.admin.configuration.EjbcaWebBean;

/**
 * JSF Backing bean for viewing security audit logs.
 * 
 * Reloads the data lazily if when requested if something was cause for an update (first time, user has invoked reload etc).
 * 
 * getConditions() will handle special cases when HTTP GET parameters are passed (e.g. show history for a username).
 * 
 * @version $Id$
 */
public class AuditorManagedBean implements Serializable {

	private static final long serialVersionUID = 1L;
	private static final Logger log = Logger.getLogger(AuditorManagedBean.class);

	private final SecurityEventsAuditorSessionLocal securityEventsAuditorSession = new EjbLocalHelper().getSecurityEventsAuditorSession();
	private final CAAdminSessionLocal caAdminSession = new EjbLocalHelper().getCaAdminSession();
	
	private boolean renderNext = false;

	private boolean reloadResultsNextView = true;
	private String device;
	private String sortColumn = AuditLogEntry.FIELD_TIMESTAMP;
	private boolean sortOrder = QueryCriteria.ORDER_DESC;
	private int maxResults = 40;
	private int startIndex = 1;
	private final List<SelectItem> sortColumns = new ArrayList<SelectItem>();
	private final List<SelectItem> columns = new ArrayList<SelectItem>();
	private final List<SelectItem> sortOrders = new ArrayList<SelectItem>();
	private List<? extends AuditLogEntry> results;
	private Map<Object, String> caIdToNameMap;

	private Map<String, String> columnNameMap = new HashMap<String, String>();
	private final List<SelectItem> eventStatusOptions = new ArrayList<SelectItem>();
	private final List<SelectItem> eventTypeOptions = new ArrayList<SelectItem>();
	private final List<SelectItem> moduleTypeOptions = new ArrayList<SelectItem>();
	private final List<SelectItem> serviceTypeOptions = new ArrayList<SelectItem>();
	private final List<SelectItem> operationsOptions = new ArrayList<SelectItem>();
	private final List<SelectItem> conditionsOptions = new ArrayList<SelectItem>();
	private String conditionColumn = AuditLogEntry.FIELD_SEARCHABLE_DETAIL2;
	private AuditSearchCondition conditionToAdd;
	private List<AuditSearchCondition> conditions = new ArrayList<AuditSearchCondition>();
	
	public AuditorManagedBean() {
		final EjbcaWebBean ejbcaWebBean = EjbcaJSFHelper.getBean().getEjbcaWebBean();
		columnNameMap.put(AuditLogEntry.FIELD_AUTHENTICATION_TOKEN, ejbcaWebBean.getText("ADMINISTRATOR"));
		columnNameMap.put(AuditLogEntry.FIELD_CUSTOM_ID, ejbcaWebBean.getText("CA"));
		columnNameMap.put(AuditLogEntry.FIELD_EVENTSTATUS, ejbcaWebBean.getText("EVENTSTATUS"));
		columnNameMap.put(AuditLogEntry.FIELD_EVENTTYPE, ejbcaWebBean.getText("EVENT"));
		columnNameMap.put(AuditLogEntry.FIELD_MODULE, ejbcaWebBean.getText("MODULE"));
		columnNameMap.put(AuditLogEntry.FIELD_NODEID, AuditLogEntry.FIELD_NODEID);
		columnNameMap.put(AuditLogEntry.FIELD_SEARCHABLE_DETAIL1, ejbcaWebBean.getText("CERTIFICATENR"));
		columnNameMap.put(AuditLogEntry.FIELD_SEARCHABLE_DETAIL2, ejbcaWebBean.getText("USERNAME_ABBR"));
		columnNameMap.put(AuditLogEntry.FIELD_SEQENCENUMBER, ejbcaWebBean.getText("SEQENCENUMBER"));
		columnNameMap.put(AuditLogEntry.FIELD_SERVICE, ejbcaWebBean.getText("SERVICE"));
		columnNameMap.put(AuditLogEntry.FIELD_TIMESTAMP, ejbcaWebBean.getText("TIME"));
		for (final Entry<String,String> entry : columnNameMap.entrySet()) {
			sortColumns.add(new SelectItem(entry.getKey(), entry.getValue()));
		}
		columnNameMap.put(AuditLogEntry.FIELD_ADDITIONAL_DETAILS, ejbcaWebBean.getText("ADDITIONAL_DETAILS"));
		columns.addAll(sortColumns);
		columns.add(new SelectItem(AuditLogEntry.FIELD_ADDITIONAL_DETAILS, columnNameMap.get(AuditLogEntry.FIELD_ADDITIONAL_DETAILS)));
		sortOrders.add(new SelectItem(QueryCriteria.ORDER_ASC, "ASC"));
		sortOrders.add(new SelectItem(QueryCriteria.ORDER_DESC, "DESC"));
		// If no device is chosen we select the first available as default
		if (getDevices().size()>0) {
			device = (String) getDevices().get(0).getValue();
		}
		// We can't use enums directly in JSF 1.2
		for (Operation current : Operation.values()) {
			operationsOptions.add(new SelectItem(current));
		}
		for (Condition current : Condition.values()) {
			conditionsOptions.add(new SelectItem(current));
		}
		for (EventStatus current : EventStatus.values()) {
			eventStatusOptions.add(new SelectItem(current));
		}
		for (EventType current : EventTypes.values()) {
			eventTypeOptions.add(new SelectItem(current));
		}
		for (EventType current : EjbcaEventTypes.values()) {
			eventTypeOptions.add(new SelectItem(current));
		}
		for (ModuleType current : ModuleTypes.values()) {
			moduleTypeOptions.add(new SelectItem(current));
		}
		for (ModuleType current : EjbcaModuleTypes.values()) {
			moduleTypeOptions.add(new SelectItem(current));
		}
		for (ServiceType current : ServiceTypes.values()) {
			serviceTypeOptions.add(new SelectItem(current));
		}
		for (ServiceType current : EjbcaServiceTypes.values()) {
			serviceTypeOptions.add(new SelectItem(current));
		}
		// By default, don't show the authorized to resource events
		conditions.add(new AuditSearchCondition(AuditLogEntry.FIELD_EVENTTYPE, Condition.NOT_EQUALS, EventTypes.ACCESS_CONTROL.name()));
	}

	public List<SelectItem> getDevices() {
		final List<SelectItem> list = new ArrayList<SelectItem>();
		for (final String deviceId : securityEventsAuditorSession.getQuerySupportingLogDevices()) {
			list.add(new SelectItem(deviceId, deviceId));
		}
		return list;
	}
	
	public boolean isOneLogDevice() {
		return getDevices().size()==1;
	}
	
	public boolean isRenderNext() throws AuthorizationDeniedException {
		getResults();
		return renderNext;
	}

	public int getResultSize() throws AuthorizationDeniedException {
		getResults();
		return results==null?0:results.size();
	}

	public List<SelectItem> getSortColumns() {
		return sortColumns;
	}

	public List<SelectItem> getSortOrders() {
		return sortOrders;
	}

	public List<SelectItem> getColumns() {
		return columns;
	}

	public void setDevice(final String selectedDevice) {
		this.device = selectedDevice;
	}

	public String getDevice() {
		return device;
	}
	
	public List<? extends AuditLogEntry> getResults() throws AuthorizationDeniedException {
		if (getDevice() != null && reloadResultsNextView) {
			reloadResults();
			reloadResultsNextView = false;
		}
		return results;
	}

	public void setSortColumn(String sortColumn) {
		this.sortColumn = sortColumn;
	}

	public String getSortColumn() {
		return sortColumn;
	}

	public void setMaxResults(final int maxResults) {
		this.maxResults = Math.min(1000, Math.max(1, maxResults));	// 1-1000 results allowed
	}

	public int getMaxResults() {
		return maxResults;
	}

	public void setStartIndex(final int startIndex) {
		this.startIndex = Math.max(1, startIndex);
	}

	public int getStartIndex() {
		return startIndex;
	}

	public void setSortOrder(boolean sortOrder) {
		this.sortOrder = sortOrder;
	}

	public boolean isSortOrder() {
		return sortOrder;
	}

	public void setConditionColumn(String conditionColumn) {
		this.conditionColumn = conditionColumn;
	}

	public String getConditionColumn() {
		return conditionColumn;
	}

	public void setConditionToAdd(AuditSearchCondition conditionToAdd) {
		this.conditionToAdd = conditionToAdd;
	}

	public AuditSearchCondition getConditionToAdd() {
		return conditionToAdd;
	}

	public void clearConditions() {
		setConditions(new ArrayList<AuditSearchCondition>());
		setConditionToAdd(null);
	}

	public void clearConditionsAndReload() {
		clearConditions();
		reload();
	}

	public void newCondition() {
		if (AuditLogEntry.FIELD_ADDITIONAL_DETAILS.equals(conditionColumn)
				|| AuditLogEntry.FIELD_AUTHENTICATION_TOKEN.equals(conditionColumn)
				|| AuditLogEntry.FIELD_NODEID.equals(conditionColumn)
				|| AuditLogEntry.FIELD_SEARCHABLE_DETAIL1.equals(conditionColumn)
				|| AuditLogEntry.FIELD_SEARCHABLE_DETAIL2.equals(conditionColumn)
				|| AuditLogEntry.FIELD_SEQENCENUMBER.equals(conditionColumn)) {
			setConditionToAdd(new AuditSearchCondition(conditionColumn, Condition.EQUALS, ""));
		} else if (AuditLogEntry.FIELD_CUSTOM_ID.equals(conditionColumn)) {
			List<SelectItem> caIds = new ArrayList<SelectItem>();
			for (Entry<Object,String> entry : caIdToNameMap.entrySet()) {
				caIds.add(new SelectItem(entry.getKey(), entry.getValue()));
			}
			setConditionToAdd(new AuditSearchCondition(conditionColumn, caIds));
		} else if (AuditLogEntry.FIELD_EVENTSTATUS.equals(conditionColumn)) {
			setConditionToAdd(new AuditSearchCondition(conditionColumn, eventStatusOptions));
		} else if (AuditLogEntry.FIELD_EVENTTYPE.equals(conditionColumn)) {
			setConditionToAdd(new AuditSearchCondition(conditionColumn, eventTypeOptions));
		} else if (AuditLogEntry.FIELD_MODULE.equals(conditionColumn)) {
			setConditionToAdd(new AuditSearchCondition(conditionColumn, moduleTypeOptions));
		} else if (AuditLogEntry.FIELD_SERVICE.equals(conditionColumn)) {
			setConditionToAdd(new AuditSearchCondition(conditionColumn, serviceTypeOptions));
		} else if (AuditLogEntry.FIELD_TIMESTAMP.equals(conditionColumn)) {
			setConditionToAdd(new AuditSearchCondition(conditionColumn, Condition.EQUALS, ValidityDate.formatAsISO8601(new Date(), ValidityDate.TIMEZONE_SERVER)));
		}
	}

	public void cancelCondition() {
		setConditionToAdd(null);
	}

	public void addCondition() {
		getConditions().add(getConditionToAdd());
		setConditionToAdd(null);
	}

	public void addConditionAndReload() {
		addCondition();
		reload();
	}

	public void reload()  {
		reloadResultsNextView = true;
	}

	// TODO: Not the safest way to send user input to the database..
	private void reloadResults() throws AuthorizationDeniedException {
		log.info("Reloading audit load. selectedDevice=" + device);
		QueryCriteria criteria = QueryCriteria.where();
		boolean first = true;
		for (final AuditSearchCondition condition : getConditions()) {
			if (!first) {
				switch (condition.getOperation()) {
				case AND:
					criteria = criteria.and(); break;
				case OR:
					criteria = criteria.or(); break;
				}
			}
			first = false;
			Object conditionValue = condition.getValue();
			if (AuditLogEntry.FIELD_TIMESTAMP.equals(condition.getColumn())) {
				try {
					conditionValue = Long.valueOf(ValidityDate.parseAsIso8601(conditionValue.toString()).getTime());
				} catch (ParseException e) {
					log.info("Admin entered invalid date for audit log search: " + condition.getValue());
					continue;
				}
			}
			switch (condition.getCondition()) {
			case EQUALS:
				criteria = criteria.eq(condition.getColumn(), conditionValue); break;
			case NOT_EQUALS:
				criteria = criteria.neq(condition.getColumn(), conditionValue); break;
			case CONTAINS:
				criteria = criteria.like(condition.getColumn(), "%" + conditionValue + "%"); break;
			case ENDS_WITH:
				criteria = criteria.like(condition.getColumn(), "%" + conditionValue); break;
			case STARTS_WITH:
				criteria = criteria.like(condition.getColumn(), conditionValue + "%"); break;
			case GREATER_THAN:
				criteria = criteria.grt(condition.getColumn(), conditionValue); break;
			case LESS_THAN:
				criteria = criteria.lsr(condition.getColumn(), conditionValue); break;
			}
		}

		AuthenticationToken token = new AlwaysAllowLocalAuthenticationToken(new UsernamePrincipal("Reload action from AuditorManagedBean"));
		results = securityEventsAuditorSession.selectAuditLogs(token, startIndex, maxResults, criteria.order(sortColumn, sortOrder), device);
		updateCaIdToNameMap();
		renderNext = results!=null && !results.isEmpty() && results.size()==maxResults;
	}
	
	public Map<Object, String> getCaIdToName() {
		return caIdToNameMap;
	}

	private void updateCaIdToNameMap() {
		final Map<Integer, String> map = caAdminSession.getCAIdToNameMap(new AlwaysAllowLocalAuthenticationToken(new UsernamePrincipal("INTERNAL")));
		final Map<Object, String> ret = new HashMap<Object, String>();
		for (final Entry<Integer,String> entry : map.entrySet()) {
			ret.put(entry.getKey().toString(), entry.getValue());
		}
		caIdToNameMap = ret;
	}

	public Map<String, String> getNameFromColumn() {
		return columnNameMap;
	}

	public void first() throws AuthorizationDeniedException {
		setStartIndex(1);
		reloadResultsNextView = true;
	}

	public void next() throws AuthorizationDeniedException {
		setStartIndex(startIndex + maxResults);
		reloadResultsNextView = true;
	}

	public void previous() throws AuthorizationDeniedException {
		setStartIndex(startIndex - maxResults);
		reloadResultsNextView = true;
	}

	public void setConditions(List<AuditSearchCondition> conditions) {
		this.conditions = conditions;
	}

	public List<AuditSearchCondition> getConditions() {
		// Special case when we supply "username" as parameter to allow view of a user's history
		final String searchDetail2String = getHttpParameter("username");
		if (searchDetail2String!=null) {
			reloadResultsNextView = true;
			conditions.clear();
			conditions.add(new AuditSearchCondition(AuditLogEntry.FIELD_SEARCHABLE_DETAIL2, Condition.EQUALS, searchDetail2String));
			sortColumn = AuditLogEntry.FIELD_TIMESTAMP;
			sortOrder = QueryCriteria.ORDER_DESC;
		}
		return conditions;
	}

	public List<SelectItem> getDefinedOperations() {
		return operationsOptions;
	}

	public List<SelectItem> getDefinedConditions() {
		return conditionsOptions;
	}

	private String getHttpParameter(String key) {
		return FacesContext.getCurrentInstance().getExternalContext().getRequestParameterMap().get(key);
	}
	
	public void reorderAscByTime() { reorderBy(AuditLogEntry.FIELD_TIMESTAMP, QueryCriteria.ORDER_ASC); }
	public void reorderDescByTime() { reorderBy(AuditLogEntry.FIELD_TIMESTAMP, QueryCriteria.ORDER_DESC); }

	public void reorderAscByEvent() { reorderBy(AuditLogEntry.FIELD_EVENTTYPE, QueryCriteria.ORDER_ASC); }
	public void reorderDescByEvent() { reorderBy(AuditLogEntry.FIELD_EVENTTYPE, QueryCriteria.ORDER_DESC); }

	public void reorderAscByStatus() { reorderBy(AuditLogEntry.FIELD_EVENTSTATUS, QueryCriteria.ORDER_ASC); }
	public void reorderDescByStatus() { reorderBy(AuditLogEntry.FIELD_EVENTSTATUS, QueryCriteria.ORDER_DESC); }

	public void reorderAscByAuthToken() { reorderBy(AuditLogEntry.FIELD_AUTHENTICATION_TOKEN, QueryCriteria.ORDER_ASC); }
	public void reorderDescByAuthToken() { reorderBy(AuditLogEntry.FIELD_AUTHENTICATION_TOKEN, QueryCriteria.ORDER_DESC); }

	public void reorderAscByService() { reorderBy(AuditLogEntry.FIELD_SERVICE, QueryCriteria.ORDER_ASC); }
	public void reorderDescByService() { reorderBy(AuditLogEntry.FIELD_SERVICE, QueryCriteria.ORDER_DESC); }

	public void reorderAscByModule() { reorderBy(AuditLogEntry.FIELD_MODULE, QueryCriteria.ORDER_ASC); }
	public void reorderDescByModule() { reorderBy(AuditLogEntry.FIELD_MODULE, QueryCriteria.ORDER_DESC); }

	public void reorderAscByCustomId() { reorderBy(AuditLogEntry.FIELD_CUSTOM_ID, QueryCriteria.ORDER_ASC); }
	public void reorderDescByCustomId() { reorderBy(AuditLogEntry.FIELD_CUSTOM_ID, QueryCriteria.ORDER_DESC); }

	public void reorderAscBySearchDetail1() { reorderBy(AuditLogEntry.FIELD_SEARCHABLE_DETAIL1, QueryCriteria.ORDER_ASC); }
	public void reorderDescBySearchDetail1() { reorderBy(AuditLogEntry.FIELD_SEARCHABLE_DETAIL1, QueryCriteria.ORDER_DESC); }

	public void reorderAscBySearchDetail2() { reorderBy(AuditLogEntry.FIELD_SEARCHABLE_DETAIL2, QueryCriteria.ORDER_ASC); }
	public void reorderDescBySearchDetail2() { reorderBy(AuditLogEntry.FIELD_SEARCHABLE_DETAIL2, QueryCriteria.ORDER_DESC); }

	public void reorderAscByNodeId() { reorderBy(AuditLogEntry.FIELD_NODEID, QueryCriteria.ORDER_ASC); }
	public void reorderDescByNodeId() { reorderBy(AuditLogEntry.FIELD_NODEID, QueryCriteria.ORDER_DESC); }

	public void reorderAscBySequenceNumber() { reorderBy(AuditLogEntry.FIELD_SEQENCENUMBER, QueryCriteria.ORDER_ASC); }
	public void reorderDescBySequenceNumber() { reorderBy(AuditLogEntry.FIELD_SEQENCENUMBER, QueryCriteria.ORDER_DESC); }

	private void reorderBy(String column, boolean orderAsc) {
		if (!sortColumn.equals(column)) {
			reloadResultsNextView = true;
		}
		sortColumn = column;
		if (sortOrder != orderAsc) {
			reloadResultsNextView = true;
		}
		sortOrder = orderAsc ? QueryCriteria.ORDER_ASC : QueryCriteria.ORDER_DESC;
	}
}

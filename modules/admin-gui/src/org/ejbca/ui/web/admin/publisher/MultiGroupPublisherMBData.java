package org.ejbca.ui.web.admin.publisher;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.ejbca.core.ejb.ca.publisher.PublisherSessionLocal;
import org.ejbca.core.model.ca.publisher.MultiGroupPublisher;
import org.ejbca.core.model.ca.publisher.PublisherDoesntExistsException;
import org.ejbca.core.model.ca.publisher.PublisherExistsException;
import org.ejbca.core.model.util.EjbLocalHelper;
import org.ejbca.ui.web.admin.configuration.EjbcaJSFHelper;

public class MultiGroupPublisherMBData {

    private static final Logger log = Logger.getLogger(MultiGroupPublisherMBData.class);

    private final EjbLocalHelper ejb = new EjbLocalHelper();

    private final PublisherSessionLocal publisherSession = ejb.getPublisherSession();

    private String multiGroupPublisherGroups;

    public String getMultiGroupPublisherGroups() {
        return multiGroupPublisherGroups;
    }

    public void setMultiGroupPublisherGroups(final String multiGroupPublisherGroups) {
        this.multiGroupPublisherGroups = multiGroupPublisherGroups;
    }

    public void initializeData(final MultiGroupPublisher publisher) {
        multiGroupPublisherGroups = getMultiPublishersDataAsString(publisher);
    }

    public void setMultiGroupPublisherParameters(final MultiGroupPublisher multiGroupPublisher)
            throws PublisherDoesntExistsException, PublisherExistsException {
        HashMap<String, Integer> publisherNameToIdMap = EjbcaJSFHelper.getBean().getEjbcaWebBean().getEjb().getPublisherSession()
                .getPublisherNameToIdMap();
        List<TreeSet<Integer>> multiPublisherGroups = convertMultiPublishersStringToData(publisherNameToIdMap, multiGroupPublisherGroups);
        multiGroupPublisher.setPublisherGroups(multiPublisherGroups);
    }

    private String getMultiPublishersDataAsString(final MultiGroupPublisher publisher) {
        final List<TreeSet<Integer>> publisherGroups = publisher.getPublisherGroups();
        final Map<Integer, String> publisherIdToNameMap = publisherSession.getPublisherIdToNameMap();
        return convertMultiPublishersDataToString(publisherIdToNameMap, publisherGroups);
    }

    private String convertMultiPublishersDataToString(final Map<Integer, String> publisherIdToNameMap, final List<TreeSet<Integer>> data) {
        StringBuffer multiPublishersDataAsString = new StringBuffer();
        String prefix = "";
        for (final TreeSet<Integer> group : data) {
            List<String> publisherNames = new ArrayList<>();
            for (Integer publisherId : group) {
                String name = publisherIdToNameMap.get(publisherId);
                if (StringUtils.isNotEmpty(name)) {
                    publisherNames.add(name);
                } else {
                    log.info("No name found for publisher with id " + publisherId);
                }
            }
            Collections.sort(publisherNames);
            for (final String publisherName : publisherNames) {
                multiPublishersDataAsString.append(prefix);
                multiPublishersDataAsString.append(publisherName);
                prefix = "\n";
            }
            if (!publisherNames.isEmpty()) {
                multiPublishersDataAsString.append("\n");
            }
        }
        multiPublishersDataAsString.setLength(Math.max(multiPublishersDataAsString.length() - 1, 0));
        return multiPublishersDataAsString.toString();
    }

    private List<TreeSet<Integer>> convertMultiPublishersStringToData(final Map<String, Integer> publisherNameToIdMap, final String textareaData)
            throws PublisherDoesntExistsException, PublisherExistsException {
        final TreeSet<Integer> selectedPublishers = new TreeSet<>();
        final List<String> listOfPublisherNames = Arrays.asList(textareaData.split("\n"));
        final ArrayList<TreeSet<Integer>> data = new ArrayList<>();
        TreeSet<Integer> tree = new TreeSet<>();
        for (String publisherName : listOfPublisherNames) {
            publisherName = publisherName.trim();
            if (StringUtils.isEmpty(publisherName)) {
                if (!tree.isEmpty()) {
                    data.add(tree);
                    tree = new TreeSet<>();
                }
            } else {
                Integer publisherId = publisherNameToIdMap.get(publisherName);
                if (publisherId != null) {
                    if (!selectedPublishers.contains(publisherId)) {
                        tree.add(publisherId);
                        selectedPublishers.add(publisherId);
                    } else {
                        throw new PublisherExistsException("Publisher selected at least twice: " + publisherName);
                    }
                } else {
                    throw new PublisherDoesntExistsException("Could not find publisher: \"" + publisherName + "\"");
                }
            }
        }
        if (!tree.isEmpty()) {
            data.add(tree);
        }
        return data;
    }

}

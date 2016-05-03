package com.atex.plugins.searchrelated.widget;

import com.polopoly.cm.app.Editor;
import com.polopoly.cm.app.Viewer;
import com.polopoly.cm.app.search.widget.OSearchResult;
import com.polopoly.cm.app.widget.OComplexFieldPolicyWidget;
import com.polopoly.cm.client.CMException;
import com.polopoly.cm.policy.Policy;
import com.polopoly.cm.policy.PolicyUtil;
import com.polopoly.metadata.Metadata;
import com.polopoly.metadata.MetadataAware;
import com.polopoly.orchid.OrchidException;
import com.polopoly.orchid.ajax.AjaxEvent;
import com.polopoly.orchid.ajax.JSCallback;
import com.polopoly.orchid.ajax.event.ClickEvent;
import com.polopoly.orchid.ajax.listener.StandardAjaxEventListener;
import com.polopoly.orchid.ajax.trigger.JsEventTriggerType;
import com.polopoly.orchid.ajax.trigger.OAjaxTriggerOnEvent;
import com.polopoly.orchid.context.OrchidContext;
import com.polopoly.orchid.widget.OButton;
import com.polopoly.search.metadata.DimensionOperator;
import com.polopoly.search.metadata.EntityOperator;
import com.polopoly.search.metadata.MetadataQueryBuilder;
import com.polopoly.util.LocaleUtil;
import org.apache.solr.client.solrj.SolrQuery;

import java.io.IOException;

import static com.polopoly.cm.policy.PolicyUtil.getTopPolicy;
import static com.polopoly.search.solr.schema.IndexFields.CONTENT_ID;
import static java.lang.String.format;

public class OSearchForRelatedWidget extends OComplexFieldPolicyWidget
        implements Editor, Viewer {

    private static final String METADATA_PATH = "metadataPath";

    private static final MetadataQueryBuilder _builder = new MetadataQueryBuilder();

    private MetadataAware metadataAware;

    private OButton searchButton;
    private OSearchResult searchArea;
    private OAjaxTriggerOnEvent ajaxTrigger;

    @Override
    public void initSelf(OrchidContext oc)
            throws OrchidException {
        try {
            String metadataPath = PolicyUtil.getParameter(METADATA_PATH, getPolicy());
            if (metadataPath == null) {
                throw new OrchidException(format("Parameter '%s' was not set for '%s'",
                        METADATA_PATH,
                        getName()));
            }
            Policy childPolicy = getTopPolicy(getPolicy()).getChildPolicy(metadataPath);
            if (!(childPolicy instanceof MetadataAware)) {
                String msg = "Policy '%s' is not a metadata policy, was '%s'" +
                        "(Did you forget to set 'metadatapath' for this related-widget?)";
                throw new OrchidException(format(msg, metadataPath, metadataAware));
            }
            metadataAware = (MetadataAware) childPolicy;
        } catch (CMException e) {
            throw new OrchidException(e);
        }

        initButton(oc);
        initSearchArea(oc);
        initAjaxTrigger(oc);

        registerAjaxListener();
    }

    @Override
    public boolean isAjaxTopWidget() {
        return true;
    }

    @Override
    public void localRender(OrchidContext oc)
            throws IOException, OrchidException {
        searchButton.render(oc);
        searchArea.render(oc);
        ajaxTrigger.render(oc);
    }

    private void initButton(OrchidContext oc)
            throws OrchidException {
        String label = LocaleUtil.format("cm.label.initResourceSearch", oc.getMessageBundle());

        searchButton = new OButton();
        searchButton.setLabel(label);
        searchButton.setTitle(label);
        addAndInitChild(oc, searchButton);
    }

    private void initSearchArea(OrchidContext oc)
            throws OrchidException {
        searchArea = new OSearchResult(getContentSession(), oc, "search_solrClientInternal");
        searchArea.setCssClass("p_related_articles_search_area");
        addAndInitChild(oc, searchArea);
    }

    private void initAjaxTrigger(OrchidContext oc)
            throws OrchidException {
        ajaxTrigger = new OAjaxTriggerOnEvent(searchButton, JsEventTriggerType.CLICK);
        ajaxTrigger.setFormPostSource(getTree().getRoot());
        addAndInitChild(oc, ajaxTrigger);
    }

    private void registerAjaxListener()
            throws OrchidException {
        StandardAjaxEventListener onClickListener = new StandardAjaxEventListener() {

            @Override
            public boolean triggeredBy(OrchidContext oc, AjaxEvent e) {
                return e instanceof ClickEvent;
            }

            @Override
            public JSCallback processEvent(OrchidContext oc, AjaxEvent e)
                    throws OrchidException {
                Metadata metadata = metadataAware.getMetadata();


                String metadataQuery = _builder.buildMetadataQuery(metadata, DimensionOperator.NONE, EntityOperator.OR);
                String queryString = format("-%s:%s", CONTENT_ID, toUnversionedIdString()) + " " + metadataQuery;
                SolrQuery sourceQuery = new SolrQuery(queryString);
                searchArea.doSearch(oc, sourceQuery);
                return null;
            }

            private String toUnversionedIdString() {
                return getPolicy().getContentId().getContentId().getContentIdString();
            }
        };
        onClickListener.addRenderWidget(this);
        onClickListener.addDecodeWidget(getTree().getRoot());
        getTree().registerAjaxEventListener(searchButton, onClickListener);
    }
}

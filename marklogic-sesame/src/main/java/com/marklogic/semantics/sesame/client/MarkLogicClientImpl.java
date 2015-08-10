/*
 * Copyright 2015 MarkLogic Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/**
 * A library that enables access to a MarkLogic-backed triple-store via the
 * Sesame API.
 */
package com.marklogic.semantics.sesame.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.marklogic.client.DatabaseClient;
import com.marklogic.client.DatabaseClientFactory;
import com.marklogic.client.FailedRequestException;
import com.marklogic.client.Transaction;
import com.marklogic.client.impl.SPARQLBindingsImpl;
import com.marklogic.client.io.FileHandle;
import com.marklogic.client.io.InputStreamHandle;
import com.marklogic.client.query.QueryDefinition;
import com.marklogic.client.semantics.*;
import com.marklogic.semantics.sesame.MarkLogicSesameException;
import org.openrdf.model.Literal;
import org.openrdf.model.Resource;
import org.openrdf.model.URI;
import org.openrdf.model.Value;
import org.openrdf.query.Binding;
import org.openrdf.query.parser.sparql.SPARQLUtil;
import org.openrdf.repository.sparql.query.SPARQLQueryBindingSet;
import org.openrdf.rio.RDFFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.InputStream;

/**
 *
 * @author James Fuller
 */
public class MarkLogicClientImpl {

    protected final Logger logger = LoggerFactory.getLogger(MarkLogicClientImpl.class);

    private static final String DEFAULT_GRAPH_URI = "http://marklogic.com/semantics#default-graph";

    private String host;

    private int port;

    private String user;

    private String password;

    private String auth;

    protected static DatabaseClientFactory.Authentication authType = DatabaseClientFactory.Authentication.valueOf(
            "DIGEST"
    );

    private SPARQLRuleset rulesets;
    private QueryDefinition constrainingQueryDef;
    private GraphPermissions graphPerms;

    private static  SPARQLQueryManager sparqlManager;
    private static GraphManager graphManager;

    private static DatabaseClient databaseClient;

    // constructor
    public MarkLogicClientImpl(String host, int port, String user, String password, String auth) {
        setDatabaseClient(DatabaseClientFactory.newClient(host, port, user, password, DatabaseClientFactory.Authentication.valueOf(auth)));
    }
    public MarkLogicClientImpl(Object databaseClient) {
        if(databaseClient instanceof  DatabaseClient){
            setDatabaseClient((DatabaseClient)databaseClient);
        }
    }

    private void setDatabaseClient(DatabaseClient databaseClient){
        this.databaseClient=databaseClient;
    }

    // host
    public String getHost() {
        return this.host;
    }
    public void setHost(String host) {
        this.host = host;
    }

    // port
    public int getPort() {
        return this.port;
    }
    public void setPort(int port) {
        this.port = port;
    }

    // user
    public String getUser() {
        return this.user;
    }
    public void setUser(String user) {
        this.user = user;
    }

    // password
    public String getPassword() {
        return password;
    }

    public void setPassword() {
        this.password = password;
    }

    // auth
    public String getAuth() {
        return auth;
    }
    public void setAuth(String auth) {
        this.auth = auth;
        this.authType = DatabaseClientFactory.Authentication.valueOf(
                auth
        );
    }

    // auth type
    public void setAuthType(DatabaseClientFactory.Authentication authType) {
        MarkLogicClientImpl.authType = authType;
    }
    public DatabaseClientFactory.Authentication getAuthType() {
        return authType;
    }

    //
    public DatabaseClient getDatabaseClient() {
        return this.databaseClient;
    }

    // performSPARQLQuery
    public InputStream performSPARQLQuery(String queryString, SPARQLQueryBindingSet bindings, long start, long pageLength, Transaction tx, boolean includeInferred, String baseURI) throws JsonProcessingException {
        return performSPARQLQuery(queryString, bindings, new InputStreamHandle(), start, pageLength, tx, includeInferred, baseURI);
    }
    public InputStream performSPARQLQuery(String queryString, SPARQLQueryBindingSet bindings, InputStreamHandle handle, long start, long pageLength, Transaction tx, boolean includeInferred, String baseURI) throws JsonProcessingException {
        sparqlManager = getDatabaseClient().newSPARQLQueryManager();
        StringBuilder sb = new StringBuilder();
        if(baseURI != null) sb.append("BASE <"+baseURI+">\n");
        sb.append(queryString);
        SPARQLQueryDefinition qdef = sparqlManager.newQueryDefinition(sb.toString());
        if(rulesets instanceof SPARQLRuleset){qdef.setRulesets(rulesets);};
        if(getConstrainingQueryDefinition() instanceof QueryDefinition){
            qdef.setConstrainingQueryDefinition(getConstrainingQueryDefinition());};
        qdef.setIncludeDefaultRulesets(includeInferred);
        qdef.setBindings(getSPARQLBindings(bindings));
        sparqlManager.executeSelect(qdef, handle, start, pageLength, tx);
        return handle.get();
    }

    // performGraphQuery
    public InputStream performGraphQuery(String queryString, SPARQLQueryBindingSet bindings, Transaction tx, boolean includeInferred, String baseURI) throws JsonProcessingException {
        return performGraphQuery(queryString, bindings, new InputStreamHandle(), tx, includeInferred, baseURI);
    }
    public InputStream performGraphQuery(String queryString, SPARQLQueryBindingSet bindings, InputStreamHandle handle, Transaction tx, boolean includeInferred, String baseURI) throws JsonProcessingException {
        sparqlManager = getDatabaseClient().newSPARQLQueryManager();
        StringBuilder sb = new StringBuilder();
        if(baseURI != null) sb.append("BASE <"+baseURI+">\n");
        sb.append(queryString);
        SPARQLQueryDefinition qdef = sparqlManager.newQueryDefinition(sb.toString());
        if(rulesets instanceof SPARQLRuleset){qdef.setRulesets(rulesets);};
        if(getConstrainingQueryDefinition() instanceof QueryDefinition){
            qdef.setConstrainingQueryDefinition(getConstrainingQueryDefinition());};
        qdef.setIncludeDefaultRulesets(includeInferred);
        qdef.setBindings(getSPARQLBindings(bindings));
        sparqlManager.executeDescribe(qdef, handle, tx);
        return handle.get();
    }

    // performBooleanQuery
    public boolean performBooleanQuery(String queryString, SPARQLQueryBindingSet bindings, Transaction tx, boolean includeInferred, String baseURI) {
        sparqlManager = getDatabaseClient().newSPARQLQueryManager();
        StringBuilder sb = new StringBuilder();
        if(baseURI != null) sb.append("BASE <"+baseURI+">\n");
        sb.append(queryString);
        SPARQLQueryDefinition qdef = sparqlManager.newQueryDefinition(sb.toString());
        qdef.setIncludeDefaultRulesets(includeInferred);
        if (rulesets instanceof SPARQLRuleset){qdef.setRulesets(rulesets);};
        if(getConstrainingQueryDefinition() instanceof QueryDefinition){
            qdef.setConstrainingQueryDefinition(getConstrainingQueryDefinition());};
        qdef.setBindings(getSPARQLBindings(bindings));
        return sparqlManager.executeAsk(qdef, tx);
    }

    // performUpdateQuery
    public void performUpdateQuery(String queryString, SPARQLQueryBindingSet bindings, Transaction tx, boolean includeInferred, String baseURI) {
        sparqlManager = getDatabaseClient().newSPARQLQueryManager();
        StringBuilder sb = new StringBuilder();
        if(baseURI != null) sb.append("BASE <"+baseURI+">\n");
        sb.append(queryString);
        SPARQLQueryDefinition qdef = sparqlManager.newQueryDefinition(sb.toString());
        if(rulesets instanceof SPARQLRuleset){qdef.setRulesets(rulesets);};
        if(getConstrainingQueryDefinition() instanceof QueryDefinition){
            qdef.setConstrainingQueryDefinition(getConstrainingQueryDefinition());};
        qdef.setIncludeDefaultRulesets(includeInferred);
        qdef.setBindings(getSPARQLBindings(bindings));
        sparqlManager.executeUpdate(qdef, tx);
    }

    // performAdd
    public void performAdd(File file, String baseURI, RDFFormat dataFormat,Transaction tx,Resource... contexts) throws MarkLogicSesameException {
        try {
            graphManager = getDatabaseClient().newGraphManager();

            graphManager.setDefaultMimetype(dataFormat.getDefaultMIMEType());
            if (dataFormat.equals(RDFFormat.NQUADS) || dataFormat.equals(RDFFormat.TRIG)) {
                //TBD- tx ?
                graphManager.mergeGraphs(new FileHandle(file));
            } else {
                //TBD- must be more efficient
                if (contexts.length != 0) {
                    for (Resource context : contexts) {
                        graphManager.merge(context.toString(), new FileHandle(file), tx);
                    }
                } else {
                    graphManager.merge(null, new FileHandle(file), tx);
                }
            }
        }catch (FailedRequestException e){
            throw new MarkLogicSesameException("Request to MarkLogic server failed, check file and format.");
        }
    }
    public void performAdd(InputStream in, String baseURI, RDFFormat dataFormat,Transaction tx,Resource... contexts) {
        graphManager = getDatabaseClient().newGraphManager();
        graphManager.setDefaultMimetype(dataFormat.getDefaultMIMEType());
        if(dataFormat.equals(RDFFormat.NQUADS)||dataFormat.equals(RDFFormat.TRIG)){
            //TBD- tx ?
            graphManager.mergeGraphs(new InputStreamHandle(in));
        }else{
            //TBD- must be more efficient
            if(contexts.length !=0) {
                graphManager.merge(contexts[0].stringValue(), new InputStreamHandle(in), tx);
            }else{
                graphManager.merge(null, new InputStreamHandle(in), tx);
            }
        }
    }

    public void performAdd(String baseURI,Resource subject,URI predicate, Value object,Transaction tx,Resource... contexts) {
        sparqlManager = getDatabaseClient().newSPARQLQueryManager();
        SPARQLQueryBindingSet bindingSet = new SPARQLQueryBindingSet();
        if(subject != null)bindingSet.addBinding("s",subject);
        if(predicate != null)bindingSet.addBinding("p",predicate);

        StringBuilder ob = new StringBuilder();
        if (object != null){
            if (object instanceof Literal) {
                Literal lit = (Literal) object;
                ob.append("\"");
                ob.append(SPARQLUtil.encodeString(lit.getLabel()));
                ob.append("\"");
                ob.append("^^<" + lit.getDatatype().stringValue() + ">");
                ob.append(" ");
            } else {
                ob.append("<" + object.stringValue() + "> ");
            }
        }else{
            ob.append("?o");
        }
    StringBuilder sb = new StringBuilder();
        if(baseURI != null) sb.append("BASE <"+baseURI+">\n");
        sb.append("INSERT DATA { ");

        for (int i = 0; i < contexts.length; i++)
        {
            if(contexts[i] != null) {
                sb.append("GRAPH <"+ contexts[i].stringValue()+"> { ?s ?p "+ob.toString()+" .} ");
            }else{
                //sb.append("OPTIONAL {GRAPH ?ctx {<"+subject.stringValue()+"> <"+predicate.stringValue()+"> "+ob.toString()+" .} }");
            }
        }
        sb.append("}");
        SPARQLQueryDefinition qdef = sparqlManager.newQueryDefinition(sb.toString());
        qdef.setBindings(getSPARQLBindings(bindingSet));
        sparqlManager.executeUpdate(qdef, tx);
    }

    // performRemove
    public void performRemove(String baseURI,Resource subject,URI predicate, Value object,Transaction tx,Resource... contexts) {
        SPARQLQueryBindingSet bindingSet = new SPARQLQueryBindingSet();
        if(subject != null)bindingSet.addBinding("s",subject);
        if(predicate != null)bindingSet.addBinding("p",predicate);
        if(object != null)bindingSet.addBinding("o",object);

        StringBuilder ob = new StringBuilder();
        if (object != null){
            if (object instanceof Literal) {
                Literal lit = (Literal) object;
                ob.append("\"");
                ob.append(SPARQLUtil.encodeString(lit.getLabel()));
                ob.append("\"");
                ob.append("^^<" + lit.getDatatype().stringValue() + ">");
                ob.append(" ");
            } else {
                ob.append("<" + object.stringValue() + "> ");
            }
        }else{
            ob.append("?o");
        }
        StringBuilder sb = new StringBuilder();
        if(baseURI != null) sb.append("BASE <"+baseURI+">\n");
        sb.append("DELETE WHERE { ");

        for (int i = 0; i < contexts.length; i++)
        {
            if(contexts[i] != null) {
                sb.append("GRAPH <"+ contexts[i].stringValue()+"> { ?s ?p "+ob.toString()+" .} ");
            }else{
                //sb.append("OPTIONAL {GRAPH ?ctx {<"+subject.stringValue()+"> <"+predicate.stringValue()+"> "+ob.toString()+" .} }");
            }
        }
        sb.append("}");
        SPARQLQueryDefinition qdef = sparqlManager.newQueryDefinition(sb.toString());
        qdef.setBindings(getSPARQLBindings(bindingSet));
        sparqlManager.executeUpdate(qdef, tx);
    }

    // performClear
    public void performClear(Transaction tx, Resource... contexts){
        graphManager = getDatabaseClient().newGraphManager();
        for(Resource context : contexts) {
            graphManager.delete(context.stringValue(), tx);
        }
    }
    public void performClearAll(Transaction tx){
        graphManager = getDatabaseClient().newGraphManager();
        graphManager.deleteGraphs();
    }

    // rulesets
    public SPARQLRuleset getRulesets(){
        return this.rulesets;
    }
    public void setRulesets(Object rulesets){
        this.rulesets=(SPARQLRuleset) rulesets;
    }

    // graph perms
    public void setGraphPerms(Object graphPerms){
        this.graphPerms = (GraphPermissions)graphPerms;
    }
    public GraphPermissions getGraphPerms(){
        return this.graphPerms;
    }

    // constraining query
    public void setConstrainingQueryDefinition(Object constrainingQueryDefinition){
        this.constrainingQueryDef = (QueryDefinition) constrainingQueryDefinition;
    }
    public QueryDefinition getConstrainingQueryDefinition(){
        return this.constrainingQueryDef;
    }

    // getSPARQLBindings
    protected SPARQLBindings getSPARQLBindings(SPARQLQueryBindingSet bindings){
        SPARQLBindings sps = new SPARQLBindingsImpl();
        for (Binding binding : bindings) {
            sps.bind(binding.getName(), binding.getValue().stringValue());
            logger.debug("binding:" + binding.getName() + "=" + binding.getValue());
        }
        return sps;
    }
}